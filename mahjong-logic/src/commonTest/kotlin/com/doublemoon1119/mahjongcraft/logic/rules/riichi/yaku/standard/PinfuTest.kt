package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.CompletionType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Fuuro
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Janto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 平和役種測試。
 *
 * 測試平和的成立條件，包括門前清兩面聽、有副露等情況。
 *
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class PinfuTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試平和 - 門前清兩面聽。
     *
     * 手牌為標準平和型，應獲得 1 翻。
     */
    @Test
    fun `test pinfu menzen ryanmen`() {
        // 手牌：123m, 456m, 789m, 23m, 55m（兩面聽牌：1m, 4m）
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
            ),
        )
        // 自摸 4m，形成 234m 順子
        val winningTile = Tile.Numeric(Tile.Suit.Character, 4)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }
        assertEquals(1, pinfuResult?.han, "Pinfu should be 1 han")
    }

    /**
     * 測試平和 - 有副露。
     *
     * 有副露時應無法獲得平和。
     */
    @Test
    fun `test pinfu with fuuro returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 2),
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                    ),
                    sourceDirection = RelativeDirection.Left,
                ),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }
        assertNull(pinfuResult, "Pinfu should not be present when there is fuuro")
    }

    private fun standardStructure(fuuro: List<Fuuro>): HandStructure.Standard = HandStructure.Standard(
        mentsus = listOf(
            Mentsu.Shuntsu(Tile.Numeric(Tile.Suit.Character, 1)),
            Mentsu.Shuntsu(Tile.Numeric(Tile.Suit.Character, 4)),
            Mentsu.Shuntsu(Tile.Numeric(Tile.Suit.Character, 7)),
        ),
        pair = Janto(Tile.Numeric(Tile.Suit.Dot, 5)),
        fuuro = fuuro,
        completionType = CompletionType.Ryanmen,
    )

    /**
     * 迴歸測試：即使呼叫端把 `isMenzen` 蓋成 `true`（`FuCalculator.isFuuroPinfuRon` 借用
     * [calculatePinfu] 檢查「若門前清是否為平和形」時就是這樣做），只要副露（`fuuro`）裡混有
     * 非順子的面子（例如碰），依然不應該判定為平和——修正前只檢查 `mentsus`，完全沒檢查
     * `fuuro`，會讓這種情況誤判成平和。
     */
    @Test
    fun `test pinfu returns null when fuuro contains a non-shuntsu meld even with isMenzen forced true`() {
        val structure = standardStructure(
            fuuro = listOf(Fuuro(Mentsu.Kotsu(Tile.Numeric(Tile.Suit.Bamboo, 1)), from = RelativeDirection.Left)),
        )

        val result = calculatePinfu(structure, isMenzen = true, roundWind = Wind.EAST, seatWind = Wind.EAST)

        assertNull(result, "A meld sitting in fuuro that isn't a Shuntsu must disqualify Pinfu, even with isMenzen forced true.")
    }

    /**
     * 迴歸測試：暗槓（[Mentsu.Ankan]）不會破壞門前清狀態（`isMenzen` 為 `true`），但平和要求
     * 完全零副露（連暗槓都不行）。修正前 `mentsus` 迴圈抓不到放在 `fuuro` 裡的暗槓，會誤判成平和。
     */
    @Test
    fun `test pinfu returns null when fuuro contains a closed kan even though isMenzen is true`() {
        val structure = standardStructure(
            fuuro = listOf(Fuuro(Mentsu.Ankan(Tile.Numeric(Tile.Suit.Bamboo, 1)), from = RelativeDirection.Self)),
        )

        val result = calculatePinfu(structure, isMenzen = true, roundWind = Wind.EAST, seatWind = Wind.EAST)

        assertNull(result, "A closed kan disqualifies Pinfu even though it doesn't break isMenzen.")
    }
}
