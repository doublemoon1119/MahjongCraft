package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import kotlin.uuid.Uuid

/**
 * 宣告移出手牌的擴充動作（見 [MahjongRuleModule.tileSetAsideBy]，例如三人日麻的拔北）的用例。
 *
 * 流程比照暗槓／加槓（[DeclareKanUseCase]）：合法性交給規則的合法動作判定器；有人可以用移出的那張牌榮和時，
 * 開啟反應視窗（[TableState.pendingRobbingReaction]），動作暫緩套用，交給 [RespondToRobbingUseCase] 解析；沒人可以榮和時
 * 直接由 [SelfDeclarationApplier.applyTileSetAside] 套用並依規則補牌。多位玩家同時可以榮和時依一炮多響設定決定開放給誰，
 * 判定為途中流局時這次動作視為未成立。
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
                        if (value.drawHappened) {
                            add(
                                acceptedActionHistoryDraft(
                                    playerId,
                                    GameAction.Draw,
                                    before,
                                    after,
                                    listOfNotNull(after.players.first { it.id == playerId }.hand.lastDrawn?.id),
                                ),
                            )
                        }
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
                state.pendingRobbingReaction != null -> state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
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
        if (result.drawHappened) {
            eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, GameAction.Draw)
            presentationPublisher.publishRoundInfoUpdated(gameId, newState)
            if (result.physicalWallTransitionPhases.isNotEmpty()) {
                presentationPublisher.publishWallLayoutTransition(gameId, result.physicalWallTransitionPhases)
            }
            val declarerSeatIndex = newState.players.indexOfFirst { it.id == playerId }
            val declarer = newState.players[declarerSeatIndex]
            presentationPublisher.publishPlayerTilesUpdated(
                gameId,
                declarerSeatIndex,
                declarer.hand.tiles.map { it.id },
                declarer.hand.lastDrawn?.id,
                declarer.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles, module.tileOrder) },
                setAsideTileIds = module.setAsideTiles(declarer).map { it.id },
                isNewlyDrawn = true,
                newlySetAsideTileIds = setOf(result.setAsideTile.id),
            )
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
                melds = declarer.hand.melds.map { it.toPresentation(declared.config.revealsClosedKanTiles, module.tileOrder) },
                setAsideTileIds = module.setAsideTiles(declarer).map { it.id },
                newlySetAsideTileIds = setOf(result.setAsideTile.id),
            )
        }

        presentationPublisher.publishGameActionDeclared(gameId, playerId, action)
        return Outcome.Success(Unit)
    }

    /** 驗證合法性並決定直接套用、開啟搶和視窗或判定途中流局。 */
    private fun declare(
        state: TableState,
        gameId: Uuid,
        playerId: Uuid,
        action: GameAction.Extension,
    ): Pair<TableState, Outcome<SetAsideResult, GameError>> {
        val illegal = state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
        val declarer = state.currentPlayer
        val incomingTile = declarer.hand.lastDrawn ?: return illegal
        val module = moduleRegistry.getModule(state.config)
        val legalActions = module.createLegalActionValidator().getLegalActions(
            tableState = state,
            player = declarer.copy(hand = declarer.hand.copy(lastDrawn = null)),
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )
        if (action !in legalActions) return illegal
        val setAsideTile = module.tileSetAsideBy(declarer, action) ?: return illegal

        val ronEligiblePlayerIds = RobbingEligibility.ronEligiblePlayerIds(
            tableState = state,
            declarerId = playerId,
            declaredAction = action,
            robbedTile = setAsideTile,
            module = module,
        )
        val ronResolution = when (ronEligiblePlayerIds.size) {
            0, 1 -> null
            2 -> state.config.multiRonPolicy.doubleRonResolution
            else -> state.config.multiRonPolicy.tripleRonResolution
        }
        val ronWinningPlayerIds = when (ronResolution) {
            null, RonResolution.ALL_WINNERS -> ronEligiblePlayerIds
            RonResolution.NEAREST_WINNER -> setOf(state.nearestPlayerInTurnOrder(playerId, ronEligiblePlayerIds))
            RonResolution.ABORTIVE_DRAW -> emptySet()
        }
        val abortiveDrawReason = if (ronResolution == RonResolution.ABORTIVE_DRAW) module.resolveMultiRonAbortiveDraw() else null
        if (abortiveDrawReason != null) {
            val newState = state.copy(players = state.players.map { it.recordAction(GameAction.ExhaustiveDraw(abortiveDrawReason)) })
            return newState to Outcome.Success(SetAsideResult(newState, setAsideTile, abortiveDrawReason = abortiveDrawReason))
        }
        if (ronWinningPlayerIds.isNotEmpty()) {
            val newState = state.copy(
                pendingRobbingReaction = PendingRobbingReaction(
                    declarerId = playerId,
                    declaredAction = action,
                    robbedTile = setAsideTile,
                    eligiblePlayerIds = ronWinningPlayerIds,
                ),
                revealedHandTileIds = state.revealedHandTileIds + setAsideTile.id,
            )
            return newState to Outcome.Success(
                SetAsideResult(
                    tableState = newState,
                    setAsideTile = setAsideTile,
                    declaredState = SelfDeclarationApplier.declaredState(state, playerId, action, setAsideTile, module),
                ),
            )
        }

        return when (val applied = SelfDeclarationApplier.applyTileSetAside(state, playerId, action, module)) {
            is SelfDeclarationApplier.Result.Rejected -> {
                val error = if (applied.reasonId == SupplementalDrawReasonIds.WALL_EXHAUSTED) {
                    GameError.WallExhausted(gameId)
                } else {
                    GameError.UnsupportedAction(gameId, playerId, applied.reasonId)
                }
                state to Outcome.Error(error)
            }
            is SelfDeclarationApplier.Result.Applied -> applied.tableState to Outcome.Success(
                SetAsideResult(
                    tableState = applied.tableState,
                    setAsideTile = setAsideTile,
                    drawHappened = applied.drawnTiles.isNotEmpty(),
                    wallRevealBatches = applied.wallRevealBatches,
                    physicalWallTransitionPhases = applied.physicalWallTransitionPhases,
                ),
            )
        }
    }

    /**
     * `update` 區塊內部使用的中繼結果。[drawHappened] 為 false 代表開啟了搶和視窗或判定為途中流局，動作與補牌皆尚未套用。
     *
     * @property setAsideTile 這次要移出手牌的牌。
     * @property abortiveDrawReason 一炮多響依規則設定判定為流局時的原因；此時這次動作視為未成立。
     * @property declaredState 開啟搶和視窗時，牌已移出、尚未補牌的候選桌況，供呈現使用；其他情況為 null。
     */
    private data class SetAsideResult(
        val tableState: TableState,
        val setAsideTile: IdentifiedTile,
        val drawHappened: Boolean = false,
        val abortiveDrawReason: ExhaustiveDrawReason? = null,
        val declaredState: TableState? = null,
        val wallRevealBatches: List<Set<Uuid>> = emptyList(),
        val physicalWallTransitionPhases: List<PhysicalWallLayoutTransitionPhase> = emptyList(),
    )
}
