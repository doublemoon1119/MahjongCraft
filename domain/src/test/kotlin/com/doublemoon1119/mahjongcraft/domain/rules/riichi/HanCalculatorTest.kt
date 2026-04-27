package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 日本麻將番數計算之單元測試。
 *
 * 測試內容涵蓋：
 * - 一般役種加總
 * - 單倍役滿
 * - 雙倍役滿
 * - 複合役滿（役滿 + 雙倍役滿）
 * - 役滿優先原則
 *
 * @see HanCalculator
 */
class HanCalculatorTest {

    /**
     * 測試一般役種加總：立直 1 翻 + 平和 1 翻 = 2 翻。
     */
    @Test
    fun `test regular yaku sum`() {
        val results = listOf(
            YakuResult.han(YakuType.Riichi, 1),
            YakuResult.han(YakuType.Pinfu, 1)
        )
        val result = HanCalculator.calculateTotalHan(results)
        assertEquals(2, result)
    }

    /**
     * 測試單倍役滿：回傳 -1。
     */
    @Test
    fun `test single yakuman returns -1`() {
        val results = listOf(
            YakuResult.yakuman(YakuType.KokushiMusou)
        )
        val result = HanCalculator.calculateTotalHan(results)
        assertEquals(-1, result)
    }

    /**
     * 測試雙倍役滿：回傳 -2。
     */
    @Test
    fun `test double yakuman returns -2`() {
        val results = listOf(
            YakuResult.doubleYakuman(YakuType.Daisuushii)
        )
        val result = HanCalculator.calculateTotalHan(results)
        assertEquals(-2, result)
    }

    /**
     * 測試複合役滿：一般役滿 + 雙倍役滿 = -3（三倍役滿）。
     */
    @Test
    fun `test combined yakuman returns -3`() {
        val results = listOf(
            YakuResult.yakuman(YakuType.KokushiMusou),
            YakuResult.doubleYakuman(YakuType.Daisuushii)
        )
        val result = HanCalculator.calculateTotalHan(results)
        assertEquals(-3, result)
    }

    /**
     * 測試役滿優先原則：役滿 + 一般役（如立直）= -1。
     *
     * 當同時存在役滿與一般役時，應僅計算役滿倍數，忽略一般翻數。
     */
    @Test
    fun `test yakuman takes priority over regular yaku`() {
        val results = listOf(
            YakuResult.yakuman(YakuType.KokushiMusou),
            YakuResult.han(YakuType.Riichi, 1)
        )
        val result = HanCalculator.calculateTotalHan(results)
        assertEquals(-1, result)
    }

    /**
     * 測試多個一般役種加總。
     */
    @Test
    fun `test multiple regular yaku sum`() {
        val results = listOf(
            YakuResult.han(YakuType.Riichi, 1),
            YakuResult.han(YakuType.Pinfu, 1),
            YakuResult.han(YakuType.Dora, 2)
        )
        val result = HanCalculator.calculateTotalHan(results)
        assertEquals(4, result)
    }

    /**
     * 測試 [HanCalculator.calculateNonYakumanHan]：仅有非役满时返回正整数。
     */
    @Test
    fun `test calculate non yakuman han`() {
        val results = listOf(
            YakuResult.han(YakuType.Riichi, 1),
            YakuResult.han(YakuType.Pinfu, 1)
        )
        val result = HanCalculator.calculateNonYakumanHan(results)
        assertEquals(2, result)
    }

    /**
     * 測試 [HanCalculator.calculateNonYakumanHan]：包含役满时只计算非役满。
     */
    @Test
    fun `test calculate non yakuman han ignores yakuman`() {
        val results = listOf(
            YakuResult.yakuman(YakuType.KokushiMusou),
            YakuResult.han(YakuType.Riichi, 1),
            YakuResult.han(YakuType.Pinfu, 1)
        )
        val result = HanCalculator.calculateNonYakumanHan(results)
        assertEquals(2, result)
    }

    /**
     * 測試 [HanCalculator.calculateYakumanMultiplier]：單一般役滿 = 1。
     */
    @Test
    fun `test calculate yakuman multiplier single`() {
        val results = listOf(
            YakuResult.yakuman(YakuType.KokushiMusou)
        )
        val result = HanCalculator.calculateYakumanMultiplier(results)
        assertEquals(1, result)
    }

    /**
     * 測試 [HanCalculator.calculateYakumanMultiplier]：單雙倍役滿 = 2。
     */
    @Test
    fun `test calculate yakuman multiplier double`() {
        val results = listOf(
            YakuResult.doubleYakuman(YakuType.Daisuushii)
        )
        val result = HanCalculator.calculateYakumanMultiplier(results)
        assertEquals(2, result)
    }

    /**
     * 測試 [HanCalculator.calculateYakumanMultiplier]：一般役滿 + 雙倍役滿 = 3。
     */
    @Test
    fun `test calculate yakuman multiplier combined`() {
        val results = listOf(
            YakuResult.yakuman(YakuType.KokushiMusou),
            YakuResult.doubleYakuman(YakuType.Daisuushii)
        )
        val result = HanCalculator.calculateYakumanMultiplier(results)
        assertEquals(3, result)
    }

    /**
     * 測試多個雙倍役滿 = 4。
     */
    @Test
    fun `test calculate yakuman multiplier multiple double`() {
        val results = listOf(
            YakuResult.doubleYakuman(YakuType.Daisuushii),
            YakuResult.doubleYakuman(YakuType.ChurenPoto)
        )
        val result = HanCalculator.calculateYakumanMultiplier(results)
        assertEquals(4, result)
    }

    /**
     * 測試多個一般役滿 = 2。
     */
    @Test
    fun `test calculate yakuman multiplier multiple normal`() {
        val results = listOf(
            YakuResult.yakuman(YakuType.KokushiMusou),
            YakuResult.yakuman(YakuType.Tsuuiisou)
        )
        val result = HanCalculator.calculateYakumanMultiplier(results)
        assertEquals(2, result)
    }

    /**
     * 測試空列表回傳 0。
     */
    @Test
    fun `test empty results returns 0`() {
        val results = emptyList<YakuResult>()
        val result = HanCalculator.calculateTotalHan(results)
        assertEquals(0, result)
    }
}