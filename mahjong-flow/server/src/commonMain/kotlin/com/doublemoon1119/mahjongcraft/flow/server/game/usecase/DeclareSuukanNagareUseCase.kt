package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 宣告四槓散了（Suukan Nagare）的實例化用例。
 *
 * 這是「暗槓/加槓」系列子項的最後一塊：全場玩家合計槓了 4 次、且並非全部由同一人達成時，觸發
 * 途中流局（莊家固定連莊、不結算任何點數），比照「流局判定」大項已落實的途中流局處理慣例。
 *
 * 系統觸發、無 `playerId` 參數，理由與 [DeclareExhaustiveDrawUseCase] 相同——四槓散了不是玩家
 * 主動發起的操作。**刻意不內嵌在 [DeclareKanUseCase]/[RespondToKanUseCase] 套用嶺上摸牌的
 * 同一次呼叫裡**：達成第 4 個槓子的那次槓牌，摸到嶺上牌後，玩家理論上應該有機會先嘗試嶺上開花
 * 自摸（[DeclareTsumoUseCase]，玩家後續另外呼叫的動作）——若在同一次呼叫裡就自動判斷四槓散了
 * 並結束此局，會讓玩家完全沒有機會宣告嶺上開花。由誰、在什麼時機呼叫本用例（例如伺服器偵測到
 * 玩家對嶺上牌選擇不自摸之後接著呼叫）是更外層（伺服器流程編排）的決定，不在這裡處理。
 *
 * 是否構成四槓散了完全交給 [MahjongRuleModule.resolveSuukanNagare]
 * 判斷，這裡不重新實作槓子計數邏輯。
 *
 * 把 [GameAction.ExhaustiveDraw] 記錄進**全員**（不只莊家）的 `actionHistory`——途中流局莊家
 * 固定連莊，讓 [AdvanceRoundUseCase] 既有的判斷式自動得出「連莊」的結果，不需要額外分支。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DeclareSuukanNagareUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
) {
    /**
     * 執行四槓散了宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @return 執行結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                else -> {
                    val module = moduleRegistry.getModule(state.config)

                    // 目前的桌況不構成四槓散了（或此規則不支援）時 resolveSuukanNagare 回傳 null。
                    val reason = module.resolveSuukanNagare(state)
                        ?: return@update state to Outcome.Error(GameError.UnsupportedAction(gameId))

                    val updatedPlayers = state.players.map { it.recordAction(GameAction.ExhaustiveDraw(reason)) }
                    val newState = state.copy(players = updatedPlayers)

                    newState to Outcome.Success(SuukanNagareResult(newState, reason))
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播流局事件；跟 GameAction.RoundStarted/DeclareExhaustiveDrawUseCase 一樣沒有實際
        // 執行者，比照既有慣例填入莊家 Uuid
        val dealerId = newState.dealerPlayerId
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, dealerId, GameAction.ExhaustiveDraw(result.reason))
        }

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [reason] 能跟著 [tableState] 一起帶出
     * `gameRepository.update` 的作用域，供廣播事件時使用（[reason] 不是 [TableState] 的欄位，
     * 無法在作用域外從 [tableState] 反推）。
     */
    private data class SuukanNagareResult(val tableState: TableState, val reason: ExhaustiveDrawReason)
}
