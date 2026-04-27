package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import kotlin.math.pow

/**
 * 日本麻將點數計算機。
 *
 * 負責計算手牌的最終點數，包括役滿點數與非役滿點數的計算。
 */
object PointCalculator {

    /**
     * 計算役滿最終點數。
     *
     * @param yakumanMultiplier 役滿倍數 (如 1, 2...)
     * @param isDealer 是否為莊家
     * @return 最終總點數
     */
    fun calculateYakumanPoint(yakumanMultiplier: Int, isDealer: Boolean): Int {
        val basePoint = if (isDealer) 48000 else 32000
        return basePoint * yakumanMultiplier
    }

    /**
     * 計算非役滿手牌的最終點數。
     *
     * 根據翻數與符數，判定點數等級（滿貫、跳滿等）或使用指數公式計算。
     * 最終點數會根據是否為莊家進行加成，並確保符合百位數進位規則。
     *
     * @param han 總翻數。
     * @param fu 總符數。
     * @param isDealer 是否為莊家。
     * @return 最終獲得的總點數（榮和總點數）。
     */
    fun calculateNonYakumanPoint(han: Int, fu: Int, isDealer: Boolean): Int {
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

        // 3. 根據身分倍率計算最終榮和點數
        // 莊家為基本點的 6 倍，子家為 4 倍
        val multiplier = if (isDealer) 6 else 4
        val rawTotal = basicPoint * multiplier

        // 4. 向上進位至百位數
        // 例如：3840 點進位至 3900 點
        return ceilToHundred(rawTotal)
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