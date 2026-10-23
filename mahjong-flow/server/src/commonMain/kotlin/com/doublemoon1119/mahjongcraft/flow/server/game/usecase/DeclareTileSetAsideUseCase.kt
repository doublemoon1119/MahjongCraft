package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.actionScoredHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isOwnTurnWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import kotlin.uuid.Uuid

/**
 * 宣告移出手牌的擴充動作（見 [MahjongRuleModule.tileSetAsideBy]，例如三人日麻的拔北）的用例。
 *
 * 流程比照暗槓／加槓（[DeclareKanUseCase]）：合法性交給規則的合法動作判定器；去向由 [TileSetAsideResolver] 決定：
 * 有人可以用移出的那張牌榮和時開啟反應視窗（[TableState.pendingRobbingReaction]），動作暫緩套用，交給
 * [RespondToRobbingUseCase] 解析；多位玩家同時可以榮和時依一炮多響設定決定開放給誰，判定為途中流局時這次動作視為未成立；
 * 沒人可以榮和時直接套用、收付並依規則補牌，補到的牌再交給 [DrawFollowUpApplier] 依規則自動處理。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
class DeclareTileSetAsideUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val eventPublisher: GameEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行宣告。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 宣告的玩家 Uuid。
     * @param action 宣告的擴充動作。
     * @return 成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        action: GameAction.Extension,
    ): Outcome<Unit, GameError> {
        val outcome = gameRepository.update(
            gameId,
            history = { before, after, result ->
                if (result is Outcome.Success && before != null && after != null) {
                    val value = result.value
                    buildList {
                        add(acceptedActionHistoryDraft(playerId, action, before, after, listOf(value.setAsideTile.id)))
                        value.scoring?.let { add(actionScoredHistoryDraft(it)) }
                        if (value.drawnTiles.isNotEmpty()) {
                            add(acceptedActionHistoryDraft(playerId, GameAction.Draw, before, after, value.drawnTiles.map { it.id }))
                        }
                        value.followUp?.let { addAll(it.historyDrafts(playerId, before, after)) }
                        value.abortiveDrawReason?.let { reason ->
                            add(acceptedActionHistoryDraft(playerId, GameAction.ExhaustiveDraw(reason), before, after))
                        }
                    }
                } else {
                    emptyList<HistoryEventDraft>()
                }
            },
        ) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } -> state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                state.currentPlayer.id != playerId -> state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))
                !state.isOwnTurnWindowOpenFor(playerId) -> state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                else -> declare(state, gameId, playerId, action)
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState
        val module = moduleRegistry.getModule(newState.config)

        snapshotSynchronizer.syncAll(gameId)

        val seatedPlayerIds = newState.players.map { it.id }
        eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, action)
        val followUp = result.followUp
        if (result.applied) {
            if (followUp != null) {
                eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, GameAction.Draw)
                followUp.publishedActions.forEach { followUpAction ->
                    eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, followUpAction)
                }
            }
            presentationPublisher.publishRoundInfoUpdated(gameId, newState)
            if (result.physicalWallTransitionPhases.isNotEmpty()) {
                presentationPublisher.publishWallLayoutTransition(gameId, result.physicalWallTransitionPhases)
            }
            result.scoring?.let { presentationPublisher.publishActionScored(gameId, it) }
            if (followUp != null) {
                presentationPublisher.publishPlayerAreaAfterDraw(
                    gameId = gameId,
                    playerId = playerId,
                    followUp = followUp,
                    module = module,
                    newlySetAsideTileIds = setOf(result.setAsideTile.id),
                )
            } else {
                val declarerSeatIndex = newState.players.indexOfFirst { it.id == playerId }
                val declarer = newState.players[declarerSeatIndex]
                presentationPublisher.publishPlayerTilesUpdated(
                    gameId = gameId,
                    seatIndex = declarerSeatIndex,
                    standingTileIds = declarer.hand.tiles.map { it.id },
                    drawnTileId = declarer.hand.lastDrawn?.id,
                    melds = declarer.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
                    setAsideTileIds = module.setAsideTiles(declarer).map { it.id },
                    newlySetAsideTileIds = setOf(result.setAsideTile.id),
                )
            }
            presentationPublisher.publishRuleStateUpdated(gameId)
            result.wallRevealBatches.forEach { revealedTileIds ->
                presentationPublisher.publishWallTilesRevealed(gameId, revealedTileIds)
            }
        }
        result.abortiveDrawReason?.let { reason ->
            eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, GameAction.ExhaustiveDraw(reason))
        }

        // 開啟搶和反應視窗時，先呈現移出完成的樣子（牌擺到桌上、尚未補牌），其他玩家等動畫播完才決定要不要搶；
        // 權威桌況要等反應結束才套用，全員放過後只會再呈現補牌。
        result.declaredState?.let { declared ->
            val declarerSeatIndex = declared.players.indexOfFirst { it.id == playerId }
            val declarer = declared.players[declarerSeatIndex]
            presentationPublisher.publishPlayerTilesUpdated(
                gameId = gameId,
                seatIndex = declarerSeatIndex,
                standingTileIds = declarer.hand.tiles.map { it.id },
                drawnTileId = declarer.hand.lastDrawn?.id,
                melds = declarer.hand.melds.map { it.toPresentation(declared.config.revealsClosedKanTiles) },
                setAsideTileIds = module.setAsideTiles(declarer).map { it.id },
                newlySetAsideTileIds = setOf(result.setAsideTile.id),
            )
        }

        presentationPublisher.publishGameActionDeclared(gameId, playerId, action)
        return Outcome.Success(Unit)
    }

    /** 驗證合法性並交給 [TileSetAsideResolver] 決定去向；套用並補牌後接著執行摸牌後的自動處理。 */
    private fun declare(
        state: TableState,
        gameId: Uuid,
        playerId: Uuid,
        action: GameAction.Extension,
    ): Pair<TableState, Outcome<SetAsideResult, GameError>> {
        val illegal = state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
        val declarer = state.currentPlayer
        if (declarer.hand.lastDrawn == null) return illegal
        val module = moduleRegistry.getModule(state.config)
        val legalActions = module.createLegalActionValidator().getLegalActions(
            LegalActionContext.ownTurn(state, playerId),
        )
        if (action !in legalActions) return illegal
        val setAsideTile = module.tileSetAsideBy(declarer, action) ?: return illegal

        return when (val resolution = TileSetAsideResolver.resolve(state, playerId, action, setAsideTile, module)) {
            is TileSetAsideResolver.Resolution.AbortiveDraw -> resolution.tableState to Outcome.Success(
                SetAsideResult(
                    tableState = resolution.tableState,
                    setAsideTile = setAsideTile,
                    abortiveDrawReason = resolution.reason,
                ),
            )

            is TileSetAsideResolver.Resolution.RobbingWindowOpened -> resolution.tableState to Outcome.Success(
                SetAsideResult(
                    tableState = resolution.tableState,
                    setAsideTile = setAsideTile,
                    declaredState = resolution.declaredState,
                ),
            )

            is TileSetAsideResolver.Resolution.Rejected -> {
                val error = if (resolution.reasonId == SupplementalDrawReasonIds.WALL_EXHAUSTED) {
                    GameError.WallExhausted(gameId)
                } else {
                    GameError.UnsupportedAction(gameId, playerId, resolution.reasonId)
                }
                state to Outcome.Error(error)
            }

            is TileSetAsideResolver.Resolution.Applied -> {
                val applied = resolution.applied
                val followUp = if (applied.drawnTiles.isNotEmpty()) {
                    DrawFollowUpApplier.run(applied.tableState, playerId, DrawSource.Supplemental(action), applied.drawnTiles, module)
                } else {
                    null
                }
                val committed = followUp?.tableState ?: applied.tableState
                committed to Outcome.Success(
                    SetAsideResult(
                        tableState = committed,
                        setAsideTile = setAsideTile,
                        applied = true,
                        drawnTiles = applied.drawnTiles,
                        scoring = applied.scoring,
                        followUp = followUp,
                        wallRevealBatches = applied.wallRevealBatches,
                        physicalWallTransitionPhases = applied.physicalWallTransitionPhases,
                    ),
                )
            }
        }
    }

    /**
     * `update` 區塊內部使用的中繼結果。
     *
     * @property tableState 本次交易提交的權威桌況。
     * @property setAsideTile 這次要移出手牌的牌。
     * @property applied 動作是否已成立並套用；開啟搶和視窗或判定途中流局時為 false。
     * @property drawnTiles 這次移出後補到的牌；沒有補牌時為空清單。
     * @property scoring 這次移出當下的點數收付；沒有成立或不收付時為 null。
     * @property followUp 補牌之後的自動處理；沒有補牌時為 null。
     * @property abortiveDrawReason 一炮多響依規則設定判定為流局時的原因；此時這次動作視為未成立。
     * @property declaredState 開啟搶和視窗時，牌已移出、尚未補牌的候選桌況，供呈現使用；其他情況為 null。
     * @property wallRevealBatches 依規則 checkpoint 順序新增公開的牌張 ID 批次。
     * @property physicalWallTransitionPhases 這次補牌已驗證的實體牌牆移動階段。
     */
    private data class SetAsideResult(
        val tableState: TableState,
        val setAsideTile: IdentifiedTile,
        val applied: Boolean = false,
        val drawnTiles: List<IdentifiedTile> = emptyList(),
        val scoring: AppliedActionScoring? = null,
        val followUp: DrawFollowUpApplier.Result? = null,
        val abortiveDrawReason: ExhaustiveDrawReason? = null,
        val declaredState: TableState? = null,
        val wallRevealBatches: List<Set<Uuid>> = emptyList(),
        val physicalWallTransitionPhases: List<PhysicalWallLayoutTransitionPhase> = emptyList(),
    )
}
