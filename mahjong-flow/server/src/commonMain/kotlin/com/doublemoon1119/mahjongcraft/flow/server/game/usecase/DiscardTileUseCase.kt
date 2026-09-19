package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.recordExhaustiveDrawForAllPlayers
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 捨牌的實例化用例。
 *
 * 負責處理玩家的捨牌請求，包含回合驗證、手牌與牌河狀態更新，以及快照與事件的同步。
 *
 * 捨牌後其他玩家是否有資格吃/碰/槓/榮和這張牌、一炮多響時依 [MultiRonPolicy]
 * 決定實際開放給誰、[RonResolution.ABORTIVE_DRAW] 是否直接觸發流局，這些邏輯與其他會打出一張牌的宣告
 * 用例共用，交給 [DiscardReactionResolver] 處理，詳見其 KDoc。
 *
 * 規則可透過 [MahjongRuleModule.forcedDiscardTileId] 限定這次只能打出的牌；玩家摸牌後原本可以自摸卻選擇
 * 捨牌時，一律呼叫 [MahjongRuleModule.onPlayerDeclinedWin]，由規則決定是否產生後果。
 *
 * 除了一炮多響判定為流局之外，這張捨牌若沒有任何人可以吃/碰/槓/榮和，還會額外透過
 * [postActionExhaustiveDrawResolverRegistry] 檢查是否構成主動觸發的途中流局（例如日麻的四風連打）。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌，見該類別 KDoc。
 * @property postActionExhaustiveDrawResolverRegistry 捨牌完成後主動觸發途中流局的判定 registry。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class DiscardTileUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
    private val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行捨牌邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起捨牌請求的玩家 Uuid。
     * @param tileId 欲捨棄牌的唯一識別碼。
     * @return 捨牌結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, tileId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))

                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))

                state.currentPlayer.hand.lastDrawn == null && !state.currentPlayer.justClaimedMeld ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))

                else -> {
                    val discardResult = state.currentPlayer.hand.discardById(tileId)
                    if (discardResult == null) {
                        state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))
                    } else {
                        val module = moduleRegistry.getModule(state.config)
                        val lastDrawn = state.currentPlayer.hand.lastDrawn

                        // 規則可以限定這次只能打出哪一張牌。
                        val forcedTileId = module.forcedDiscardTileId(state, state.currentPlayer)
                        if (forcedTileId != null && forcedTileId != tileId) {
                            return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))
                        }

                        val discardedTile = discardResult.tile
                        val organizedHand = if (handSortPreferenceStore.isEnabled(playerId)) {
                            discardResult.hand.organize(module.tileOrder)
                        } else {
                            discardResult.hand
                        }

                        // 摸牌後原本可以自摸卻選擇捨牌，視為放棄和牌，後果交給規則決定。lastDrawn 先從手牌
                        // 移除、再當 incomingTile 傳入，避免在 standingTiles 裡重複計算這張牌。
                        val playerAfterDeclineCheck = if (lastDrawn != null) {
                            val playerForCheck = state.currentPlayer.copy(hand = state.currentPlayer.hand.copy(lastDrawn = null))
                            val ownTurnActions = module.createLegalActionValidator().getLegalActions(
                                tableState = state,
                                player = playerForCheck,
                                sourceAction = GameAction.Draw,
                                sourceDirection = RelativeDirection.Self,
                                incomingTile = lastDrawn,
                            )
                            if (ownTurnActions.any { it is GameAction.Tsumo }) {
                                module.onPlayerDeclinedWin(state.currentPlayer)
                            } else {
                                state.currentPlayer
                            }
                        } else {
                            state.currentPlayer
                        }

                        val updatedPlayer = playerAfterDeclineCheck
                            .copy(
                                hand = organizedHand,
                                discardPile = playerAfterDeclineCheck.discardPile.discardTile(discardedTile),
                            )
                            .recordAction(GameAction.Discard(tileId))
                        val updatedPlayers = state.players.map { if (it.id == playerId) updatedPlayer else it }
                        val stateAfterDiscard = state.copy(players = updatedPlayers)

                        val resolved =
                            DiscardReactionResolver.resolve(state, stateAfterDiscard, module, playerId, discardedTile)

                        val revealResult = if (
                            resolved.abortiveDrawReason == null && resolved.tableState.pendingReaction == null
                        ) {
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
                            return@update state to Outcome.Error(
                                GameError.UnsupportedAction(gameId, playerId, revealResult.reasonId),
                            )
                        }
                        revealResult as WallRevealDecisionApplier.Result.Applied
                        val resolvedAfterReveal = resolved.copy(tableState = revealResult.tableState)

                        // 沒有觸發一炮多響流局、也沒有人可反應時，額外檢查是否構成主動觸發的途中流局。
                        val postActionExhaustiveDrawReason =
                            if (
                                resolvedAfterReveal.abortiveDrawReason == null &&
                                resolvedAfterReveal.tableState.pendingReaction == null
                            ) {
                                postActionExhaustiveDrawResolverRegistry.resolve(
                                    CompletedGameActionContext(
                                        actorPlayerId = playerId,
                                        action = GameAction.Discard(tileId),
                                        tableState = resolvedAfterReveal.tableState,
                                    ),
                                    module,
                                )
                            } else {
                                null
                            }
                        val finalResult = if (postActionExhaustiveDrawReason != null) {
                            resolvedAfterReveal.copy(
                                tableState = resolvedAfterReveal.tableState.recordExhaustiveDrawForAllPlayers(
                                    postActionExhaustiveDrawReason,
                                ),
                                abortiveDrawReason = postActionExhaustiveDrawReason,
                            )
                        } else {
                            resolvedAfterReveal
                        }

                        finalResult.tableState to Outcome.Success(
                            DiscardResult(finalResult, revealResult.newlyRevealedTileIds),
                        )
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.resolution.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 通知在場玩家與旁觀者；流局有觸發時，先廣播捨牌事件、再接著廣播流局事件
        val seatedPlayerIds = newState.players.map { it.id }
        eventPublisher.publishToTable(gameId, seatedPlayerIds, playerId, GameAction.Discard(tileId))
        result.resolution.abortiveDrawReason?.let { reason ->
            eventPublisher.publishToTable(gameId, seatedPlayerIds, playerId, GameAction.ExhaustiveDraw(reason))
        }

        // 4. 觸發平台呈現層：重新排列立牌列（涵蓋摸切、或打手牌併入摸到的牌兩種情況；副露本身雖然
        // 沒變，仍要一併帶上讓手牌讓開偏移量算得準），並把捨棄的牌移到牌河
        val seatIndex = newState.players.indexOfFirst { it.id == playerId }
        val discarder = newState.players[seatIndex]
        presentationPublisher.publishPlayerAreaUpdated(
            gameId,
            seatIndex,
            discarder.hand.tiles.map { it.id },
            null,
            discarder.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
        )
        presentationPublisher.publishDiscardPileUpdated(
            gameId,
            seatIndex,
            discarder.discardPile.entries.filterNot { it.isTaken }.map { it.tile.id },
            (discarder.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            newlyDiscardedTileId = tileId,
        )
        if (result.newlyRevealedTileIds.isNotEmpty()) {
            presentationPublisher.publishWallTilesRevealed(gameId, result.newlyRevealedTileIds)
        }

        return Outcome.Success(Unit)
    }

    /** 將捨牌反應結果與該 checkpoint 新公開的牌張一起帶出原子更新區塊。 */
    private data class DiscardResult(
        val resolution: DiscardReactionResolver.Result,
        val newlyRevealedTileIds: Set<Uuid> = emptySet(),
    ) {
        /** 更新後的權威桌況。 */
        val tableState: TableState get() = resolution.tableState
    }
}
