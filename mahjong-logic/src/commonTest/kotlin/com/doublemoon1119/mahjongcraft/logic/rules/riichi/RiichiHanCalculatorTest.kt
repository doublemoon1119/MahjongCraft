package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
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
 * @see RiichiHanCalculator
 */
class RiichiHanCalculatorTest {

    /**
     * 測試一般役種加總：立直 1 翻 + 平和 1 翻 = 2 翻。
     */
    @Test
    fun `test regular yaku sum`() {
        val results = listOf(
            RiichiYakuResult.han(RiichiYakuType.Riichi, 1),
            RiichiYakuResult.han(RiichiYakuType.Pinfu, 1),
        )
        val result = RiichiHanCalculator.calculateTotalHan(results)
        assertEquals(2, result)
    }

    /**
     * 測試單倍役滿：回傳 -1。
     */
    @Test
    fun `test single yakuman returns -1`() {
        val results = listOf(
            element = RiichiYakuResult.yakuman(RiichiYakuType.KokushiMusou),
        )
        val result = RiichiHanCalculator.calculateTotalHan(results)
        assertEquals(-1, result)
    }

    /**
     * 測試雙倍役滿：回傳 -2。
     */
    @Test
    fun `test double yakuman returns -2`() {
        val results = listOf(
            element = RiichiYakuResult.doubleYakuman(RiichiYakuType.Daisuushii),
        )
        val result = RiichiHanCalculator.calculateTotalHan(results)
        assertEquals(-2, result)
    }

    /**
     * 測試複合役滿：一般役滿 + 雙倍役滿 = -3（三倍役滿）。
     */
    @Test
    fun `test combined yakuman returns -3`() {
        val results = listOf(
            RiichiYakuResult.yakuman(RiichiYakuType.KokushiMusou),
            RiichiYakuResult.doubleYakuman(RiichiYakuType.Daisuushii),
        )
        val result = RiichiHanCalculator.calculateTotalHan(results)
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
            RiichiYakuResult.yakuman(RiichiYakuType.KokushiMusou),
            RiichiYakuResult.han(RiichiYakuType.Riichi, 1),
        )
        val result = RiichiHanCalculator.calculateTotalHan(results)
        assertEquals(-1, result)
    }

    /**
     * 測試多個一般役種加總。
     */
    @Test
    fun `test multiple regular yaku sum`() {
        val results = listOf(
            RiichiYakuResult.han(RiichiYakuType.Riichi, 1),
            RiichiYakuResult.han(RiichiYakuType.Pinfu, 1),
            RiichiYakuResult.han(RiichiYakuType.Dora, 2),
        )
        val result = RiichiHanCalculator.calculateTotalHan(results)
        assertEquals(4, result)
    }

    /**
     * 測試 [RiichiHanCalculator.calculateNonYakumanHan]：仅有非役满时返回正整数。
     */
    @Test
    fun `test calculate non yakuman han`() {
        val results = listOf(
            RiichiYakuResult.han(RiichiYakuType.Riichi, 1),
            RiichiYakuResult.han(RiichiYakuType.Pinfu, 1),
        )
        val result = RiichiHanCalculator.calculateNonYakumanHan(results)
        assertEquals(2, result)
    }

    /**
     * 測試 [RiichiHanCalculator.calculateNonYakumanHan]：包含役满时只计算非役满。
     */
    @Test
    fun `test calculate non yakuman han ignores yakuman`() {
        val results = listOf(
            RiichiYakuResult.yakuman(RiichiYakuType.KokushiMusou),
            RiichiYakuResult.han(RiichiYakuType.Riichi, 1),
            RiichiYakuResult.han(RiichiYakuType.Pinfu, 1),
        )
        val result = RiichiHanCalculator.calculateNonYakumanHan(results)
        assertEquals(2, result)
    }

    /**
     * 測試 [RiichiHanCalculator.calculateYakumanMultiplier]：單一般役滿 = 1。
     */
    @Test
    fun `test calculate yakuman multiplier single`() {
        val results = listOf(
            element = RiichiYakuResult.yakuman(RiichiYakuType.KokushiMusou),
        )
        val result = RiichiHanCalculator.calculateYakumanMultiplier(results)
        assertEquals(1, result)
    }

    /**
     * 測試 [RiichiHanCalculator.calculateYakumanMultiplier]：單雙倍役滿 = 2。
     */
    @Test
    fun `test calculate yakuman multiplier double`() {
        val results = listOf(
            element = RiichiYakuResult.doubleYakuman(RiichiYakuType.Daisuushii),
        )
        val result = RiichiHanCalculator.calculateYakumanMultiplier(results)
        assertEquals(2, result)
    }

    /**
     * 測試 [RiichiHanCalculator.calculateYakumanMultiplier]：一般役滿 + 雙倍役滿 = 3。
     */
    @Test
    fun `test calculate yakuman multiplier combined`() {
        val results = listOf(
            RiichiYakuResult.yakuman(RiichiYakuType.KokushiMusou),
            RiichiYakuResult.doubleYakuman(RiichiYakuType.Daisuushii),
        )
        val result = RiichiHanCalculator.calculateYakumanMultiplier(results)
        assertEquals(3, result)
    }

    /**
     * 測試多個雙倍役滿 = 4。
     */
    @Test
    fun `test calculate yakuman multiplier multiple double`() {
        val results = listOf(
            RiichiYakuResult.doubleYakuman(RiichiYakuType.Daisuushii),
            RiichiYakuResult.doubleYakuman(RiichiYakuType.ChurenPoto),
        )
        val result = RiichiHanCalculator.calculateYakumanMultiplier(results)
        assertEquals(4, result)
    }

    /**
     * 測試多個一般役滿 = 2。
     */
    @Test
    fun `test calculate yakuman multiplier multiple normal`() {
        val results = listOf(
            RiichiYakuResult.yakuman(RiichiYakuType.KokushiMusou),
            RiichiYakuResult.yakuman(RiichiYakuType.Tsuuiisou),
        )
        val result = RiichiHanCalculator.calculateYakumanMultiplier(results)
        assertEquals(2, result)
    }

    /**
     * 測試空列表回傳 0。
     */
    @Test
    fun `test empty results returns 0`() {
        val results = emptyList<RiichiYakuResult>()
        val result = RiichiHanCalculator.calculateTotalHan(results)
        assertEquals(0, result)
    }
}
