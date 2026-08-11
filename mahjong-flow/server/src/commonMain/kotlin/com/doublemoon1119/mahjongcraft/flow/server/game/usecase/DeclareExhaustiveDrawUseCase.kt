package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 宣告一般流局（牌山摸盡）的實例化用例。
 *
 * 這是「流局判定」系列子項的第一塊：處理牌山摸盡觸發的一般流局結算（聽牌/不聽罰符，或流局滿貫
 * 成立時視為自摸滿貫），是後續子項（九種九牌、三家和了流局、四風連打+四家立直）共用的機制骨架。
 *
 * 系統觸發、無 `playerId` 參數——一般流局不是玩家主動發起的操作，理由與
 * [AdvanceRoundUseCase] 相同。由誰、在什麼時機呼叫本用例（例如伺服器捕捉到 [DrawTileUseCase]
 * 回傳 [GameError.WallExhausted] 後接著呼叫）是更外層（伺服器流程編排）的決定，不在這裡處理；
 * 呼叫前應確認牌山確實已經摸盡。
 *
 * 聽牌判定、流局滿貫偵測與點數換算、不聽罰符拆分等規則相關的計算完全交給
 * [com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.declareExhaustiveDraw]
 * 處理——這裡刻意不轉型成任何規則專屬的具體型別，理由與 [DeclareTsumoUseCase] 相同。
 *
 * 只把 [GameAction.ExhaustiveDraw] 記錄進聽牌玩家（含流局滿貫成立者）的 `actionHistory`，
 * 不聽的玩家不記錄——[AdvanceRoundUseCase] 判斷連莊與否時，只要檢查莊家的 `actionHistory`
 * 裡有沒有 `ExhaustiveDraw`，就能同時涵蓋「莊家聽牌」與「莊家流局滿貫」兩種連莊依據。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DeclareExhaustiveDrawUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
) {
    /**
     * 執行一般流局宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @return 執行結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、計算流局結算並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                else -> {
                    val module = moduleRegistry.getModule(state.config)

                    // 這個規則不支援流局結算時 declareExhaustiveDraw 回傳 null。
                    val settlement = module.declareExhaustiveDraw(state)
                        ?: return@update state to Outcome.Error(GameError.UnsupportedAction(gameId))

                    // 這次流局若有玩家該收下場上所有供託（如立直棒，例如日麻的流局滿貫成立者），
                    // 不支援供託機制的規則回傳 null
                    val stickPot = if (settlement.stickPotCollectorPlayerIds.isNotEmpty()) {
                        module.collectStickPot(state)
                    } else {
                        null
                    }
                    val stickPotDeltas = distributeStickPot(state, settlement.stickPotCollectorPlayerIds, stickPot?.second ?: 0)

                    val finalDeltas = (settlement.scoreDeltas.keys + stickPotDeltas.keys).associateWith { id ->
                        (settlement.scoreDeltas[id] ?: 0) + (stickPotDeltas[id] ?: 0)
                    }

                    val updatedPlayers = state.players.map { player ->
                        val withScore = player.copy(score = player.score + (finalDeltas[player.id] ?: 0))
                        if (player.id in settlement.tenpaiPlayerIds) {
                            withScore.recordAction(GameAction.ExhaustiveDraw(settlement.reason))
                        } else {
                            withScore
                        }
                    }
                    val newState = state.copy(
                        players = updatedPlayers,
                        dynamicRuleState = stickPot?.first ?: state.dynamicRuleState,
                    )

                    newState to Outcome.Success(ExhaustiveDrawResult(newState, settlement.reason))
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播流局事件；跟 GameAction.RoundStarted 一樣沒有實際執行者，比照既有慣例填入莊家 Uuid
        val dealerId = newState.players.first { it.currentWind == Wind.EAST }.id
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, dealerId, GameAction.ExhaustiveDraw(result.reason))
        }

        return Outcome.Success(Unit)
    }

    /**
     * 決定供託由誰收下：多位玩家同時該收下供託時（例如日麻的流局滿貫同時多位成立，機率極低），
     * 頭跳——供託全數由座位順序最接近莊家的一人取得，其餘成立者不收供託。與這個專案在一般榮和
     * 多家和時處理供託分配的既有做法一致（見 [RonSettlementResolver.resolve]：多位贏家時供託由
     * [TableState.nearestPlayerInTurnOrder] 判定的那位收下，不是均分），榮和沒有「放銃者」可用
     * 來判斷順位，改以莊家為基準。
     *
     * @return 收下者應得的供託金額（單一 entry 的 map）；無人該收下（或供託金額為 0）時回傳空 map。
     */
    private fun distributeStickPot(state: TableState, stickPotCollectorPlayerIds: Set<Uuid>, stickPotAmount: Int): Map<Uuid, Int> {
        if (stickPotCollectorPlayerIds.isEmpty() || stickPotAmount == 0) return emptyMap()
        if (stickPotCollectorPlayerIds.size == 1) return mapOf(stickPotCollectorPlayerIds.first() to stickPotAmount)

        val dealerId = state.players.first { it.currentWind == Wind.EAST }.id
        val collectorId = state.nearestPlayerInTurnOrder(dealerId, stickPotCollectorPlayerIds)
        return mapOf(collectorId to stickPotAmount)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [reason] 能跟著 [tableState] 一起帶出
     * `gameRepository.update` 的作用域，供廣播事件時使用（[reason] 不是 [TableState] 的欄位，
     * 無法在作用域外從 [tableState] 反推）。
     */
    private data class ExhaustiveDrawResult(val tableState: TableState, val reason: ExhaustiveDrawReason)
}
