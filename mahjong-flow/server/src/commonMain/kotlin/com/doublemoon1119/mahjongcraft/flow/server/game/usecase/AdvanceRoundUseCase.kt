package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 連莊/過莊、開下一局的實例化用例。
 *
 * 這是「連莊/過莊/開下一局」系列子項的最後一塊：把前面各自獨立寫好、測好的
 * [com.doublemoon1119.mahjongcraft.logic.table.TableState.advanceRound]（連莊/過莊判定與局數/
 * 本場數/場風推進）與 [GameInitializer.startNextRound]（開下一局的新初始化路徑）串起來，
 * 做成一個真正會寫回 [GameRepository] 的 use case。
 *
 * 由誰、在什麼時機呼叫本用例（例如接在 [DeclareTsumoUseCase]/[RespondToDiscardUseCase] 成功之後
 * 自動觸發，或由前端明確請求）是更外層（伺服器流程編排）的決定，不在這裡處理；呼叫前應確認本局
 * 已經結算完畢（沒有 `pendingReaction`）。
 *
 * 「莊家是否連莊」目前能偵測「莊家胡牌」與「莊家一般流局時聽牌（含流局滿貫）」兩條依據：
 * [DeclareTsumoUseCase]/[RespondToDiscardUseCase] 會把 [GameAction.Tsumo]/[GameAction.Ron] 記錄進
 * 贏家的 `actionHistory`；[DeclareExhaustiveDrawUseCase] 則只把 [GameAction.ExhaustiveDraw] 記錄進
 * 聽牌玩家（含流局滿貫成立者）的 `actionHistory`，不聽的玩家不記錄。
 * [GameInitializer.startNextRound] 每次開新的一局都會把 `actionHistory` 重置成空，所以「這局的
 * `actionHistory` 裡有沒有 Tsumo/Ron/ExhaustiveDraw」可以直接拿來判斷「莊家這局是不是贏家之一，
 * 或流局時是否聽牌」。九種九牌等途中流局的連莊依據（莊家固定連莊，不需要判斷聽牌與否）留給
 * 對應子項擴充。
 *
 * 整場對局已結束（[com.doublemoon1119.mahjongcraft.logic.table.RoundAdvancementResult.isMatchOver]）
 * 時，本用例只誠實回報「已經結束」，不做任何額外收尾（房間清理、最終排名等留給未來獨立的單位）——
 * 不會開新的一局，`TableState` 維持呼叫前的樣子不變，也不會同步快照或廣播事件。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class AdvanceRoundUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher,
) {
    /**
     * 執行連莊/過莊、開下一局邏輯。
     *
     * @param gameId 對局 Uuid。
     * @return 執行結果，成功時為 [AdvanceRoundResult]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<AdvanceRoundResult, GameError> {
        // 1. 以原子方式讀取桌況、計算連莊/過莊結果並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                else -> {
                    val module = moduleRegistry.getModule(state.config)
                    val dealer = state.players.first { it.currentWind == Wind.EAST }
                    val dealerRepeats = dealer.actionHistory.any {
                        it is GameAction.Tsumo || it is GameAction.Ron || it is GameAction.ExhaustiveDraw
                    }
                    val roundAdvancement = state.advanceRound(dealerRepeats)

                    if (roundAdvancement.isMatchOver) {
                        state to Outcome.Success(AdvanceRoundResult(state, isMatchOver = true))
                    } else {
                        val newState = GameInitializer.startNextRound(
                            gameId = gameId,
                            roundAdvancement = roundAdvancement,
                            previousDynamicRuleState = state.dynamicRuleState,
                            module = module,
                        )
                        newState to Outcome.Success(AdvanceRoundResult(newState, isMatchOver = false))
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        if (result.isMatchOver) return Outcome.Success(result)
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        val observers = gameSnapshotRepository.getAllObservers(gameId)
        observers.forEach { observerId ->
            gameSnapshotRepository.setSnapshot(observerId, newState.toSnapshot(observerId))
        }

        // 3. 廣播「下一局已開始」事件；RoundStarted 沒有實際執行者，比照 GameStarted 的既有慣例，
        //    填入新莊家的 Uuid
        val newDealerId = newState.players.first { it.currentWind == Wind.EAST }.id
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, newDealerId, GameAction.RoundStarted)
        }

        return Outcome.Success(result)
    }

    /**
     * [AdvanceRoundUseCase] 的執行結果。
     *
     * @property tableState 套用後的桌況——整場對局已結束時，維持呼叫前的桌況不變（不會開新的一局）；
     *           否則為已經開始下一局的新桌況。
     * @property isMatchOver 整場對局是否已依規則的 `GameLength` 結束。
     */
    data class AdvanceRoundResult(val tableState: TableState, val isMatchOver: Boolean)
}
