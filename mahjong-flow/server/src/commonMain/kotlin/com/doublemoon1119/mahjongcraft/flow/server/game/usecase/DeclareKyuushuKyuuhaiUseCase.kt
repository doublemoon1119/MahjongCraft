package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiLegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 宣告九種九牌（Kyuushu Kyuuhai）的實例化用例。
 *
 * 這是「流局判定」系列子項的第二塊：接上 [RiichiLegalActionValidator] 早就已經有的
 * 偵測邏輯（第一巡摸牌後，手牌含摸到的牌擁有 9 種以上么九牌），讓玩家真正能夠宣告這個流局。
 *
 * 是否合法完全交給該規則模組自己的 `LegalActionValidator`：現在
 * [GameAction.ExhaustiveDraw] 是不是合法動作——這裡不重新實作么九牌計數或第一巡判斷，理由與
 * [DeclareRiichiUseCase] 相同。九種九牌屬於途中流局（比照子項 1 查證並落實的區分）：莊家固定
 * 連莊、不結算任何點數、供託延續到下一局（不呼叫 `collectStickPot`）——不需要新增任何 [MahjongRuleModule] 規則鉤子，
 * 直接把 `getLegalActions` 回傳的 [GameAction.ExhaustiveDraw] 實例本身（含其規則無關的 `reason` 屬性）
 * 記錄進**全員**的 `actionHistory`，讓 [AdvanceRoundUseCase] 既有的連莊判斷式自動得出正確結果。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DeclareKyuushuKyuuhaiUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
) {
    /**
     * 執行九種九牌宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起宣告的玩家 Uuid。
     * @return 宣告結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))

                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))

                state.currentPlayer.hand.lastDrawn == null ->
                    state to Outcome.Error(GameError.UnsupportedAction(gameId, playerId))

                else -> {
                    val incomingTile = state.currentPlayer.hand.lastDrawn
                        ?: return@update state to Outcome.Error(GameError.UnsupportedAction(gameId, playerId))
                    val module = moduleRegistry.getModule(state.config)

                    // LegalActionValidator 的既有慣例是傳入的手牌「不含胡牌張」，胡牌張只透過
                    // incomingTile 參數單獨傳入；剝離手法與 DeclareTsumoUseCase 相同。
                    val playerForCheck =
                        state.currentPlayer.copy(hand = state.currentPlayer.hand.copy(lastDrawn = null))
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = playerForCheck,
                        sourceAction = GameAction.Draw,
                        sourceDirection = RelativeDirection.Self,
                        incomingTile = incomingTile,
                    )
                    val exhaustiveDraw = legalActions.filterIsInstance<GameAction.ExhaustiveDraw>().firstOrNull()
                        ?: return@update state to Outcome.Error(GameError.UnsupportedAction(gameId, playerId))

                    // 途中流局：莊家固定連莊、不結算任何點數，供託延續到下一局。把 ExhaustiveDraw
                    // 記錄進全員（不只莊家）的 actionHistory，讓 AdvanceRoundUseCase 既有的判斷式
                    // 自動得出「連莊」的結果，不需要額外分支。
                    val updatedPlayers =
                        state.players.map { it.recordAction(GameAction.ExhaustiveDraw(exhaustiveDraw.reason)) }
                    val newState = state.copy(players = updatedPlayers)

                    newState to Outcome.Success(KyuushuKyuuhaiResult(newState, exhaustiveDraw.reason))
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播流局事件；跟 DeclareTsumoUseCase/DeclareRiichiUseCase 一樣，actor 是宣告的玩家本人
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.ExhaustiveDraw(result.reason))
        }

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [reason] 能跟著 [tableState] 一起帶出
     * `gameRepository.update` 的作用域，供廣播事件時使用（[reason] 不是 [TableState] 的欄位，
     * 無法在作用域外從 [tableState] 反推）。
     */
    private data class KyuushuKyuuhaiResult(val tableState: TableState, val reason: ExhaustiveDrawReason)
}
