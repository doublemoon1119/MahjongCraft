package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 日本麻將點數計算之單元測試。
 *
 * 測試內容涵蓋：
 * - 滿貫以下點數計算（1-4 翻）
 * - 等級點數計算（滿貫、跳滿、倍滿、數役滿）
 * - 百位數進位規則
 * - 役滿點數計算（單倍、雙倍）
 *
 * @see PointCalculator
 */
class PointCalculatorTest {

    /**
     * 測試 1 翻 30 符（子家）：1000 點。
     *
     * 計算公式：
     * - 基本點：30 * 2^(1+2) = 30 * 8 = 240
     * - 子家倍率：240 * 4 = 960
     * - 百位數進位：1000（960 進位至 1000）
     */
    @Test
    fun `test 1 han 30 fu for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 30, isDealer = false)
        assertEquals(1000, result)
    }

    /**
     * 測試 1 翻 30 符（莊家）：1500 點。
     *
     * 計算公式：
     * - 基本點：30 * 2^(1+2) = 30 * 8 = 240
     * - 莊家倍率：240 * 6 = 1440
     * - 百位數進位：1500（1440 進位至 1500）
     */
    @Test
    fun `test 1 han 30 fu for dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 30, isDealer = true)
        assertEquals(1500, result)
    }

    /**
     * 測試 3 翻 70 符（切上滿貫）。
     *
     * 計算公式：
     * - 基本點：70 * 2^(3+2) = 70 * 32 = 2240
     * - 滿貫封頂：2000
     * - 子家倍率：2000 * 4 = 8000
     * - 百位數進位：8000
     */
    @Test
    fun `test 3 han 70 fu rounds to mangan`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 3, fu = 70, isDealer = false)
        assertEquals(8000, result)
    }

    /**
     * 測試 3 翻 40 符（子家）：2000 點。
     *
     * 計算公式：
     * - 基本點：40 * 2^(3+2) = 40 * 32 = 1280
     * - 滿貫封頂：2000（因為 1280 < 2000，仍用 1280 計算）
     * - 子家倍率：1280 * 4 = 5120
     * - 百位數進位：5200
     */
    @Test
    fun `test 3 han 40 fu for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 3, fu = 40, isDealer = false)
        assertEquals(5200, result)
    }

    /**
     * 測試 4 翻 40 符（子家）：4000 點。
     *
     * 計算公式：
     * - 基本點：40 * 2^(4+2) = 40 * 64 = 2560
     * - 滿貫封頂：2000
     * - 子家倍率：2000 * 4 = 8000
     * - 百位數進位：8000
     */
    @Test
    fun `test 4 han 40 fu for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 4, fu = 40, isDealer = false)
        assertEquals(8000, result)
    }

    /**
     * 測試滿貫（5 翻）：子家 8000 點 / 莊家 12000 點。
     *
     * 等級點數固定為 2000 基本點。
     * - 子家：2000 * 4 = 8000
     * - 莊家：2000 * 6 = 12000
     */
    @Test
    fun `test mangan 5 han for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = false)
        assertEquals(8000, result)
    }

    @Test
    fun `test mangan 5 han for dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = true)
        assertEquals(12000, result)
    }

    /**
     * 測試跳滿（6 翻）：子家 12000 點 / 莊家 18000 點。
     *
     * 等級點數固定為 3000 基本點。
     * - 子家：3000 * 4 = 12000
     * - 莊家：3000 * 6 = 18000
     */
    @Test
    fun `test haneman 6 han for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 6, fu = 0, isDealer = false)
        assertEquals(12000, result)
    }

    @Test
    fun `test haneman 6 han for dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 6, fu = 0, isDealer = true)
        assertEquals(18000, result)
    }

    /**
     * 測試倍滿（8 翻）：子家 16000 點 / 莊家 24000 點。
     *
     * 等級點數固定為 4000 基本點。
     * - 子家：4000 * 4 = 16000
     * - 莊家：4000 * 6 = 24000
     */
    @Test
    fun `test baiman 8 han for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 8, fu = 0, isDealer = false)
        assertEquals(16000, result)
    }

    @Test
    fun `test baiman 8 han for dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 8, fu = 0, isDealer = true)
        assertEquals(24000, result)
    }

    /**
     * 測試三倍滿（11 翻）：子家 24000 點 / 莊家 36000 點。
     *
     * 等級點數固定為 6000 基本點。
     * - 子家：6000 * 4 = 24000
     * - 莊家：6000 * 6 = 36000
     */
    @Test
    fun `test sanbaiman 11 han for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 11, fu = 0, isDealer = false)
        assertEquals(24000, result)
    }

    @Test
    fun `test sanbaiman 11 han for dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 11, fu = 0, isDealer = true)
        assertEquals(36000, result)
    }

    /**
     * 測試數役滿（13 翻）：子家 32000 點 / 莊家 48000 點。
     *
     * 等級點數固定為 8000 基本點。
     * - 子家：8000 * 4 = 32000
     * - 莊家：8000 * 6 = 48000
     */
    @Test
    fun `test suuankou 13 han for non-dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 13, fu = 0, isDealer = false)
        assertEquals(32000, result)
    }

    @Test
    fun `test suuankou 13 han for dealer`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 13, fu = 0, isDealer = true)
        assertEquals(48000, result)
    }

    /**
     * 測試百位數進位：1920 點 → 2000 點（子家）。
     *
     * 驗證進位邏輯是否正確處理非百位數。
     */
    @Test
    fun `test ceil to hundred 1920 to 2000`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 2, fu = 30, isDealer = false)
        assertEquals(2000, result)
    }

    /**
     * 測試百位數進位：3200 點 → 3200 點（子家）。
     */
    @Test
    fun `test ceil to hundred 3200 no rounding needed`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 2, fu = 50, isDealer = false)
        assertEquals(3200, result)
    }

    /**
     * 測試百位數進位：640 點 → 700 點（子家）。
     */
    @Test
    fun `test ceil to hundred 640 to 700`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 20, isDealer = false)
        assertEquals(700, result)
    }

    /**
     * 測試百位數進位：5760 點 → 5800 點（莊家）。
     *
     * 3翻30符：30 * 2^5 = 960 → dealer: 960 * 6 = 5760 → 5800
     */
    @Test
    fun `test ceil to hundred 5760 to 5800`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 3, fu = 30, isDealer = true)
        assertEquals(5800, result)
    }

    /**
     * 測試百位數進位：恰好為百位整數时不進位。
     *
     * fu=50, 1翻時：50 * 8 = 400 → non-dealer: 400 * 4 = 1600
     */
    @Test
    fun `test exact hundred no rounding needed`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 50, isDealer = false)
        assertEquals(1600, result)
    }

    /**
     * 測試役滿點數（單倍役滿）：子家 32000 點 / 莊家 48000 點。
     */
    @Test
    fun `test yakuman 1x for non-dealer`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 1, isDealer = false)
        assertEquals(32000, result)
    }

    @Test
    fun `test yakuman 1x for dealer`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 1, isDealer = true)
        assertEquals(48000, result)
    }

    /**
     * 測試役滿點數（雙倍役滿）：子家 64000 點 / 莊家 96000 點。
     */
    @Test
    fun `test yakuman 2x for non-dealer`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 2, isDealer = false)
        assertEquals(64000, result)
    }

    @Test
    fun `test yakuman 2x for dealer`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 2, isDealer = true)
        assertEquals(96000, result)
    }

    /**
     * 測試役滿點數（累計役滿，三倍）：子家 96000 點 / 莊家 144000 點。
     */
    @Test
    fun `test yakuman 3x for non-dealer`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 3, isDealer = false)
        assertEquals(96000, result)
    }

    @Test
    fun `test yakuman 3x for dealer`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 3, isDealer = true)
        assertEquals(144000, result)
    }

    /**
     * 測試役滿點數（累計役滿役滿，四倍）：子家 128000 點。
     */
    @Test
    fun `test yakuman 4x for non-dealer`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 4, isDealer = false)
        assertEquals(128000, result)
    }
}