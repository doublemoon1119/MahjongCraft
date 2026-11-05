package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.actionScoredHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isOwnTurnWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 宣告移出手牌的擴充動作（見 [MahjongRuleModule.tileSetAsideBy]，例如三人日麻的拔北）的用例。
 *
 * 流程比照暗槓／加槓（[DeclareKanUseCase]）：合法性交給規則的合法動作判定器；去向由 [TileSetAsideResolver] 決定：
 * 有人可以用移出的那張牌榮和時開啟反應視窗（[TableState.pendingRobbingReaction]），動作暫緩套用，交給
 * [RespondToRobbingUseCase] 解析；多位玩家同時可以榮和時依一炮多響設定決定開放給誰，判定為途中流局時這次動作視為未成立；
 * 沒人可以榮和時直接套用、收付並依規則補牌，補到的牌再交給 [DrawFollowUpApplier] 依規則自動處理。
 *
 * @property componentRegistry 正式組件定義與純效果來源。
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
    private val componentRegistry: ComponentRegistry,
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
        }
        result.abortiveDrawReason?.let { reason ->
            eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, GameAction.ExhaustiveDraw(reason))
        }

        presentationPublisher.publishTableSteps(gameId, result.steps)
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

        return when (val resolution = TileSetAsideResolver.resolve(state, playerId, action, setAsideTile, module, componentRegistry = componentRegistry)) {
            is TileSetAsideResolver.Resolution.AbortiveDraw -> resolution.tableState to Outcome.Success(
                SetAsideResult(
                    tableState = resolution.tableState,
                    setAsideTile = setAsideTile,
                    abortiveDrawReason = resolution.reason,
                    steps = listOf(TableStep(resolution.tableState, listOf(TableStepEvent.ActionDeclared(playerId, action)))),
                ),
            )

            is TileSetAsideResolver.Resolution.RobbingWindowOpened -> resolution.tableState to Outcome.Success(
                SetAsideResult(
                    tableState = resolution.tableState,
                    setAsideTile = setAsideTile,
                    // 先呈現移出完成的樣子（牌擺到桌上、尚未補牌），其他玩家看到後才決定要不要搶；權威桌況要等反應結束才套用。
                    steps = listOf(
                        TableStep(resolution.declaredState ?: resolution.tableState, listOf(TableStepEvent.ActionDeclared(playerId, action))),
                    ),
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
                    DrawFollowUpApplier.run(applied.tableState, playerId, DrawSource.Supplemental(action), applied.drawnTiles, module, componentRegistry = componentRegistry)
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
                        steps = applied.tableSteps(playerId, action, followUp),
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
     * @property steps 依序呈現的步驟；開啟搶和視窗時只有牌已移出、尚未補牌的候選桌況。
     */
    private data class SetAsideResult(
        val tableState: TableState,
        val setAsideTile: IdentifiedTile,
        val applied: Boolean = false,
        val drawnTiles: List<IdentifiedTile> = emptyList(),
        val scoring: AppliedActionScoring? = null,
        val followUp: DrawFollowUpApplier.Result? = null,
        val abortiveDrawReason: ExhaustiveDrawReason? = null,
        val steps: List<TableStep>,
    )
}
