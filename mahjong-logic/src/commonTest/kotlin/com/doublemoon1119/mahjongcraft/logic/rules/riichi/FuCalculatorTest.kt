package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.CompletionType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Fuuro
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Janto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
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
        seatWind: Wind = Wind.EAST,
    ): RiichiHandValueContext = FakeRiichiHandValueContextFactory.create(
        hand = FakeHandFactory.create(),
        winningTile = Tile.Numeric(Tile.Suit.Character, 1),
        isTsumo = isTsumo,
        isMenzen = isMenzen,
        roundWind = roundWind,
        seatWind = seatWind,
    )

    private fun createStandardStructure(
        pair: Janto,
        completionType: CompletionType = CompletionType.Ryanmen,
    ): HandStructure.Standard = HandStructure.Standard(
        mentsus = emptyList(),
        pair = pair,
        fuuro = emptyList(),
        completionType = completionType,
    )

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
                    Janto(Tile.Honor.East),
                ),
            ),
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
            completionType = CompletionType.Ryanmen,
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
            completionType = CompletionType.Ryanmen,
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
            completionType = CompletionType.Tanki, // 單騎 +2
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
            completionType = CompletionType.Kanchan,
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
            completionType = CompletionType.Penchan,
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
            completionType = CompletionType.Tanki,
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
            completionType = CompletionType.Ryanmen,
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
            completionType = CompletionType.Ryanmen,
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
            completionType = CompletionType.Ryanmen,
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
            completionType = CompletionType.Ryanmen,
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
            completionType = CompletionType.Ryanmen,
        )
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertTrue(result >= 20)
    }

    // 雀頭固定用三元牌（白），確保 calculatePinfu 不會因為役牌雀頭而誤判為平和；順帶避開一個
    // 探查到但不在本次範圍內的既有 bug——calculatePinfu 只檢查 structure.mentsus 是否全為順子，
    // 完全沒有檢查 structure.fuuro（副露），導致帶有槓子/碰的副露卻只把面子放進 fuuro（而非
    // mentsus）時，會被誤判為平和。用役牌雀頭讓 isYakuhai 提早擋下，不受這個既有 bug 影響。
    // 三元牌雀頭固定 +2 符，已經算進下方每個測試案例的預期總符數。
    private fun createStructureWithFuuro(mentsu: Mentsu): HandStructure.Standard = HandStructure.Standard(
        mentsus = emptyList(),
        pair = Janto(Tile.Honor.White),
        fuuro = listOf(Fuuro(mentsu, from = RelativeDirection.Left)),
        completionType = CompletionType.Ryanmen,
    )

    /**
     * 測試暗槓（中張牌）+16 符。
     * 符底 20 + 暗槓中張 16 + 三元牌雀頭 2 = 38 → 40
     */
    @Test
    fun `test ankan of simple tile adds 16 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStructureWithFuuro(Mentsu.Ankan(Tile.Numeric(Tile.Suit.Character, 3)))
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(40, result)
    }

    /**
     * 測試暗槓（么九牌）+32 符。
     * 符底 20 + 暗槓么九 32 + 三元牌雀頭 2 = 54 → 60
     */
    @Test
    fun `test ankan of terminal tile adds 32 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStructureWithFuuro(Mentsu.Ankan(Tile.Numeric(Tile.Suit.Character, 1)))
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(60, result)
    }

    /**
     * 測試暗槓（三元牌）+32 符（三元牌與么九牌同級距）。
     * 符底 20 + 暗槓三元 32 + 三元牌雀頭 2 = 54 → 60
     */
    @Test
    fun `test ankan of dragon tile adds 32 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStructureWithFuuro(Mentsu.Ankan(Tile.Honor.Red))
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(60, result)
    }

    /**
     * 測試明槓（中張牌）+8 符。
     * 符底 20 + 明槓中張 8 + 三元牌雀頭 2 = 30 → 30
     */
    @Test
    fun `test minkan of simple tile adds 8 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStructureWithFuuro(Mentsu.Minkan(Tile.Numeric(Tile.Suit.Character, 3)))
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試明槓（么九牌）+16 符。
     * 符底 20 + 明槓么九 16 + 三元牌雀頭 2 = 38 → 40
     */
    @Test
    fun `test minkan of terminal tile adds 16 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStructureWithFuuro(Mentsu.Minkan(Tile.Numeric(Tile.Suit.Character, 9)))
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(40, result)
    }

    /**
     * 測試加槓（中張牌）+8 符，與明槓同級距（[FuCalculator] 的 `when` 分支將 Minkan/Kakan 視為同一類）。
     * 符底 20 + 加槓中張 8 + 三元牌雀頭 2 = 30 → 30
     */
    @Test
    fun `test kakan of simple tile adds 8 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false)
        val handStructure = createStructureWithFuuro(Mentsu.Kakan(Tile.Numeric(Tile.Suit.Character, 3)))
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(30, result)
    }

    /**
     * 測試加槓（客風牌）+16 符。
     * 符底 20 + 加槓客風 16 + 三元牌雀頭 2 = 38 → 40
     */
    @Test
    fun `test kakan of guest wind tile adds 16 fu`() {
        val context = createContext(isMenzen = false, isTsumo = false, roundWind = Wind.EAST, seatWind = Wind.EAST)
        val handStructure = createStructureWithFuuro(Mentsu.Kakan(Tile.Honor.South))
        val result = FuCalculator.calculateTotalFu(context, handStructure)
        assertEquals(40, result)
    }
}
