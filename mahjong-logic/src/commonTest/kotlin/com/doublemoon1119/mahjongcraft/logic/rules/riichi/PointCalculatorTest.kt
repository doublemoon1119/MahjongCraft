package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 日本麻將點數計算之單元測試。
 *
 * 測試內容涵蓋：
 * - 榮和：滿貫以下點數計算（1-4 翻）、等級點數計算（滿貫、跳滿等）、百位數進位規則
 * - 自摸：莊家自摸（三家均攤）與閒家自摸（莊家/閒家分別支付）的點數拆分
 * - 役滿點數計算（單倍、雙倍，榮和與自摸）
 *
 * @see PointCalculator
 */
class PointCalculatorTest {

    /**
     * 測試 1 翻 30 符（子家）榮和：1000 點。
     *
     * 計算公式：
     * - 基本點：30 * 2^(1+2) = 30 * 8 = 240
     * - 子家倍率：240 * 4 = 960
     * - 百位數進位：1000（960 進位至 1000）
     */
    @Test
    fun `test 1 han 30 fu for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 30, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(1000), result)
    }

    /**
     * 測試 1 翻 30 符（莊家）榮和：1500 點。
     *
     * 計算公式：
     * - 基本點：30 * 2^(1+2) = 30 * 8 = 240
     * - 莊家倍率：240 * 6 = 1440
     * - 百位數進位：1500（1440 進位至 1500）
     */
    @Test
    fun `test 1 han 30 fu for dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 30, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(1500), result)
    }

    /**
     * 測試 3 翻 70 符榮和（切上滿貫）。
     *
     * 計算公式：
     * - 基本點：70 * 2^(3+2) = 70 * 32 = 2240
     * - 滿貫封頂：2000
     * - 子家倍率：2000 * 4 = 8000
     * - 百位數進位：8000
     */
    @Test
    fun `test 3 han 70 fu ron rounds to mangan`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 3, fu = 70, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(8000), result)
    }

    /**
     * 測試 3 翻 40 符（子家）榮和：2000 點。
     *
     * 計算公式：
     * - 基本點：40 * 2^(3+2) = 40 * 32 = 1280
     * - 滿貫封頂：2000（因為 1280 < 2000，仍用 1280 計算）
     * - 子家倍率：1280 * 4 = 5120
     * - 百位數進位：5200
     */
    @Test
    fun `test 3 han 40 fu for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 3, fu = 40, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(5200), result)
    }

    /**
     * 測試 4 翻 40 符（子家）榮和：4000 點。
     *
     * 計算公式：
     * - 基本點：40 * 2^(4+2) = 40 * 64 = 2560
     * - 滿貫封頂：2000
     * - 子家倍率：2000 * 4 = 8000
     * - 百位數進位：8000
     */
    @Test
    fun `test 4 han 40 fu for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 4, fu = 40, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(8000), result)
    }

    /**
     * 測試滿貫（5 翻）榮和：子家 8000 點。
     *
     * 等級點數固定為 2000 基本點。
     * - 子家：2000 * 4 = 8000
     */
    @Test
    fun `test mangan 5 han for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(8000), result)
    }

    /**
     * 測試滿貫（5 翻）榮和：莊家 12000 點。
     *
     * 等級點數固定為 2000 基本點。
     * - 莊家：2000 * 6 = 12000
     */
    @Test
    fun `test mangan 5 han for dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(12000), result)
    }

    /**
     * 測試跳滿（6 翻）榮和：子家 12000 點。
     *
     * 等級點數固定為 3000 基本點。
     * - 子家：3000 * 4 = 12000
     */
    @Test
    fun `test haneman 6 han for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 6, fu = 0, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(12000), result)
    }

    /**
     * 測試跳滿（6 翻）榮和：莊家 18000 點。
     *
     * 等級點數固定為 3000 基本點。
     * - 莊家：3000 * 6 = 18000
     */
    @Test
    fun `test haneman 6 han for dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 6, fu = 0, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(18000), result)
    }

    /**
     * 測試倍滿（8 翻）榮和：子家 16000 點。
     *
     * 等級點數固定為 4000 基本點。
     * - 子家：4000 * 4 = 16000
     */
    @Test
    fun `test baiman 8 han for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 8, fu = 0, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(16000), result)
    }

    /**
     * 測試倍滿（8 翻）榮和：莊家 24000 點。
     *
     * 等級點數固定為 4000 基本點。
     * - 莊家：4000 * 6 = 24000
     */
    @Test
    fun `test baiman 8 han for dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 8, fu = 0, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(24000), result)
    }

    /**
     * 測試三倍滿（11 翻）榮和：子家 24000 點。
     *
     * 等級點數固定為 6000 基本點。
     * - 子家：6000 * 4 = 24000
     */
    @Test
    fun `test sanbaiman 11 han for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 11, fu = 0, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(24000), result)
    }

    /**
     * 測試三倍滿（11 翻）榮和：莊家 36000 點。
     *
     * 等級點數固定為 6000 基本點。
     * - 莊家：6000 * 6 = 36000
     */
    @Test
    fun `test sanbaiman 11 han for dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 11, fu = 0, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(36000), result)
    }

    /**
     * 測試數役滿（13 翻）榮和：子家 32000 點 。
     *
     * 等級點數固定為 8000 基本點。
     * - 子家：8000 * 4 = 32000
     */
    @Test
    fun `test suuankou 13 han for non-dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 13, fu = 0, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(32000), result)
    }

    /**
     * 測試數役滿（13 翻）榮和：莊家 48000 點。
     *
     * 等級點數固定為 8000 基本點。
     * - 莊家：8000 * 6 = 48000
     */
    @Test
    fun `test suuankou 13 han for dealer ron`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 13, fu = 0, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(48000), result)
    }

    /**
     * 測試百位數進位：1920 點 → 2000 點（子家榮和）。
     *
     * 驗證進位邏輯是否正確處理非百位數。
     */
    @Test
    fun `test ceil to hundred 1920 to 2000`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 2, fu = 30, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(2000), result)
    }

    /**
     * 測試百位數進位：3200 點 → 3200 點（子家榮和）。
     */
    @Test
    fun `test ceil to hundred 3200 no rounding needed`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 2, fu = 50, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(3200), result)
    }

    /**
     * 測試百位數進位：640 點 → 700 點（子家榮和）。
     */
    @Test
    fun `test ceil to hundred 640 to 700`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 20, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(700), result)
    }

    /**
     * 測試百位數進位：5760 點 → 5800 點（莊家榮和）。
     *
     * 3翻30符：30 * 2^5 = 960 → dealer: 960 * 6 = 5760 → 5800
     */
    @Test
    fun `test ceil to hundred 5760 to 5800`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 3, fu = 30, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(5800), result)
    }

    /**
     * 測試百位數進位：恰好為百位整數时不進位。
     *
     * fu=50, 1翻時：50 * 8 = 400 → non-dealer: 400 * 4 = 1600
     */
    @Test
    fun `test exact hundred no rounding needed`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 50, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(1600), result)
    }

    /**
     * 測試閒家自摸：1 翻 20 符（例如平和自摸）。
     *
     * 這是最常見的自摸型態之一，也是「先加總乘倍率再進位」與「每筆分開進位」
     * 兩種算法會產生不同結果的典型案例：
     * - 基本點：20 * 2^(1+2) = 160
     * - 莊家支付：ceil100(160 * 2) = ceil100(320) = 400
     * - 其餘閒家支付：ceil100(160 * 1) = ceil100(160) = 200（每人）
     * - 總點數：400 + 200 + 200 = 800（麻將點數表俗稱「200/400」）
     *
     * 若誤用榮和公式（160 * 4 = 640 進位至 700），總點數會少算 100 點。
     */
    @Test
    fun `test 1 han 20 fu non-dealer tsumo splits payments correctly`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 20, isDealer = false, isTsumo = true)
        assertEquals(RiichiPointResult.NonDealerTsumo(dealerPayment = 400, otherNonDealerPayment = 200), result)
        assertEquals(800, result.total)
    }

    /**
     * 測試閒家自摸：1 翻 40 符。
     *
     * - 基本點：40 * 2^(1+2) = 320
     * - 莊家支付：ceil100(320 * 2) = ceil100(640) = 700
     * - 其餘閒家支付：ceil100(320 * 1) = ceil100(320) = 400（每人）
     * - 總點數：700 + 400 + 400 = 1500（麻將點數表俗稱「400/700」）
     */
    @Test
    fun `test 1 han 40 fu non-dealer tsumo splits payments correctly`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 40, isDealer = false, isTsumo = true)
        assertEquals(RiichiPointResult.NonDealerTsumo(dealerPayment = 700, otherNonDealerPayment = 400), result)
        assertEquals(1500, result.total)
    }

    /**
     * 測試莊家自摸：1 翻 20 符。
     *
     * - 基本點：20 * 2^(1+2) = 160
     * - 每位閒家支付：ceil100(160 * 2) = ceil100(320) = 400
     * - 總點數：400 * 3 = 1200（麻將點數表俗稱「400 all」）
     */
    @Test
    fun `test 1 han 20 fu dealer tsumo splits payments correctly`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 1, fu = 20, isDealer = true, isTsumo = true)
        assertEquals(RiichiPointResult.DealerTsumo(paymentPerNonDealer = 400), result)
        assertEquals(1200, result.total)
    }

    /**
     * 測試滿貫（5 翻）閒家自摸：俗稱「2000/4000」，總點數 8000。
     */
    @Test
    fun `test mangan non-dealer tsumo`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = false, isTsumo = true)
        assertEquals(RiichiPointResult.NonDealerTsumo(dealerPayment = 4000, otherNonDealerPayment = 2000), result)
        assertEquals(8000, result.total)
    }

    /**
     * 測試滿貫（5 翻）莊家自摸：俗稱「4000 all」，總點數 12000。
     */
    @Test
    fun `test mangan dealer tsumo`() {
        val result = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = true, isTsumo = true)
        assertEquals(RiichiPointResult.DealerTsumo(paymentPerNonDealer = 4000), result)
        assertEquals(12000, result.total)
    }

    /**
     * 測試役滿點數（單倍役滿）榮和：子家 32000 點 。
     */
    @Test
    fun `test yakuman 1x for non-dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 1, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(32000), result)
    }

    /**
     * 測試役滿點數（單倍役滿）榮和：莊家 48000 點。
     */
    @Test
    fun `test yakuman 1x for dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 1, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(48000), result)
    }

    /**
     * 測試役滿點數（雙倍役滿）榮和：子家 64000 點。
     */
    @Test
    fun `test yakuman 2x for non-dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 2, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(64000), result)
    }

    /**
     * 測試役滿點數（雙倍役滿）榮和：莊家 96000 點。
     */
    @Test
    fun `test yakuman 2x for dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 2, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(96000), result)
    }

    /**
     * 測試役滿點數（累計役滿，三倍）榮和：子家 96000 點。
     */
    @Test
    fun `test yakuman 3x for non-dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 3, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(96000), result)
    }

    /**
     * 測試役滿點數（累計役滿，三倍）榮和：莊家 144000 點。
     */
    @Test
    fun `test yakuman 3x for dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 3, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(144000), result)
    }

    /**
     * 測試役滿點數（累計役滿役滿，四倍）榮和：子家 128000 點。
     */
    @Test
    fun `test yakuman 4x for non-dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 4, isDealer = false, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(128000), result)
    }

    /**
     * 測試役滿點數（累計役滿役滿，四倍）榮和：莊家 192000 點。
     */
    @Test
    fun `test yakuman 4x for dealer ron`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 4, isDealer = true, isTsumo = false)
        assertEquals(RiichiPointResult.Ron(192000), result)
    }

    /**
     * 測試役滿點數（單倍役滿）閒家自摸：俗稱「8000/16000」，總點數 32000。
     */
    @Test
    fun `test yakuman 1x non-dealer tsumo`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 1, isDealer = false, isTsumo = true)
        assertEquals(RiichiPointResult.NonDealerTsumo(dealerPayment = 16000, otherNonDealerPayment = 8000), result)
        assertEquals(32000, result.total)
    }

    /**
     * 測試役滿點數（單倍役滿）莊家自摸：俗稱「16000 all」，總點數 48000。
     */
    @Test
    fun `test yakuman 1x dealer tsumo`() {
        val result = PointCalculator.calculateYakumanPoint(yakumanMultiplier = 1, isDealer = true, isTsumo = true)
        assertEquals(RiichiPointResult.DealerTsumo(paymentPerNonDealer = 16000), result)
        assertEquals(48000, result.total)
    }

    /**
     * 測試包牌自摸（贏家為閒家）：包牌責任者一人支付全額 32000 點，
     * 取代原本應由三家分攤的「8000/16000」。
     */
    @Test
    fun `test pao tsumo for non-dealer winner`() {
        val result = PointCalculator.calculateYakumanPoint(
            yakumanMultiplier = 1,
            isDealer = false,
            isTsumo = true,
            isPao = true
        )
        assertEquals(RiichiPointResult.PaoTsumo(32000), result)
        assertEquals(32000, result.total)
    }

    /**
     * 測試包牌自摸（贏家為莊家）：包牌責任者一人支付全額 48000 點，
     * 取代原本應由三家分攤的「16000 all」。
     */
    @Test
    fun `test pao tsumo for dealer winner`() {
        val result = PointCalculator.calculateYakumanPoint(
            yakumanMultiplier = 1,
            isDealer = true,
            isTsumo = true,
            isPao = true
        )
        assertEquals(RiichiPointResult.PaoTsumo(48000), result)
        assertEquals(48000, result.total)
    }

    /**
     * 測試包牌榮和（贏家為閒家）：包牌責任者與實際放銃者平分總點數 32000，各付 16000。
     */
    @Test
    fun `test pao ron for non-dealer winner`() {
        val result = PointCalculator.calculateYakumanPoint(
            yakumanMultiplier = 1,
            isDealer = false,
            isTsumo = false,
            isPao = true
        )
        assertEquals(RiichiPointResult.PaoRon(16000), result)
        assertEquals(32000, result.total)
    }

    /**
     * 測試包牌榮和（贏家為莊家）：包牌責任者與實際放銃者平分總點數 48000，各付 24000。
     */
    @Test
    fun `test pao ron for dealer winner`() {
        val result = PointCalculator.calculateYakumanPoint(
            yakumanMultiplier = 1,
            isDealer = true,
            isTsumo = false,
            isPao = true
        )
        assertEquals(RiichiPointResult.PaoRon(24000), result)
        assertEquals(48000, result.total)
    }

    /**
     * 測試累計役滿（雙倍役滿）情境下的包牌榮和，確認平分邏輯在倍數疊加時依然正確。
     */
    @Test
    fun `test pao ron with double yakuman multiplier`() {
        val result = PointCalculator.calculateYakumanPoint(
            yakumanMultiplier = 2,
            isDealer = false,
            isTsumo = false,
            isPao = true
        )
        assertEquals(RiichiPointResult.PaoRon(32000), result)
        assertEquals(64000, result.total)
    }
}
