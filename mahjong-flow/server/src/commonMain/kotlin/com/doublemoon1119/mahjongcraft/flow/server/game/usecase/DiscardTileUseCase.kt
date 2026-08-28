package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
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
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 捨牌的實例化用例。
 *
 * 負責處理玩家的捨牌請求，包含回合驗證、手牌與牌河狀態更新，以及快照與事件的同步。
 *
 * 捨牌後其他玩家是否有資格吃/碰/槓/榮和這張牌、一炮多響時依 [MultiRonPolicy]
 * 決定實際開放給誰、[RonResolution.ABORTIVE_DRAW] 是否直接觸發流局，這些邏輯與 [DeclareRiichiUseCase]
 * （立直宣告牌）共用，交給 [DiscardReactionResolver] 處理，詳見其 KDoc。
 *
 * 除了一炮多響判定為流局之外，這張捨牌若沒有任何人可以吃/碰/槓/榮和，還會額外檢查是否構成四風連打
 * （[MahjongRuleModule.resolveSuufonRenda]）。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌，見該類別 KDoc。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class DiscardTileUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
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

                        // 立直鎖定手牌結構：只能打剛摸到的牌（摸切），不能改打手牌裡其他牌——立直宣告
                        // 本身一定是門前清，鳴牌後準備捨牌（justClaimedMeld、lastDrawn == null）不會
                        // 發生在立直玩家身上，這裡不需要另外排除。
                        if (module.isPlayerInRiichi(state.currentPlayer) && lastDrawn != null && lastDrawn.id != tileId) {
                            return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))
                        }

                        val discardedTile = discardResult.tile
                        val organizedHand = if (handSortPreferenceStore.isEnabled(playerId)) {
                            discardResult.hand.organize(module.tileOrder)
                        } else {
                            discardResult.hand
                        }

                        // 立直中摸切棄胡（原本自摸合法卻選擇打出摸到的牌）視同見逃す，本局起永久振聽——
                        // 手法比照 GetLegalActionsUseCase own-turn 分支：先把 lastDrawn 移除、再當
                        // incomingTile 傳入，避免在 standingTiles 裡重複計算這張牌。
                        val playerAfterDeclineCheck = if (module.isPlayerInRiichi(state.currentPlayer) &&
                            lastDrawn != null &&
                            lastDrawn.id == tileId
                        ) {
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

                        // 沒有觸發一炮多響流局、也沒有人可反應時，額外檢查是否構成四風連打。
                        val suufonReason =
                            if (resolved.abortiveDrawReason == null && resolved.tableState.pendingReaction == null) {
                                module.resolveSuufonRenda(resolved.tableState)
                            } else {
                                null
                            }
                        val finalResult = if (suufonReason != null) {
                            resolved.copy(
                                tableState = resolved.tableState.copy(
                                    players = resolved.tableState.players.map {
                                        it.recordAction(
                                            GameAction.ExhaustiveDraw(
                                                suufonReason,
                                            ),
                                        )
                                    },
                                ),
                                abortiveDrawReason = suufonReason,
                            )
                        } else {
                            resolved
                        }

                        finalResult.tableState to Outcome.Success(finalResult)
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 通知對局內的所有玩家；流局有觸發時，先廣播捨牌事件、再接著廣播流局事件
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Discard(tileId))
            result.abortiveDrawReason?.let { reason ->
                eventPublisher.publish(gameId, player.id, playerId, GameAction.ExhaustiveDraw(reason))
            }
        }

        // 4. 觸發平台呈現層：重新排列立牌列（涵蓋摸切、或打手牌併入摸到的牌兩種情況；副露本身雖然
        // 沒變，仍要一併帶上讓手牌讓開偏移量算得準），並把捨棄的牌移到牌河
        val seatIndex = newState.players.indexOfFirst { it.id == playerId }
        val discarder = newState.players[seatIndex]
        val dealerSeatIndex = newState.dealerIndex
        presentationPublisher.publishPlayerAreaUpdated(
            gameId,
            seatIndex,
            discarder.hand.tiles.map { it.id },
            null,
            discarder.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
            comboStickCount = if (seatIndex == dealerSeatIndex) newState.comboCount else 0,
        )
        presentationPublisher.publishDiscardPileUpdated(
            gameId,
            seatIndex,
            discarder.discardPile.entries.filterNot { it.isTaken }.map { it.tile.id },
            (discarder.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            newlyDiscardedTileId = tileId,
        )

        return Outcome.Success(Unit)
    }
}
