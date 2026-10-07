package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.local

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContext
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Janto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證古役的成立條件、翻數、取代關係與古役開關。 */
class LocalYakuTest {
    /** 啟用古役的計算器。 */
    private val local = RiichiHandValueCalculator(useLocalYaku = true)

    /** 未啟用古役的計算器。 */
    private val standard = RiichiHandValueCalculator()

    /** 燕返：以立直宣言牌榮和為 1 翻；自摸或未啟用古役時不成立。 */
    @Test
    fun `tsubame gaeshi needs a ron on the riichi declaration tile`() {
        val context = context(plainHand(), m(9), isRiichiDeclarationDiscard = true)

        assertEquals(1, local.calculate(context).han(YakuType.TsubameGaeshi))
        assertFalse(local.calculate(context.copy(isRiichiDeclarationDiscard = false)).has(YakuType.TsubameGaeshi))
        assertFalse(local.calculate(context.copy(isTsumo = true)).has(YakuType.TsubameGaeshi))
        assertFalse(standard.calculate(context).has(YakuType.TsubameGaeshi))
    }

    /** 槓振：以槓後打出的牌榮和為 1 翻。 */
    @Test
    fun `kanburi needs a ron on the discard after a kan`() {
        val context = context(plainHand(), m(9), isDiscardAfterKan = true)

        assertEquals(1, local.calculate(context).han(YakuType.Kanburi))
        assertFalse(local.calculate(context.copy(isTsumo = true)).has(YakuType.Kanburi))
    }

    /** 十二落抬：四組副露後單騎和牌為 1 翻；有暗槓時不成立。 */
    @Test
    fun `shiiaru raotai needs four open melds and a single wait`() {
        val melds = listOf(chi(m(1)), pon(p(5)), chi(s(7)), pon(Tile.Honor.East))
        val result = local.calculate(context(FakeHandFactory.create(listOf(Tile.Honor.White), melds), Tile.Honor.White, isMenzen = false))

        assertEquals(1, result.han(YakuType.ShiiaruRaotai))
        val withClosedKan = melds.dropLast(1) + closedKan(Tile.Honor.East)
        assertFalse(local.calculate(context(FakeHandFactory.create(listOf(Tile.Honor.White), withClosedKan), Tile.Honor.White, isMenzen = false)).has(YakuType.ShiiaruRaotai))
    }

    /** 五門齊：萬、筒、索、風、三元齊全為 2 翻，七對子也成立；缺一種時不成立。 */
    @Test
    fun `uumen chii needs all five tile groups`() {
        val hand = FakeHandFactory.create(listOf(m(1), m(2), m(3), p(4), p(5), p(6), s(7), s(8), s(9), Tile.Honor.East, Tile.Honor.East, Tile.Honor.East, Tile.Honor.White))
        assertEquals(2, local.calculate(context(hand, Tile.Honor.White)).han(YakuType.UumenChii))

        val pairs = FakeHandFactory.create(listOf(m(1), m(1), m(5), m(5), p(2), p(2), s(3), s(3), s(8), s(8), Tile.Honor.North, Tile.Honor.North, Tile.Honor.Red))
        assertTrue(local.calculate(context(pairs, Tile.Honor.Red)).has(YakuType.UumenChii))

        val noDragon = FakeHandFactory.create(listOf(m(1), m(2), m(3), p(4), p(5), p(6), s(7), s(8), s(9), Tile.Honor.East, Tile.Honor.East, Tile.Honor.East, Tile.Honor.South))
        assertFalse(local.calculate(context(noDragon, Tile.Honor.South)).has(YakuType.UumenChii))
    }

    /** 三連刻：同花色連續三組刻子為 2 翻；不同花色不成立。 */
    @Test
    fun `sanrenkou needs three consecutive triplets of one suit`() {
        val hand = FakeHandFactory.create(listOf(m(5), m(5), m(5), p(2), p(3), s(9), s(9)), listOf(pon(m(3)), pon(m(4))))
        assertEquals(2, local.calculate(context(hand, p(4), isMenzen = false)).han(YakuType.Sanrenkou))

        val mixed = FakeHandFactory.create(listOf(p(5), p(5), p(5), p(2), p(3), s(9), s(9)), listOf(pon(m(3)), pon(m(4))))
        assertFalse(local.calculate(context(mixed, p(4), isMenzen = false)).has(YakuType.Sanrenkou))
    }

    /** 一色三同順：門清 3 翻、副露 2 翻。 */
    @Test
    fun `isshoku sanjun is three han closed and two han open`() {
        val sequences = HandStructure.Standard(
            mentsus = listOf(Mentsu.Shuntsu(m(2)), Mentsu.Shuntsu(m(2)), Mentsu.Shuntsu(m(2)), Mentsu.Shuntsu(p(5))),
            pair = Janto(s(9)),
        )
        assertEquals(3, calculateIsshokuSanjun(handStructure = sequences, isMenzen = true)?.han)
        assertEquals(2, calculateIsshokuSanjun(handStructure = sequences, isMenzen = false)?.han)

        val open = FakeHandFactory.create(listOf(m(1), m(2), m(3), m(1), m(2), m(3), p(5), p(6), p(7), s(9)), listOf(chi(m(1))))
        assertEquals(2, local.calculate(context(open, s(9), isMenzen = false)).han(YakuType.IsshokuSanjun))
    }

    /**
     * 三組相同順子也能讀成三組連續刻子；依高點法取較高的讀法，且一色三同順與一杯口不會同時出現。
     */
    @Test
    fun `isshoku sanjun never stacks with iipeikou`() {
        val closed = FakeHandFactory.create(listOf(m(2), m(3), m(4), m(2), m(3), m(4), m(2), m(3), m(4), p(5), p(6), p(7), s(9)))
        val result = local.calculate(context(closed, s(9)))

        assertTrue(result.has(YakuType.IsshokuSanjun) || result.has(YakuType.Sanrenkou), "${result.yakuResults}")
        assertFalse(result.has(YakuType.IsshokuSanjun) && result.has(YakuType.Iipeikou))
        assertTrue(standard.calculate(context(closed, s(9))).yakuResults.none { it.yaku.isLocal })
    }

    /** 一筒摸月取代海底摸月為 5 翻；海底不是一筒時仍是海底摸月。 */
    @Test
    fun `iipin moyue replaces haitei when the last draw is one pin`() {
        val hand = FakeHandFactory.create(listOf(m(2), m(3), m(4), s(5), s(6), s(7), s(2), s(3), s(4), Tile.Honor.East, Tile.Honor.East, p(2), p(3)))
        val result = local.calculate(context(hand, p(1), isTsumo = true, isLastDraw = true))

        assertEquals(5, result.han(YakuType.IipinMoyue))
        assertFalse(result.has(YakuType.Haitei))
        val other = local.calculate(context(hand, p(4), isTsumo = true, isLastDraw = true))
        assertTrue(other.has(YakuType.Haitei))
        assertFalse(other.has(YakuType.IipinMoyue))
    }

    /** 九筒撈魚取代河底撈魚為 5 翻。 */
    @Test
    fun `chuupin raoyui replaces houtei when the last discard is nine pin`() {
        val hand = FakeHandFactory.create(listOf(m(2), m(3), m(4), s(5), s(6), s(7), s(2), s(3), s(4), Tile.Honor.East, Tile.Honor.East, p(7), p(8)))
        val result = local.calculate(context(hand, p(9), isLastDiscard = true))

        assertEquals(5, result.han(YakuType.ChuupinRaoyui))
        assertFalse(result.has(YakuType.Houtei))
    }

    /** 人和：非莊家在第一巡榮和為役滿；莊家、自摸或非第一巡都不成立。 */
    @Test
    fun `renhou needs a non dealer ron in the first go around`() {
        val context = context(plainHand(), m(9), isFirstTurn = true)

        assertTrue(local.calculate(context).has(YakuType.Renhou))
        assertTrue(local.calculate(context).isYakuman)
        assertFalse(local.calculate(context.copy(isDealer = true)).has(YakuType.Renhou))
        assertFalse(local.calculate(context.copy(isFirstTurn = false)).has(YakuType.Renhou))
        assertFalse(standard.calculate(context).has(YakuType.Renhou))
    }

    /** 大車輪、大竹林、大數鄰分別為筒、索、萬的 2～8 各兩張；含 1 或 9 時不成立。 */
    @Test
    fun `wheel yakuman needs pairs of two to eight in one suit`() {
        listOf(
            Tile.Suit.Dot to YakuType.Daisharin,
            Tile.Suit.Bamboo to YakuType.Daichikurin,
            Tile.Suit.Character to YakuType.Daisuurin,
        ).forEach { (suit, yaku) ->
            val tiles = (2..8).flatMap { listOf(Tile.Numeric(suit, it), Tile.Numeric(suit, it)) }
            val result = local.calculate(context(FakeHandFactory.create(tiles.dropLast(1)), tiles.last()))
            assertTrue(result.has(yaku), "$suit should give $yaku, got ${result.yakuResults}")
            assertTrue(result.isYakuman)
        }
        val withNine = (2..7).flatMap { listOf(p(it), p(it)) } + p(9)
        assertFalse(local.calculate(context(FakeHandFactory.create(withNine), p(9))).has(YakuType.Daisharin))
    }

    /** 石上三年：雙立直後海底自摸或河底榮和為役滿。 */
    @Test
    fun `ishino ue sannen needs double riichi and the last tile`() {
        val tsumo = context(plainHand(), m(9), isTsumo = true, isRiichi = true, isDoubleRiichi = true, isLastDraw = true)
        val ron = context(plainHand(), m(9), isRiichi = true, isDoubleRiichi = true, isLastDiscard = true)

        assertTrue(local.calculate(tsumo).has(YakuType.IshinoUeSannen))
        assertTrue(local.calculate(ron).has(YakuType.IshinoUeSannen))
        assertFalse(local.calculate(tsumo.copy(isDoubleRiichi = false)).has(YakuType.IshinoUeSannen))
        assertFalse(local.calculate(tsumo.copy(isLastDraw = false)).has(YakuType.IshinoUeSannen))
    }

    /** 大七星：七種字牌各一對為雙倍役滿，取代字一色；未啟用古役時仍是字一色。 */
    @Test
    fun `daichisei is a double yakuman replacing tsuuiisou`() {
        val honors = listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red)
        val hand = FakeHandFactory.create(honors.flatMap { listOf(it, it) }.dropLast(1))
        val result = local.calculate(context(hand, Tile.Honor.Red))

        assertTrue(result.has(YakuType.Daichisei))
        assertFalse(result.has(YakuType.Tsuuiisou))
        assertEquals(-2, result.totalHan)
        assertTrue(standard.calculate(context(hand, Tile.Honor.Red)).has(YakuType.Tsuuiisou))
    }

    /** 未啟用古役時，任何古役都不會出現。 */
    @Test
    fun `local yaku never appear when disabled`() {
        val contexts = listOf(
            context(plainHand(), m(9), isRiichiDeclarationDiscard = true, isDiscardAfterKan = true, isFirstTurn = true),
            context(plainHand(), m(9), isTsumo = true, isRiichi = true, isDoubleRiichi = true, isLastDraw = true),
        )
        contexts.forEach { context ->
            assertTrue(standard.calculate(context).yakuResults.none { it.yaku.isLocal }, "${standard.calculate(context).yakuResults}")
        }
    }

    /** 和牌前 13 張：m1-8、p456、s789 的兩面等待（m6-9 不會成立其他古役）。 */
    private fun plainHand() = FakeHandFactory.create(listOf(m(1), m(2), m(3), p(4), p(5), p(6), s(7), s(8), s(9), s(2), s(2), m(7), m(8)))

    /**
     * 建立非莊家的計算情境。
     *
     * @param hand 和牌前的手牌。
     * @param winningTile 和牌張。
     * @param isTsumo 是否自摸。
     * @param isMenzen 是否門清。
     * @param isRiichi 是否立直。
     * @param isDoubleRiichi 是否雙立直。
     * @param isLastDraw 是否海底自摸。
     * @param isLastDiscard 是否河底榮和。
     * @param isFirstTurn 是否第一巡。
     * @param isRiichiDeclarationDiscard 是否以立直宣言牌榮和。
     * @param isDiscardAfterKan 是否以槓後捨牌榮和。
     * @return 計算情境。
     */
    private fun context(
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean = false,
        isMenzen: Boolean = true,
        isRiichi: Boolean = false,
        isDoubleRiichi: Boolean = false,
        isLastDraw: Boolean = false,
        isLastDiscard: Boolean = false,
        isFirstTurn: Boolean = false,
        isRiichiDeclarationDiscard: Boolean = false,
        isDiscardAfterKan: Boolean = false,
    ): RiichiHandValueContext = FakeRiichiHandValueContextFactory.create(
        hand = hand,
        winningTile = winningTile,
        isTsumo = isTsumo,
        isMenzen = isMenzen,
        isRiichi = isRiichi,
        isDoubleRiichi = isDoubleRiichi,
        seatWind = Wind.SOUTH,
        isDealer = false,
        isLastDraw = isLastDraw,
        isLastDiscard = isLastDiscard,
        isFirstTurn = isFirstTurn,
        isRiichiDeclarationDiscard = isRiichiDeclarationDiscard,
        isDiscardAfterKan = isDiscardAfterKan,
    )

    /** 萬子。 */
    private fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    /** 筒子。 */
    private fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    /** 索子。 */
    private fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)

    /** 碰。 */
    private fun pon(tile: Tile): Meld = meld(MeldType.PON, List(3) { tile })

    /** 以最小數字為首的吃。 */
    private fun chi(head: Tile): Meld {
        val numeric = head as Tile.Numeric
        return meld(MeldType.CHI, (0..2).map { Tile.Numeric(numeric.suit, numeric.value + it) })
    }

    /** 暗槓。 */
    private fun closedKan(tile: Tile): Meld = Meld(
        type = MeldType.CLOSED_KAN,
        tiles = List(4) { FakeIdentifiedTileFactory.create(tile) },
        sourceDirection = RelativeDirection.Self,
    )

    /**
     * 從左家鳴牌的副露。
     *
     * @param type 副露種類。
     * @param tiles 副露的牌。
     * @return 副露。
     */
    private fun meld(type: MeldType, tiles: List<Tile>): Meld {
        val identified = tiles.map { FakeIdentifiedTileFactory.create(it) }
        return Meld(type = type, tiles = identified, sourceTile = identified.last(), sourceDirection = RelativeDirection.Left)
    }

    /** 結果中是否含有指定役。 */
    private fun RiichiHandValueResult.has(yaku: YakuType): Boolean = yakuResults.any { it.yaku == yaku }

    /** 結果中指定役的翻數；沒有時為 0。 */
    private fun RiichiHandValueResult.han(yaku: YakuType): Int = yakuResults.firstOrNull { it.yaku == yaku }?.han ?: 0
}
