package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 回應搶槓反應視窗（榮和/過）的實例化用例。
 *
 * 對應 [DeclareKanUseCase] 宣告暗槓/加槓後開啟的 `TableState.pendingChankan`：每位有資格搶槓的
 * 玩家各自呼叫一次本用例記錄自己的回應，直到所有有資格的玩家都回應完畢，才會實際結算。
 *
 * 只信任 [GameAction.Ron]/[GameAction.Pass]：`LegalActionValidator.getLegalActions` 的「反應」
 * 分支不分辨 `sourceAction` 是捨牌還是槓牌，會一併算出吃/碰/明槓資格，但搶槓情境下這些都不合法
 * （這張牌不是捨牌）——回應驗證時額外過濾，只接受 `Ron`/`Pass`。
 *
 * 全員放過時，原本被 [DeclareKanUseCase] 暫緩的副露套用與嶺上摸牌會在這裡「補做」
 * （[KanDeclarationApplier]）；有人搶槓成功時，這次暗槓/加槓視為未成立，透過
 * [RonSettlementResolver] 結算榮和（搶槓的宣告者扮演「放銃者」的角色）。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與規則特有邏輯。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class RespondToChankanUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行搶槓反應回應邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起回應的玩家 Uuid。
     * @param action 欲執行的回應動作（[GameAction.Pass] 或 [GameAction.Ron]）。
     * @return 回應結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, action: GameAction): Outcome<Unit, GameError> {
        val outcome = gameRepository.update(gameId) { state ->
            val pending = state?.pendingChankan
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                pending == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId !in pending.eligiblePlayerIds ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId in pending.responses ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                else -> {
                    val module = moduleRegistry.getModule(state.config)
                    val responder = state.players.first { it.id == playerId }
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = responder,
                        sourceAction = pending.kanAction,
                        sourceDirection = state.relativeDirectionOf(playerId, pending.declarerId),
                        incomingTile = pending.robbedTile,
                    ).filter { it is GameAction.Ron || it == GameAction.Pass }
                    if (action !in legalActions) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                    }

                    val newPending = pending.copy(responses = pending.responses + (playerId to action))
                    if (!newPending.isComplete) {
                        val newState = state.copy(pendingChankan = newPending)
                        return@update newState to Outcome.Success(ChankanResult(newState, drawHappened = false))
                    }

                    val ronWinnerIds = newPending.responses.filterValues { it is GameAction.Ron }.keys
                    if (ronWinnerIds.isNotEmpty()) {
                        val resolved = RonSettlementResolver.resolve(
                            state = state,
                            players = state.players,
                            discarderId = pending.declarerId,
                            winningTile = pending.robbedTile,
                            module = module,
                            winnerIds = ronWinnerIds,
                            isRobbingKan = pending.kanAction.type == GameAction.KanType.ADDED_KAN,
                        )
                        val newState = resolved?.copy(pendingChankan = null)
                            ?: state.copy(pendingChankan = newPending)
                        newState to Outcome.Success(ChankanResult(newState, drawHappened = false))
                    } else {
                        val applied = KanDeclarationApplier.apply(state, pending.declarerId, pending.kanAction, pending.robbedTile, module)
                        if (applied.rinshanTile == null) {
                            return@update state to Outcome.Error(GameError.WallExhausted(gameId))
                        }
                        val newState = applied.tableState.copy(pendingChankan = null)
                        newState to Outcome.Success(
                            ChankanResult(newState, drawHappened = true, declarerId = pending.declarerId),
                        )
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        snapshotSynchronizer.syncAll(gameId)

        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, action)
            if (result.drawHappened) {
                eventPublisher.publish(gameId, player.id, playerId, GameAction.Draw)
            }
        }

        // 全員放過、原本暫緩的副露補做成立時，重新呈現宣告者的整份副露列表
        result.declarerId?.let { declarerId ->
            val declarerSeatIndex = newState.players.indexOfFirst { it.id == declarerId }
            val declarer = newState.players[declarerSeatIndex]
            presentationPublisher.publishMeldsUpdated(
                gameId,
                declarerSeatIndex,
                declarer.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
            )
        }

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [drawHappened]／[declarerId] 能跟著 [tableState] 一起帶出
     * `gameRepository.update` 的作用域，供廣播事件／觸發呈現時使用。[drawHappened] 為 true 代表全員
     * 放過、原本暫緩的副露與嶺上摸牌已在這次呼叫補做完成，需要額外廣播 [GameAction.Draw]。
     * [declarerId] 只在 [drawHappened] 為 true 時才有值，供呼叫端重新呈現宣告者的副露。
     */
    private data class ChankanResult(val tableState: TableState, val drawHappened: Boolean, val declarerId: Uuid? = null)
}
