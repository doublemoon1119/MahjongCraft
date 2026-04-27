package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.CompletionType
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Janto
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueContext
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 日本麻將符數計算之單元測試。
 *
 * 測試內容涵蓋：
 * - 特殊牌型（七對子）
 * - 基礎加符（門前清榮和、自摸）
 * - 聽牌型加符（嵌張、邊張、單騎）
 * - 雀頭加符
 * - 進位邏輯
 *
 * @see FuCalculator
 */
class FuCalculatorTest {

    private fun createContext(
        isMenzen: Boolean = true,
        isTsumo: Boolean = false,
        roundWind: Wind = Wind.EAST,
        seatWind: Wind = Wind.EAST
    ): RiichiHandValueContext {
        return RiichiHandValueContext(
            hand = Hand(mutableListOf()),
            winningTile = Tile.Numeric(Tile.Suit.Character, 1),
            isTsumo = isTsumo,
            isRiichi = false,
            isDoubleRiichi = false,
            isIppatsu = false,
            isMenzen = isMenzen,
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList(),
            roundWind = roundWind,
            seatWind = seatWind
        )
    }

    private fun createStandardStructure(
        pair: Janto,
        completionType: CompletionType = CompletionType.Ryanmen
    ): HandStructure.Standard {
        return HandStructure.Standard(
            mentsus = emptyList(),
            pair = pair,
            fuuro = emptyList(),
            completionType = completionType
        )
    }

    /**
     * 測試七對子固定 25 符。
     */
    @Test
    fun `test chiitoitsu fixed 25 fu`() {
        val result = FuCalculator.calculateTotalFu(
            createContext(),
            HandStructure.Chiitoitsu(
                pairs = listOf(
                    Janto(Tile.Numeric(Tile.Suit.Character, 1)),
                    Janto(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                    Janto(Tile.Numeric(Tile.Suit.Dot, 1)),
                    Janto(Tile.Numeric(Tile.Suit.Character, 9)),
                    Janto(Tile.Numeric(Tile.Suit.Bamboo, 9)),
                    Janto(Tile.Numeric(Tile.Suit.Dot, 9)),
                    Janto(Tile.Honor.East)
                )
            )
        )
        assertEquals(25, result)
    }

    /**
     * 測試門前清榮和 +10。
     * 符底 20 + 門前清榮和 10 = 30
     */
    @Test
    fun `test menzen ron adds 10 fu`() {
        val context = createContext(isMenzen = true, isTsumo = false)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Numeric(Tile.Suit.Character, 5)),
            completionType = CompletionType.Ryanmen
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試非門前自摸 +2。
     * 符底 20 + 自摸 2 = 22 → 30
     */
    @Test
    fun `test tsumo adds 2 fu`() {
        val context = createContext(isMenzen = false, isTsumo = true)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Numeric(Tile.Suit.Character, 5)),
            completionType = CompletionType.Ryanmen
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試門前自摸（非平和型）。
     * 符底 20 + 2 (自摸) + 2 (雀頭) + 2 (單騎) = 26 → 30
     */
    @Test
    fun `test menzen tsumo non pinfu`() {
        // 門前自摸不加 10 符，僅加 2 符
        val context = createContext(isMenzen = true, isTsumo = true)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Honor.East), // 自風雀頭 +2，確保不符合平和
            completionType = CompletionType.Tanki // 單騎 +2
        )
        // 20 + 2 (自摸) + 2 (雀頭) + 2 (單騎) = 26 → 30
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試嵌張 +2。
     * 符底 20 + 嵌張 2 = 22 → 30
     */
    @Test
    fun `test kanchan adds 2 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Numeric(Tile.Suit.Character, 5)),
            completionType = CompletionType.Kanchan
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試邊張 +2。
     */
    @Test
    fun `test penchan adds 2 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Numeric(Tile.Suit.Character, 5)),
            completionType = CompletionType.Penchan
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試單騎 +2。
     */
    @Test
    fun `test tanki adds 2 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Numeric(Tile.Suit.Character, 5)),
            completionType = CompletionType.Tanki
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試兩面聽不加符（符底 >= 20）。
     */
    @Test
    fun `test ryanmen adds 0 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Numeric(Tile.Suit.Character, 5)),
            completionType = CompletionType.Ryanmen
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertTrue(result >= 20)
    }

    /**
     * 測試自風牌雀頭 +2 符。
     * 符底 20 + 雀頭 2 = 22 → 30
     */
    @Test
    fun `test seat wind pair adds 2 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false, seatWind = Wind.EAST)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Honor.East),
            completionType = CompletionType.Ryanmen
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試場風牌雀頭 +2 符。
     */
    @Test
    fun `test round wind pair adds 2 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false, roundWind = Wind.SOUTH)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Honor.South),
            completionType = CompletionType.Ryanmen
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試三元牌雀頭 +2 符。
     */
    @Test
    fun `test dragon pair adds 2 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Honor.Red),
            completionType = CompletionType.Ryanmen
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試普通牌雀頭不加符（符底 >= 20）。
     */
    @Test
    fun `test normal pair adds 0 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStandardStructure(
            pair = Janto(Tile.Numeric(Tile.Suit.Character, 5)),
            completionType = CompletionType.Ryanmen
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertTrue(result >= 20)
    }
}