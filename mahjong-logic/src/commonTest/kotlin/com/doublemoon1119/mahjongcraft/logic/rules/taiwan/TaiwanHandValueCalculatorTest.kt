package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 台麻台數、互斥、拆牌取最高及輸入驗證。 */
class TaiwanHandValueCalculatorTest {
    /** 計算預設台表。 */
    private val calculator = TaiwanHandValueCalculator(TaiwanRuleConfig())

    /** 五順子的兩面放槍符合平胡，門清可另外加台。 */
    @Test
    fun `all sequences permits concealed hand and rejects self draw and flowers`() {
        val context = context(sequences())
        val result = calculator.calculate(context)
        assertEquals(3, result.totalTai)
        assertEquals(setOf(TaiwanTaiType.ConcealedHand, TaiwanTaiType.AllSequences), types(result))
        assertFalse(TaiwanTaiType.AllSequences in types(calculator.calculate(context.copy(isTsumo = true))))
        assertFalse(TaiwanTaiType.AllSequences in types(calculator.calculate(context.copy(flowers = listOf(Tile.Extension(TaiwanTileTypes.SUMMER))))))
    }

    /** 五暗刻只取最高暗刻獎勵，另計碰碰胡與門清自摸。 */
    @Test
    fun `five concealed triplets includes all triplets without lower concealed awards`() {
        val context = context(m(1, 1, 1) + p(2, 2, 2) + s(3, 3, 3) + List(3) { Tile.Honor.East } + List(3) { Tile.Honor.Red } + List(2) { Tile.Honor.South })
        val ron = calculator.calculate(context)
        assertEquals(17, ron.totalTai)
        assertTrue(TaiwanTaiType.FiveConcealedTriplets in types(ron))
        assertFalse(TaiwanTaiType.FourConcealedTriplets in types(ron))
        assertFalse(TaiwanTaiType.ThreeConcealedTriplets in types(ron))
        val tsumo = calculator.calculate(context.copy(isTsumo = true))
        assertEquals(19, tsumo.totalTai)
    }

    /** 他家完成的刻子不算暗刻。 */
    @Test
    fun `discard completed triplet is not a concealed triplet`() {
        val complete = m(1, 1, 1) + p(2, 2, 2) + s(3, 3, 3) + List(2) { Tile.Honor.South } + List(3) { Tile.Honor.East } + List(3) { Tile.Honor.Red }
        val result = calculator.calculate(context(complete))
        assertTrue(TaiwanTaiType.FourConcealedTriplets in types(result))
        assertFalse(TaiwanTaiType.FiveConcealedTriplets in types(result))
    }

    /** 同雀頭的多種面子拆法也需全部比較。 */
    @Test
    fun `highest scoring partition is selected without merging alternative awards`() {
        val complete = m(1, 1, 1, 2, 2, 2, 3, 3, 3) + p(4, 5, 6) + s(7, 8, 9) + List(2) { Tile.Honor.East }
        val result = calculator.calculate(context(complete))
        assertTrue(TaiwanTaiType.ThreeConcealedTriplets in types(result))
        assertEquals(4, result.totalTai)
    }

    /** 字一色加碰碰胡，大四喜則排除門風及圈風。 */
    @Test
    fun `all honors stacks with all triplets while big winds suppress wind awards`() {
        val complete = List(3) { Tile.Honor.East } + List(3) { Tile.Honor.South } + List(3) { Tile.Honor.West } +
            List(3) { Tile.Honor.North } + List(3) { Tile.Honor.Red } + List(2) { Tile.Honor.White }
        val result = calculator.calculate(context(complete))
        assertTrue(setOf(TaiwanTaiType.AllHonors, TaiwanTaiType.AllTriplets, TaiwanTaiType.BigWinds).all { it in types(result) })
        assertFalse(TaiwanTaiType.SeatWind in types(result))
        assertFalse(TaiwanTaiType.RoundWind in types(result))
    }

    /** 大小三元均不再加單獨三元台。 */
    @Test
    fun `big and little dragons replace individual dragon awards`() {
        val base = m(1, 2, 3) + p(4, 5, 6) + List(3) { Tile.Honor.Red } + List(3) { Tile.Honor.Green }
        val big = calculator.calculate(context(base + List(3) { Tile.Honor.White } + List(2) { Tile.Honor.South }))
        val little = calculator.calculate(context(base + s(7, 8, 9) + List(2) { Tile.Honor.White }))
        assertTrue(TaiwanTaiType.BigDragons in types(big))
        assertTrue(TaiwanTaiType.LittleDragons in types(little))
        assertFalse(TaiwanTaiType.Dragon in types(big))
        assertFalse(TaiwanTaiType.Dragon in types(little))
    }

    /** 花槓仍計正花，八花不在一般計台器中自動變成花胡。 */
    @Test
    fun `flower sets stack with matching seat flowers`() {
        val flowers = TaiwanTileTypes.createAll()
        val result = calculator.calculate(context(sequences()).copy(flowers = flowers))
        assertEquals(2, result.taiResults.single { it.type == TaiwanTaiType.SeatFlower }.tai)
        assertEquals(2, result.taiResults.single { it.type == TaiwanTaiType.FlowerSet }.tai)
        assertFalse(TaiwanTaiType.EightFlowers in types(result))
        assertFalse(calculator.calculate(context(sequences()).copy(flowers = flowers + flowers.first())).isWinningHand)
    }

    /** 天胡保留自摸，排除門清額外台、獨聽與補牌台。 */
    @Test
    fun `heavenly win excludes specified awards but retains self draw`() {
        val result = calculator.calculate(
            context = context(sequences()).copy(
                isTsumo = true,
                situation = TaiwanWinSituation(
                    isReplacementWin = true,
                    openingWin = TaiwanTaiType.HeavenlyWin,
                    readyQualification = TaiwanTaiType.HeavenlyReady,
                ),
            ),
        )
        assertEquals(25, result.totalTai)
        assertEquals(setOf(TaiwanTaiType.HeavenlyWin, TaiwanTaiType.SelfDraw), types(result))
    }

    /** 聽牌資格互斥由輸入型別限制，普通牌況不會自動取得資格。 */
    @Test
    fun `ready and situational awards are explicit and opening win replaces ready`() {
        val context = context(sequences())
        val ready = calculator.calculate(context.copy(situation = TaiwanWinSituation(readyQualification = TaiwanTaiType.HeavenlyReady)))
        assertEquals(19, ready.totalTai)
        val human = calculator.calculate(context.copy(situation = TaiwanWinSituation(openingWin = TaiwanTaiType.HumanWin, readyQualification = TaiwanTaiType.EarthlyReady)))
        assertEquals(setOf(TaiwanTaiType.HumanWin, TaiwanTaiType.AllSequences), types(human))
        assertEquals(18, human.totalTai)
        val robbed = calculator.calculate(context.copy(situation = TaiwanWinSituation(isRobbingKong = true)))
        assertEquals(4, robbed.totalTai)
    }

    /** 不完整、超量及不相符的胡牌來源不能產生有效結果。 */
    @Test
    fun `invalid structure multiplicity and contradictory circumstances are rejected`() {
        assertFalse(calculator.calculate(context(sequences().drop(1))).isWinningHand)
        assertFalse(calculator.calculate(context(List(17) { Tile.Honor.East })).isWinningHand)
        assertFalse(calculator.calculate(context(sequences()).copy(isTsumo = true, situation = TaiwanWinSituation(isRobbingKong = true))).isWinningHand)
        assertFalse(TaiwanHandValueCalculator(TaiwanRuleConfig(minimumWinConstraint = 4)).calculate(context(sequences())).isWinningHand)
    }

    /** 五組副露只留雀頭，自摸與全求人的台數依來源區分。 */
    @Test
    fun `fully exposed self draw and discard win have different awards`() {
        val faces = listOf(m(1, 2, 3), m(4, 5, 6), p(2, 3, 4), p(6, 7, 8), s(7, 8, 9))
        val melds = faces.map { meld(it, MeldType.Chi) }
        val hand = FakeHandFactory.create(tiles = listOf(Tile.Honor.West), melds = melds)
        val context = context(List(17) { Tile.Honor.East }).copy(hand = hand, winningTileValue = TileValue(Tile.Honor.West), isTsumo = true)
        val tsumo = calculator.calculate(context)
        assertTrue(tsumo.isWinningHand)
        assertEquals(1, tsumo.totalTai)
        val ron = calculator.calculate(context.copy(isTsumo = false))
        assertEquals(2, ron.totalTai)
        assertEquals(setOf(TaiwanTaiType.AllExposed), types(ron))
    }

    /** 有副露且無任何計台項目的胡牌仍可成立。 */
    @Test
    fun `zero tai is valid unless a minimum is configured`() {
        val meld = meld(m(1, 2, 3), MeldType.Chi)
        val complete = m(4, 5, 6) + p(2, 3, 4) + s(7, 8, 9) + List(2) { Tile.Honor.South } + List(3) { Tile.Honor.West }
        val context = context(complete).let { it.copy(hand = it.hand.copy(melds = listOf(meld))) }
        val result = calculator.calculate(context)
        assertTrue(result.isWinningHand)
        assertEquals(0, result.totalTai)
        assertTrue(result.taiResults.isEmpty())
        assertFalse(TaiwanHandValueCalculator(TaiwanRuleConfig(minimumWinConstraint = 1)).calculate(context).isWinningHand)
    }

    /** 混一色、清一色及小四喜採各自成立與排除條件。 */
    @Test
    fun `flush and little winds use their own stacking rules`() {
        val pure = calculator.calculate(context(m(1, 2, 3, 2, 3, 4, 4, 5, 6, 6, 7, 8, 7, 8, 9, 5, 5)))
        assertTrue(TaiwanTaiType.FullFlush in types(pure))
        assertFalse(TaiwanTaiType.HalfFlush in types(pure))
        val mixed = calculator.calculate(context(m(1, 2, 3, 2, 3, 4, 4, 5, 6, 7, 8, 9) + List(3) { Tile.Honor.Red } + List(2) { Tile.Honor.South }))
        assertTrue(TaiwanTaiType.HalfFlush in types(mixed))
        val winds = calculator.calculate(
            context = context(
                complete = List(3) { Tile.Honor.East } + List(3) { Tile.Honor.South } + List(3) { Tile.Honor.West } +
                    m(1, 2, 3) + p(4, 5, 6) + List(2) { Tile.Honor.North },
            ),
        )
        assertTrue(TaiwanTaiType.LittleWinds in types(winds))
        assertFalse(TaiwanTaiType.SeatWind in types(winds))
        assertFalse(TaiwanTaiType.RoundWind in types(winds))
    }

    /** 暗槓仍為門清及暗刻，且其四張牌須列入總持有量檢查。 */
    @Test
    fun `closed kong contributes a concealed group and rejects a fifth owned copy`() {
        val tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.Red) }
        val closed = Meld(
            type = MeldType.ClosedKan,
            tiles = tiles,
            sourceDirection = RelativeDirection.Self,
            publiclyRevealedTileIds = emptySet(),
            faceDownTileIds = tiles.map { it.id }.toSet(),
        )
        val remaining = m(1, 1, 1) + p(2, 2, 2) + s(3, 3, 3) + List(3) { Tile.Honor.East } + List(2) { Tile.Honor.South }
        val context = context(remaining).let { it.copy(hand = it.hand.copy(melds = listOf(closed))) }
        val result = calculator.calculate(context)
        assertTrue(result.isWinningHand)
        assertTrue(TaiwanTaiType.FiveConcealedTriplets in types(result))
        assertTrue(TaiwanTaiType.ConcealedHand in types(result))
        val invalid = context.copy(winningTileValue = TileValue(Tile.Honor.Red))
        assertFalse(calculator.calculate(invalid).isWinningHand)
    }

    /** 補牌、海底及地胡有明確計台輸入，不靠牌型猜測。 */
    @Test
    fun `replacement last draw and earthly win retain the specified awards`() {
        val context = context(sequences()).copy(isTsumo = true)
        val normal = calculator.calculate(context.copy(situation = TaiwanWinSituation(isReplacementWin = true, isLastDraw = true)))
        assertEquals(5, normal.totalTai)
        val earthly = calculator.calculate(context.copy(situation = TaiwanWinSituation(isReplacementWin = true, openingWin = TaiwanTaiType.EarthlyWin)))
        assertEquals(18, earthly.totalTai)
        assertEquals(setOf(TaiwanTaiType.EarthlyWin, TaiwanTaiType.SelfDraw, TaiwanTaiType.ReplacementWin), types(earthly))
        val ready = calculator.calculate(context.copy(situation = TaiwanWinSituation(readyQualification = TaiwanTaiType.EarthlyReady)))
        assertEquals(11, ready.totalTai)
    }

    /** 建立17張完整牌例，最後一張獨立作為胡牌張。 */
    private fun context(complete: List<Tile>): TaiwanHandValueContext = TaiwanHandValueContext(
        hand = FakeHandFactory.create(complete.dropLast(1)),
        winningTileValue = TileValue(complete.last()),
        isTsumo = false,
        roundWind = Wind.EAST,
        seatWind = Wind.EAST,
        flowers = emptyList(),
        situation = TaiwanWinSituation(),
    )

    /** 五順子、數牌雀頭及兩面胡牌張。 */
    private fun sequences(): List<Tile> = m(1, 2, 3, 4, 5, 6) + p(2, 3, 4, 6, 7, 8) + s(5, 5, 7, 8, 9)

    /** 取得結果的項目集合。 */
    private fun types(result: TaiwanHandValueResult): Set<TaiwanTaiType> = result.taiResults.map { it.type }.toSet()

    /** 建立萬子牌例。 */
    private fun m(vararg ranks: Int): List<Tile> = ranks.map { Tile.Numeric(Tile.Suit.Character, it) }

    /** 建立筒子牌例。 */
    private fun p(vararg ranks: Int): List<Tile> = ranks.map { Tile.Numeric(Tile.Suit.Dot, it) }

    /** 建立條子牌例。 */
    private fun s(vararg ranks: Int): List<Tile> = ranks.map { Tile.Numeric(Tile.Suit.Bamboo, it) }

    /** 建立已公開、來源合法的副露。 */
    private fun meld(faces: List<Tile>, type: MeldType): Meld {
        val tiles = faces.map { FakeIdentifiedTileFactory.create(it) }
        return Meld(
            type = type,
            tiles = tiles,
            sourceTile = tiles.last(),
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = tiles.map { it.id }.toSet(),
            faceDownTileIds = emptySet(),
        )
    }
}
