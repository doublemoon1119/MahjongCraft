package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證日麻本場點數的付款分攤。 */
class RiichiComboBonusTest {
    private val module: RiichiRuleModule = RiichiRuleModule(
        id = "mahjongcraft:riichi",
        config = RiichiRuleConfig(),
    )

    /** 驗證一般榮和時由放銃者支付 本場數 × 300。 */
    @Test
    fun `test ron combo bonus is paid by the discarder`() {
        val seats = Seats(winnerPao = null)
        val table = seats.table(comboCount = 2)
        val resolution = module.declareRon(table, seats.winner, FakeIdentifiedTileFactory.create(Tile.Honor.Red), discarderId = seats.west.id)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = seats.winner.id,
            discarderId = seats.west.id,
            resolution = resolution,
        )

        assertEquals(mapOf(seats.west.id to 600), payments)
    }

    /** 驗證一般自摸時其他每家各付 本場數 × 100。 */
    @Test
    fun `test tsumo combo bonus is paid by every other player`() {
        val seats = Seats(winnerPao = null, drawn = true)
        val table = seats.table(comboCount = 2)
        val resolution = module.declareTsumo(table, seats.winner)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = seats.winner.id,
            discarderId = null,
            resolution = resolution,
        )

        assertEquals(mapOf(seats.east.id to 200, seats.west.id to 200, seats.north.id to 200), payments)
    }

    /** 驗證沒有手牌結算的自摸等價結果（流局滿貫）比照一般自摸分攤。 */
    @Test
    fun `test combo bonus without resolution is shared like a tsumo`() {
        val seats = Seats(winnerPao = null)
        val table = seats.table(comboCount = 1)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = seats.winner.id,
            discarderId = null,
            resolution = null,
        )

        assertEquals(mapOf(seats.east.id to 100, seats.west.id to 100, seats.north.id to 100), payments)
    }

    /** 驗證本場數為 0 時不產生任何付款。 */
    @Test
    fun `test zero combo count produces no payments`() {
        val seats = Seats(winnerPao = null)
        val table = seats.table(comboCount = 0)
        val resolution = module.declareRon(table, seats.winner, FakeIdentifiedTileFactory.create(Tile.Honor.Red), discarderId = seats.west.id)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = seats.winner.id,
            discarderId = seats.west.id,
            resolution = resolution,
        )

        assertTrue(payments.isEmpty())
    }

    /** 驗證包牌自摸時由責任者一人支付全部本場點數。 */
    @Test
    fun `test pao tsumo combo bonus is paid entirely by the liable player`() {
        val seats = Seats(winnerPao = RelativeDirection.Left, drawn = true)
        val table = seats.table(comboCount = 3)
        val resolution = module.declareTsumo(table, seats.winner)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = seats.winner.id,
            discarderId = null,
            resolution = resolution,
        )

        assertEquals(mapOf(seats.east.id to 900), payments)
    }

    /** 驗證包牌責任者本人放銃時由責任者一人支付全部本場點數。 */
    @Test
    fun `test pao liable discarder pays the whole combo bonus`() {
        val seats = Seats(winnerPao = RelativeDirection.Left)
        val table = seats.table(comboCount = 3)
        val resolution = module.declareRon(table, seats.winner, FakeIdentifiedTileFactory.create(Tile.Honor.Red), discarderId = seats.east.id)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = seats.winner.id,
            discarderId = seats.east.id,
            resolution = resolution,
        )

        assertEquals(mapOf(seats.east.id to 900), payments)
    }

    /** 驗證第三方放銃給被包者時，本場點數由放銃者與責任者平分。 */
    @Test
    fun `test third party ron against pao winner splits the combo bonus`() {
        val seats = Seats(winnerPao = RelativeDirection.Left)
        val table = seats.table(comboCount = 3)
        val resolution = module.declareRon(table, seats.winner, FakeIdentifiedTileFactory.create(Tile.Honor.Red), discarderId = seats.west.id)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = seats.winner.id,
            discarderId = seats.west.id,
            resolution = resolution,
        )

        assertEquals(mapOf(seats.west.id to 450, seats.east.id to 450), payments)
    }

    /**
     * 四人座位：東家為莊家；南家是贏家，手牌差一張中完成大三元。
     *
     * @param winnerPao 贏家身上包牌責任的方向；東家位於南家的 [RelativeDirection.Left]。
     * @param drawn 贏家是否已摸進胡牌張，供自摸結算使用。
     */
    private class Seats(winnerPao: RelativeDirection?, drawn: Boolean = false) {
        val east: MahjongPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner: MahjongPlayer = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(
                tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) },
                lastDrawn = if (drawn) FakeIdentifiedTileFactory.create(Tile.Honor.Red) else null,
            ),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(paoLiability = winnerPao?.let { PaoLiability(PaoYaku.Daisangen, it) }),
        )
        val west: MahjongPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val north: MahjongPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)

        /** 以 [comboCount] 本場建立四人桌況。 */
        fun table(comboCount: Int): TableState = FakeTableStateFactory.create(
            players = listOf(east, winner, west, north),
            comboCount = comboCount,
            config = RiichiRuleConfig(),
        )

        /** 中中、發發發、白白白、123m、55p。 */
        private fun daisangenTiles() = listOf(
            Tile.Honor.Red, Tile.Honor.Red,
            Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
            Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Dot, 5),
            Tile.Numeric(Tile.Suit.Dot, 5),
        )
    }
}
