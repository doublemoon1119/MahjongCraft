package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import kotlin.uuid.Uuid

/** 台灣麻將逐筆計算底台、莊家與連莊付款，以及包牌時的付款分配，不修改玩家分數。 */
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

    /**
     * 把一般付款改由包牌者負擔，贏家總收入不變。
     *
     * - 自摸：包牌者付三家的份，每一份照原本付款者計算的莊家台不變。
     * - 包牌者本人放槍：照常由他付。
     * - 別人放槍：包牌者與放槍者平分；除不盡時包牌者多付一點。
     *
     * 包牌者一律標示包牌付款原因。
     *
     * @param settlement [calculate] 算出的一般付款。
     * @param paoPlayerId 包牌者。
     * @param discarderId 放槍者；自摸為 null。
     * @return 改由包牌者負擔後的付款。
     */
    fun withPaoLiability(
        settlement: WinSettlementResult,
        paoPlayerId: Uuid,
        discarderId: Uuid?,
    ): WinSettlementResult {
        val payments = when (discarderId) {
            null -> mapOf(paoPlayerId to settlement.totalGained)
            paoPlayerId -> settlement.paymentsByPlayerId
            else -> {
                val amount = settlement.paymentsByPlayerId.getValue(discarderId)
                mapOf(paoPlayerId to amount - amount / 2, discarderId to amount / 2).filterValues { it > 0 }
            }
        }
        return WinSettlementResult(
            totalGained = settlement.totalGained,
            paymentsByPlayerId = payments,
            paymentReasonIdsByPlayerId = mapOf(paoPlayerId to BuiltInPaymentReasonIds.PAO),
        )
    }
}
