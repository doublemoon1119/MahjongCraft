package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import kotlin.uuid.Uuid

/** 台灣麻將逐筆計算底台、莊家與連莊付款，不修改玩家分數。 */
object TaiwanPaymentCalculator {
    /**
     * 計算自摸三家付款或放槍者單獨付款；特殊花胡亦使用相同的付款結構。
     *
     * @param config 底分與每台分數。
     * @param playerIds 四位玩家的不同識別碼。
     * @param winnerId 收款玩家。
     * @param dealerId 本局莊家。
     * @param handTai 本次有效手牌或獨立花胡的台數，不含莊家台。
     * @param dealerStreak 目前已連莊次數，初莊為0。
     * @param discarderId 單獨付款者；自摸或八仙過海為null，七搶一為被搶花者。
     * @return 贏家總收入與每位付款者金額。
     */
    fun calculate(
        config: TaiwanScoreConfig,
        playerIds: List<Uuid>,
        winnerId: Uuid,
        dealerId: Uuid,
        handTai: Int,
        dealerStreak: Int,
        discarderId: Uuid?,
    ): WinSettlementResult {
        require(playerIds.size == 4 && playerIds.distinct().size == 4) { "Taiwan settlement requires four distinct players" }
        require(winnerId in playerIds && dealerId in playerIds) { "Winner and dealer must be seated" }
        require(discarderId == null || (discarderId in playerIds && discarderId != winnerId)) { "Payer must be a seated opponent" }
        require(handTai >= 0 && dealerStreak >= 0) { "Tai and dealer streak must be non-negative" }
        require(config.baseScore >= 0 && config.pointPerTai >= 0) { "Base and points per tai must be non-negative" }
        val payers = discarderId?.let(::listOf) ?: playerIds.filter { it != winnerId }
        val payments = payers.associateWith { payer ->
            val dealerTai = if (winnerId == dealerId || payer == dealerId) 1L + 2L * dealerStreak else 0L
            val tai = handTai.toLong() + dealerTai
            require(config.pointPerTai == 0 || tai <= (Int.MAX_VALUE.toLong() - config.baseScore) / config.pointPerTai) {
                "Taiwan payment exceeds the supported point range"
            }
            (config.baseScore.toLong() + tai * config.pointPerTai).toInt()
        }.filterValues { it > 0 }
        val total = payments.values.sumOf { it.toLong() }
        require(total <= Int.MAX_VALUE) { "Taiwan total payment exceeds the supported point range" }
        return WinSettlementResult(total.toInt(), payments)
    }
}
