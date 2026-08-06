package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult

/**
 * 日本麻將番數計算機。
 *
 * 負責計算手牌的總番數，包括一般役的番數加總與役滿倍數的計算。
 *
 * 番數表示規則：
 * - 正整數：一般役的番數總和
 * - -1 = 役滿 (1倍)
 * - -2 = 雙倍役滿 (2倍)
 * - -3 = 三倍役滿 (3倍)
 * ...
 */
object HanCalculator {

    /**
     * 計算役滿倍數（役滿結果中的最大倍數）。
     *
     * 當有多個役滿時，取最大倍數作為役滿基數。
     *
     * @param yakumanResults 役滿結果列表
     * @return 役滿倍數（1, 2, 3...）
     */
    fun calculateYakumanMultiplier(yakumanResults: List<YakuResult>): Int {
        val normalYakumanCount = yakumanResults.count { it.isYakuman && !it.isDoubleYakuman }
        val doubleYakumanCount = yakumanResults.count { it.isDoubleYakuman }
        return if (doubleYakumanCount > 0) {
            // 雙倍役滿為最高優先
            doubleYakumanCount * 2 + normalYakumanCount
        } else {
            normalYakumanCount
        }
    }

    /**
     * 計算非役滿結果的總番數。
     *
     * @param results 非役滿的 YakuResult 列表
     * @return 總番數（正整數）
     */
    fun calculateNonYakumanHan(results: List<YakuResult>): Int = results.filter { !it.isYakuman }.sumOf { it.han }

    /**
     * 計算總番數。
     *
     * 若存在役滿，則回傳負值（役滿倍數 * -1）。
     * 若無役滿，則回傳一般役的番數總和。
     *
     * @param results YakuResult 列表
     * @return 總番數（役滿時為負值，一般役時為正整數）
     */
    fun calculateTotalHan(results: List<YakuResult>): Int {
        val yakumanResults = results.filter { it.isYakuman }
        val nonYakumanResults = results.filter { !it.isYakuman }

        return if (yakumanResults.isNotEmpty()) {
            // 有役滿時，回傳負值
            -calculateYakumanMultiplier(yakumanResults)
        } else {
            // 無役滿時，回傳正整數
            calculateNonYakumanHan(nonYakumanResults)
        }
    }
}
