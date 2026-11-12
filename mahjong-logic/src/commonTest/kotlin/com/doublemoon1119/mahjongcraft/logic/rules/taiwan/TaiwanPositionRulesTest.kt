package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationEffect
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.module.WinValue
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.taiwan.toTaiwanSnapshotForTests
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證台灣麻將規則查詢與正式結算一致，並只依評估者看得到的狀態回答。 */
class TaiwanPositionRulesTest {
    /** 用於比對查詢結果與實際結算的規則模組。 */
    private val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig())

    /** 本規則每台的分數。 */
    private val pointsPerTai = module.config.scoreConfig.pointPerTai

    /** 台灣麻將的規則查詢。 */
    private val rules = module.createPositionRules()

    /** 自摸的胡牌價值等於正式自摸結算的收入，包含連莊台。 */
    @Test
    fun `self-draw value matches the formal settlement`() {
        val table = table(comboCount = 2)
        val waiter = table.players[WAITER]

        val formal = checkNotNull(module.declareTsumo(table, waiter.copy(hand = waiter.hand.copy(lastDrawn = winningTile()))))

        assertEquals(WinValue.Points(formal.totalGained), rules.winValue(view(table, waiter), waiter.hand, WINNING_VALUE, isTsumo = true))
    }

    /** 榮和的胡牌價值等於每位對手放槍時正式結算收入的平均，因此莊家放槍的莊家台也會算進去。 */
    @Test
    fun `discard win value averages the formal settlement over every discarder`() {
        val table = table(comboCount = 1)
        val waiter = table.players[WAITER]
        val incomes = table.players.filter { it.id != waiter.id }.map { discarder ->
            checkNotNull(module.declareRon(table, waiter, winningTile(), discarder.id)).totalGained
        }

        assertEquals(WinValue.Points(incomes.sum() / incomes.size), rules.winValue(view(table, waiter), waiter.hand, WINNING_VALUE, isTsumo = false))
    }

    /** 假設宣告聽牌時多一台。 */
    @Test
    fun `a hypothetical ready declaration adds one tai`() {
        val table = table()
        val waiter = table.players[WAITER]

        val plain = assertIs<WinValue.Points>(rules.winValue(view(table, waiter), waiter.hand, WINNING_VALUE, isTsumo = false))
        val declared = assertIs<WinValue.Points>(rules.winValue(view(table, waiter), waiter.hand, WINNING_VALUE, isTsumo = false, declarations = setOf(TAIWAN_DECLARE_READY_ACTION)))

        assertEquals(DECLARED_READY_TAI * pointsPerTai, declared.points - plain.points)
    }

    /** 已成立的天聽以天聽的台數計算。 */
    @Test
    fun `an existing heavenly ready declaration counts its own tai`() {
        val table = table(waiterComponents = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.HEAVENLY))
        val waiter = table.players[WAITER]
        val undeclared = table(waiterComponents = ComponentSet.EMPTY)

        val declared = assertIs<WinValue.Points>(rules.winValue(view(table, waiter), waiter.hand, WINNING_VALUE, isTsumo = false))
        val plain = assertIs<WinValue.Points>(rules.winValue(view(undeclared, undeclared.players[WAITER]), waiter.hand, WINNING_VALUE, isTsumo = false))

        assertEquals(HEAVENLY_READY_TAI * pointsPerTai, declared.points - plain.points)
    }

    /** 過水中最後打出的牌仍是自己聽的牌時不能胡；打出其他牌之後就能胡。 */
    @Test
    fun `passed win blocks winning only while the last discard is still a wait`() {
        val passed = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit)
        val stillPassed = table(waiterComponents = passed, waiterDiscard = WINNING_TILE)
        val cleared = table(waiterComponents = passed)

        assertEquals(WinValue.NotWinnable, rules.winValue(view(stillPassed, stillPassed.players[WAITER]), stillPassed.players[WAITER].hand, WINNING_VALUE, isTsumo = true))
        assertIs<WinValue.Points>(rules.winValue(view(cleared, cleared.players[WAITER]), cleared.players[WAITER].hand, WINNING_VALUE, isTsumo = true))
    }

    /** 宣告聽牌公開給所有玩家；過水只有本人看得到。 */
    @Test
    fun `ready declaration is public and passed win is private`() {
        val table = table(
            waiterComponents = ComponentSet.EMPTY
                .with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.GENERAL)
                .with(TaiwanPlayerComponents.PASSED_WIN, Unit),
        )
        val waiter = table.players[WAITER]
        val asSelf = view(table, waiter).evaluator
        val asOther = view(table, table.players[0]).player(waiter.id)

        assertEquals(TaiwanReadyKind.GENERAL, asOther.componentViews[TaiwanPlayerComponentViews.READY_DECLARATION])
        assertNull(asOther.componentViews[TaiwanPlayerComponentViews.PASSED_WIN])
        assertNotNull(asSelf.componentViews[TaiwanPlayerComponentViews.PASSED_WIN])
    }

    /** 宣告聽牌不付分數且鎖住手牌；已宣告後不再提議宣告。 */
    @Test
    fun `ready declaration locks the hand and is offered only once`() {
        val undeclared = table()
        val declared = table(waiterComponents = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.GENERAL))

        assertEquals(DeclarationEffect(cost = 0, locksHand = true), rules.declarationEffect(view(undeclared, undeclared.players[WAITER]), TAIWAN_DECLARE_READY_ACTION))
        assertEquals(setOf(TAIWAN_DECLARE_READY_ACTION), rules.prospectiveDeclarations(view(undeclared, undeclared.players[WAITER]), undeclared.players[WAITER].hand))
        assertEquals(emptySet(), rules.prospectiveDeclarations(view(declared, declared.players[WAITER]), declared.players[WAITER].hand))
    }

    /**
     * 建立莊家為東家、南家單騎九索聽牌的四人桌況；每位玩家都已打過一張牌，因此不是起手胡。
     *
     * @param comboCount 連莊次數。
     * @param waiterComponents 南家的玩家組件。
     * @param waiterDiscard 南家打過的牌。
     * @return 桌況。
     */
    private fun table(
        comboCount: Int = 0,
        waiterComponents: ComponentSet = ComponentSet.EMPTY,
        waiterDiscard: Tile = Tile.Honor.North,
    ): TableState {
        val players = Wind.entries.mapIndexed { index, wind ->
            val discard = if (index == WAITER) waiterDiscard else Tile.Honor.West
            FakeMahjongPlayerFactory.create(
                initialSeat = wind,
                hand = if (index == WAITER) Hand(tiles = waitingTiles()) else Hand(),
                discardPile = TaiwanDiscardPile().discardTile(FakeIdentifiedTileFactory.create(discard)),
                components = if (index == WAITER) waiterComponents else ComponentSet.EMPTY,
            )
        }
        return FakeTableStateFactory.create(
            players = players,
            dealerPlayerId = players.first().id,
            config = TaiwanRuleConfig(),
            comboCount = comboCount,
            tileWall = TileWall(List(LIVE_TILES) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)) }),
        )
    }

    /**
     * 以 [player] 為觀察者的視角。
     *
     * @param table 權威桌況。
     * @param player 觀察桌況的玩家。
     * @return 查詢視角。
     */
    private fun view(table: TableState, player: MahjongPlayer): PositionView = PositionView(table.toTaiwanSnapshotForTests(player.id), player.id)

    /**
     * 單騎九索聽牌的十六張手牌。
     *
     * @return 手牌。
     */
    private fun waitingTiles(): List<IdentifiedTile> = (1..9).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, it)) } +
        (1..6).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it)) } +
        FakeIdentifiedTileFactory.create(WINNING_TILE)

    /**
     * 建立一張新的胡牌張。
     *
     * @return 九索。
     */
    private fun winningTile(): IdentifiedTile = FakeIdentifiedTileFactory.create(WINNING_TILE)

    /** 測試常數。 */
    private companion object {
        /** 聽牌玩家的座位。 */
        const val WAITER = 1

        /** 測試桌況的活牌張數。 */
        const val LIVE_TILES = 20

        /** 聽牌玩家等待的牌。 */
        val WINNING_TILE: Tile = Tile.Numeric(Tile.Suit.Bamboo, 9)

        /** 聽牌玩家等待的牌值。 */
        val WINNING_VALUE = TileValue(WINNING_TILE)
    }
}

/** 報聽的台數。 */
private const val DECLARED_READY_TAI = 1

/** 天聽的台數。 */
private const val HEAVENLY_READY_TAI = 16
