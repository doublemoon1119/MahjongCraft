package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.recordAbortiveDraw
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isOwnTurnWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import kotlin.uuid.Uuid

/**
 * 執行第三方宣告與同一張牌捨出的共用流程。
 *
 * 規則只提供純宣告效果，權威驗證、反應判定、流局檢查與副作用由這個用例統一處理，
 * 整個命令只會對對局寫入一次。
 *
 * @property gameRepository 權威對局資料倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 玩家手牌排序偏好。
 * @property postActionExhaustiveDrawResolverRegistry 動作後途中流局判定。
 * @property eventPublisher 對局事件發布器。
 * @property presentationPublisher 對局呈現發布器。
 */
class DeclareAndDiscardUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
    private val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
    private val eventPublisher: GameEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 驗證並原子套用宣告與捨牌。
     *
     * @param gameId 對局識別碼。
     * @param playerId 宣告玩家識別碼。
     * @param action 規則提供的宣告動作。
     * @param tileId 同時捨出的實體牌識別碼。
     * @return 成功時為 Unit，失敗時回傳流程錯誤。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        action: GameAction.Extension,
        tileId: Uuid,
    ): Outcome<Unit, GameError> {
        val outcome = gameRepository.update(
            gameId,
            history = { before, after, result ->
                if (result is Outcome.Success && before != null && after != null) {
                    listOf(
                        acceptedActionHistoryDraft(playerId, action, before, after, listOf(tileId)),
                        acceptedActionHistoryDraft(playerId, GameAction.Discard(tileId), before, after, listOf(tileId)),
                    ) + result.value.abortiveDrawReason?.let { reason ->
                        listOf(acceptedActionHistoryDraft(playerId, GameAction.ExhaustiveDraw(reason), before, after))
                    }.orEmpty()
                } else {
                    emptyList<HistoryEventDraft>()
                }
            },
        ) { state ->
            if (state == null) return@update state to Outcome.Error(GameError.GameNotFound(gameId))
            val player = state.players.firstOrNull { it.id == playerId }
                ?: return@update state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
            if (state.currentPlayer.id != playerId || !state.isOwnTurnWindowOpenFor(playerId)) {
                return@update state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))
            }

            val module = moduleRegistry.getModule(state.config)
            val validator = module.createLegalActionValidator()
            val context = LegalActionContext.ownTurn(state, playerId)
            val ownTurnActions = validator.getLegalActions(context)
            if (action !in ownTurnActions) {
                return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
            }
            val requirement = validator.tileSelectionRequirement(state, player, action)
            if (requirement != null &&
                (tileId !in requirement.eligibleTileIds || 1 !in requirement.minCount..requirement.maxCount)
            ) {
                return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
            }
            val forcedTileId = module.forcedDiscardTileId(state, player)
            if (forcedTileId != null && forcedTileId != tileId) {
                return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))
            }
            val discard = player.hand.discardById(tileId)
                ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))
            val organized = if (handSortPreferenceStore.isEnabled(playerId)) {
                discard.copy(hand = discard.hand.organize(module.tileOrder))
            } else {
                discard
            }
            // 宣告附帶的捨牌與一般捨牌相同：先讓規則處理放棄自摸與打牌後的狀態，再套用宣告效果。
            val declinedChecked = if (player.hand.lastDrawn != null && ownTurnActions.any { it is GameAction.Tsumo }) {
                module.onPlayerDeclinedWin(player)
            } else {
                player
            }
            val afterDiscardHooks = module.onPlayerDiscarded(
                declinedChecked
                    .copy(hand = discard.hand, discardPile = declinedChecked.discardPile.discardTile(discard.tile))
                    .recordAction(GameAction.Discard(tileId)),
            )
            val declaringPlayer = afterDiscardHooks.copy(
                hand = player.hand,
                discardPile = player.discardPile,
                actionHistory = player.actionHistory,
            )
            val policy = module.declarationDiscardPolicy()
                ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
            val effect = policy.resolve(state, declaringPlayer, action, discard)
                ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
            val expectedScore = declaringPlayer.score.toLong() - effect.cost.toLong()
            val previousDiscardEntries = declaringPlayer.discardPile.entries
            val effectDiscardEntries = effect.player.discardPile.entries
            if (
                (effect.cost > 0 && effect.cost > declaringPlayer.score) ||
                expectedScore !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
                effect.player.id != declaringPlayer.id ||
                effect.player.initialSeatIndex != declaringPlayer.initialSeatIndex ||
                effect.player.seatWind != declaringPlayer.seatWind ||
                effect.player.hand != discard.hand ||
                effect.player.score.toLong() != expectedScore ||
                effectDiscardEntries.size != previousDiscardEntries.size + 1 ||
                effectDiscardEntries.dropLast(1) != previousDiscardEntries ||
                effectDiscardEntries.lastOrNull()?.tile != discard.tile ||
                effectDiscardEntries.lastOrNull()?.isTaken != false ||
                effect.player.passedTilesInRound != declaringPlayer.passedTilesInRound ||
                effect.player.actionHistory != declaringPlayer.actionHistory ||
                effect.player.copy(
                    hand = declaringPlayer.hand,
                    discardPile = declaringPlayer.discardPile,
                    score = declaringPlayer.score,
                    playerRuleState = effect.player.playerRuleState,
                    actionHistory = declaringPlayer.actionHistory,
                ) != declaringPlayer.copy(playerRuleState = effect.player.playerRuleState)
            ) {
                throw RuleContractViolationException(
                    GameAbortReasonIds.RULE_EXECUTION_FAILED,
                    "Declaration policy returned an unauthorized player mutation: $action",
                )
            }
            val updatedPlayer = effect.player.copy(hand = organized.hand)
                .recordAction(action)
                .recordAction(GameAction.Discard(tileId))
            val updatedState = state.copy(
                players = state.players.map { if (it.id == playerId) updatedPlayer else it },
                dynamicRuleState = effect.dynamicRuleState,
            )
            val resolved = DiscardReactionResolver.resolve(state, updatedState, module, playerId, discard.tile)
            val revealResult = if (resolved.abortiveDrawReason == null && resolved.tableState.pendingReaction == null) {
                WallRevealDecisionApplier.apply(
                    tableState = resolved.tableState,
                    checkpoint = WallRevealCheckpoint.AFTER_DISCARD_REACTIONS,
                    module = module,
                    actorPlayerId = playerId,
                    sourceAction = GameAction.Discard(tileId),
                )
            } else {
                WallRevealDecisionApplier.Result.Applied(resolved.tableState)
            }
            if (revealResult is WallRevealDecisionApplier.Result.Rejected) {
                return@update state to Outcome.Error(GameError.UnsupportedAction(gameId, playerId, revealResult.reasonId))
            }
            revealResult as WallRevealDecisionApplier.Result.Applied
            val stateAfterReveal = revealResult.tableState
            val abortive = if (resolved.abortiveDrawReason == null && resolved.tableState.pendingReaction == null) {
                postActionExhaustiveDrawResolverRegistry.resolve(
                    CompletedGameActionContext(playerId, GameAction.Discard(tileId), stateAfterReveal),
                    module,
                )
            } else {
                null
            }
            val finalState = abortive?.let {
                stateAfterReveal.recordAbortiveDraw(it, declarerId = null, module = module)
            } ?: stateAfterReveal
            finalState to Outcome.Success(
                InvocationResult(
                    tableState = finalState,
                    abortiveDrawReason = resolved.abortiveDrawReason ?: abortive,
                    newlyRevealedTileIds = revealResult.newlyRevealedTileIds,
                ),
            )
        }
        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        snapshotSynchronizer.syncAll(gameId)
        eventPublisher.publishToAllObservers(gameId, result.tableState.players.map { it.id }, playerId, action)
        eventPublisher.publishToAllObservers(gameId, result.tableState.players.map { it.id }, playerId, GameAction.Discard(tileId))
        result.abortiveDrawReason?.let { eventPublisher.publishToAllObservers(gameId, result.tableState.players.map { it.id }, playerId, GameAction.ExhaustiveDraw(it)) }
        val module = moduleRegistry.getModule(result.tableState.config)
        val index = result.tableState.players.indexOfFirst { it.id == playerId }
        if (index >= 0) {
            val updated = result.tableState.players[index]
            presentationPublisher.publishPlayerTilesUpdated(
                gameId,
                index,
                updated.hand.tiles.map { it.id },
                null,
                updated.hand.melds.map { it.toPresentation(result.tableState.config.revealsClosedKanTiles) },
                setAsideTileIds = module.setAsideTiles(updated).map { it.id },
            )
            presentationPublisher.publishDiscardPileUpdated(
                gameId,
                index,
                updated.discardPile.entries.filterNot { it.isTaken }.map { it.tile.id },
                (updated.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
                tileId,
            )
        }
        presentationPublisher.publishRuleStateUpdated(gameId)
        presentationPublisher.publishRoundInfoUpdated(gameId, result.tableState)
        presentationPublisher.publishGameActionDeclared(gameId, playerId, action)
        if (result.newlyRevealedTileIds.isNotEmpty()) {
            presentationPublisher.publishWallTilesRevealed(gameId, result.newlyRevealedTileIds)
        }
        return Outcome.Success(Unit)
    }

    /**
     * 交易成功後供歷史、事件與呈現階段使用的結果。
     *
     * @property tableState 寫入後的權威桌況。
     * @property abortiveDrawReason 本次宣告流程產生的途中流局原因；沒有流局時為 null。
     * @property newlyRevealedTileIds 捨牌反應結束後新公開的牌山牌張 ID。
     */
    private data class InvocationResult(
        val tableState: TableState,
        val abortiveDrawReason: ExhaustiveDrawReason?,
        val newlyRevealedTileIds: Set<Uuid>,
    )
}
