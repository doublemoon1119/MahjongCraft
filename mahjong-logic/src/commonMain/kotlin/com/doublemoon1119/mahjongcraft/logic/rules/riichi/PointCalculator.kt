package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import kotlin.math.pow

/**
 * 日本麻將點數計算機。
 *
 * 負責計算手牌的最終點數，包括役滿點數與非役滿點數的計算。
 */
object PointCalculator {

    /**
     * 計算役滿的點數結算結果。
     *
     * @param yakumanMultiplier 役滿倍數 (如 1, 2...)
     * @param isDealer 贏家是否為莊家。
     * @param isTsumo 是否為自摸。
     * @return 依榮和/自摸區分的 [RiichiPointResult]。
     */
    fun calculateYakumanPoint(yakumanMultiplier: Int, isDealer: Boolean, isTsumo: Boolean): RiichiPointResult {
        // 役滿的基本點固定為 8000，與非役滿點數表中的「數役滿」級距相同
        val basicPoint = 8000 * yakumanMultiplier
        return buildPointResult(basicPoint, isDealer, isTsumo)
    }

    /**
     * 計算非役滿手牌的點數結算結果。
     *
     * 根據翻數與符數，判定點數等級（滿貫、跳滿等）或使用指數公式計算基本點，
     * 再依「贏家身分」與「和牌方式（榮和/自摸）」換算為實際結算金額。
     *
     * @param han 總翻數。
     * @param fu 總符數。
     * @param isDealer 贏家是否為莊家。
     * @param isTsumo 是否為自摸。
     * @return 依榮和/自摸區分的 [RiichiPointResult]。
     */
    fun calculateNonYakumanPoint(han: Int, fu: Int, isDealer: Boolean, isTsumo: Boolean): RiichiPointResult {
        // 1. 判定固定點數等級 (滿貫以上)
        val fixedBasicPoint = when {
            han >= 13 -> 8000   // 數役滿
            han >= 11 -> 6000   // 三倍滿
            han >= 8 -> 4000    // 倍滿
            han >= 6 -> 3000    // 跳滿
            han == 5 -> 2000    // 滿貫
            else -> null        // 滿貫以下，需要計算
        }

        // 2. 計算基本點 (Basic Point)
        val basicPoint = if (fixedBasicPoint != null) {
            fixedBasicPoint
        } else {
            // 公式：符數 * 2^(翻數 + 2)
            val calculatedBP = fu * 2.0.pow(han + 2).toInt()
            // 滿貫封頂：基本點最高為 2000
            calculatedBP.coerceAtMost(2000)
        }

        return buildPointResult(basicPoint, isDealer, isTsumo)
    }

    /**
     * 依基本點、贏家身分與和牌方式，建立對應的 [RiichiPointResult]。
     *
     * 榮和時僅有單一放銃者支付全額（基本點 * 身分倍率，一次性進位至百位）。
     * 自摸時由其餘玩家分別支付，每一筆支付各自獨立進位至百位——
     * 這與榮和「先乘倍率、最後才進位」不同，兩者的加總不必然相等，故須分開處理。
     *
     * @param basicPoint 基本點。
     * @param isDealer 贏家是否為莊家。
     * @param isTsumo 是否為自摸。
     */
    private fun buildPointResult(basicPoint: Int, isDealer: Boolean, isTsumo: Boolean): RiichiPointResult {
        if (!isTsumo) {
            // 榮和：莊家為基本點的 6 倍，子家為 4 倍，一次性進位
            val multiplier = if (isDealer) 6 else 4
            return RiichiPointResult.Ron(ceilToHundred(basicPoint * multiplier))
        }

        return if (isDealer) {
            // 莊家自摸：三位閒家各支付基本點的 2 倍
            val paymentPerNonDealer = ceilToHundred(basicPoint * 2)
            RiichiPointResult.DealerTsumo(paymentPerNonDealer)
        } else {
            // 閒家自摸：莊家支付基本點的 2 倍，另外兩位閒家各支付基本點的 1 倍
            val dealerPayment = ceilToHundred(basicPoint * 2)
            val otherNonDealerPayment = ceilToHundred(basicPoint)
            RiichiPointResult.NonDealerTsumo(dealerPayment, otherNonDealerPayment)
        }
    }

    /**
     * 將數值向上進位至百位數。
     *
     * @param value 原始點數。
     * @return 進位後的點數。
     */
    private fun ceilToHundred(value: Int): Int {
        return if (value % 100 == 0) value else (value / 100 + 1) * 100
    }
}