package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
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
 * 時，本用例只把這個事實記到 [com.doublemoon1119.mahjongcraft.flow.common.game.model.Game.isMatchOver]，
 * 不做任何額外收尾（房間清理、最終排名等留給未來獨立的單位）——不會開新的一局，`TableState`
 * 維持呼叫前的樣子不變，也不會同步快照或廣播事件。務必記下這個事實：`AiTurnDriver`／
 * `ForcedAutoPlayDriver` 都靠它提前跳過已結束的對局，否則牌山已空的桌況會被反覆嘗試摸牌、
 * 反覆觸發流局結算。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class AdvanceRoundUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行連莊/過莊、開下一局邏輯。
     *
     * @param gameId 對局 Uuid。
     * @return 執行結果，成功時為 [AdvanceRoundResult]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<AdvanceRoundResult, GameError> {
        // 1. 以原子方式讀取桌況、計算連莊/過莊結果並寫回
        val outcome = gameRepository.updateGame(gameId) { game ->
            val state = game?.tableState
            when {
                game == null || state == null -> game to Outcome.Error(GameError.GameNotFound(gameId))
                else -> {
                    val module = moduleRegistry.getModule(state.config)
                    val dealer = state.players.first { it.currentWind == Wind.EAST }
                    val dealerRepeats = dealer.actionHistory.any {
                        it is GameAction.Tsumo || it is GameAction.Ron || it is GameAction.ExhaustiveDraw
                    }
                    val roundAdvancement = state.advanceRound(dealerRepeats)

                    if (roundAdvancement.isMatchOver) {
                        val advanceOutcome = AdvanceRoundOutcome(
                            result = AdvanceRoundResult(state, isMatchOver = true),
                            diceRoll = null,
                            wallStructure = null,
                        )
                        // 對局已經結束：記下這個事實，讓 AiTurnDriver／ForcedAutoPlayDriver 之後都
                        // 跳過這場對局，不會對已經沒有牌可摸的桌況繼續重複嘗試、重複觸發流局結算。
                        game.copy(isMatchOver = true) to Outcome.Success(advanceOutcome)
                    } else {
                        val initializationResult = GameInitializer.startNextRound(
                            gameId = gameId,
                            roundAdvancement = roundAdvancement,
                            previousDynamicRuleState = state.dynamicRuleState,
                            module = module,
                        )
                        val newState = initializationResult.tableState
                        val advanceOutcome = AdvanceRoundOutcome(
                            result = AdvanceRoundResult(newState, isMatchOver = false),
                            diceRoll = initializationResult.diceRoll,
                            wallStructure = initializationResult.wallStructure,
                        )
                        game.copy(tableState = newState) to Outcome.Success(advanceOutcome)
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val advanceOutcome = (outcome as Outcome.Success).value
        val result = advanceOutcome.result
        if (result.isMatchOver) return Outcome.Success(result)
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播「下一局已開始」事件；RoundStarted 沒有實際執行者，比照 GameStarted 的既有慣例，
        //    填入新莊家的 Uuid
        val newDealerId = newState.players.first { it.currentWind == Wind.EAST }.id
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, newDealerId, GameAction.RoundStarted)
        }

        // 4. 觸發平台呈現層：規則不支援開門流程時皆為 null，直接跳過
        advanceOutcome.diceRoll?.let { presentationPublisher.publishDiceRoll(gameId, it) }
        advanceOutcome.wallStructure?.let { presentationPublisher.publishWallStructure(gameId, it) }

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

    /**
     * [invoke] 內部使用的中介結果，把只有平台呈現層需要的一次性擲骰／牌牆結構資料，跟對外公開的
     * [AdvanceRoundResult] 分開夾帶，避免污染既有呼叫端（例如 `GameFlowCoordinator`）只關心的
     * 對外回傳形狀。
     */
    private data class AdvanceRoundOutcome(
        val result: AdvanceRoundResult,
        val diceRoll: DiceRollResult?,
        val wallStructure: Map<Uuid, TileWallPosition>?,
    )
}
