package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證台灣麻將的捨牌、過水、最低台數與宣告聽牌分析。 */
class TaiwanDiscardReadinessAnalyzerTest {
    private val threeBamboo = Tile.Numeric(Tile.Suit.Bamboo, 3)
    private val sixBamboo = Tile.Numeric(Tile.Suit.Bamboo, 6)
    private val floatingTile = Tile.Honor.East

    /** 四組完成面子、五筒雀頭與四五條兩面聽組成的十六張聽牌手牌。 */
    private val tenpaiTiles = listOf(
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Character, 5),
        Tile.Numeric(Tile.Suit.Character, 6),
        Tile.Numeric(Tile.Suit.Character, 7),
        Tile.Numeric(Tile.Suit.Character, 8),
        Tile.Numeric(Tile.Suit.Character, 9),
        Tile.Numeric(Tile.Suit.Dot, 1),
        Tile.Numeric(Tile.Suit.Dot, 2),
        Tile.Numeric(Tile.Suit.Dot, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Bamboo, 4),
        Tile.Numeric(Tile.Suit.Bamboo, 5),
    )

    /** 含一組刻子的普通聽牌；榮和只有門清一台，自摸另有自摸與不求人。 */
    private val mixedTenpaiTiles = listOf(
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Character, 5),
        Tile.Numeric(Tile.Suit.Character, 6),
        Tile.Numeric(Tile.Suit.Character, 7),
        Tile.Numeric(Tile.Suit.Character, 8),
        Tile.Numeric(Tile.Suit.Character, 9),
        Tile.Numeric(Tile.Suit.Dot, 1),
        Tile.Numeric(Tile.Suit.Dot, 2),
        Tile.Numeric(Tile.Suit.Dot, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Bamboo, 4),
        Tile.Numeric(Tile.Suit.Bamboo, 5),
    )

    @Test
    fun `current hand analysis returns both waits and uses only visible tiles for remaining counts`() {
        val player = player(tenpaiTiles)
        val table = table(player)
        val visibleThree = FakeIdentifiedTileFactory.create(threeBamboo)

        val analysis = assertNotNull(
            analyzer().analyzeCurrentHand(
                tableState = table,
                player = player,
                visibleTiles = player.hand.allTiles + visibleThree,
            ),
        )

        assertEquals(setOf<Tile>(threeBamboo, sixBamboo), analysis.waitingTiles.mapTo(linkedSetOf()) { it.tile })
        assertEquals(3, analysis.waitingTiles.single { it.tile == threeBamboo }.remainingCount)
        assertEquals(4, analysis.waitingTiles.single { it.tile == sixBamboo }.remainingCount)
        assertNull(analysis.statusIndicatorId)
    }

    @Test
    fun `remaining counts ignore concealed opponent tiles that were not supplied as visible`() {
        val player = player(tenpaiTiles)
        val opponent = player(List(3) { threeBamboo })
        val table = FakeTableStateFactory.create(
            players = listOf(player, opponent),
            dealerPlayerId = player.id,
            config = TaiwanRuleConfig(),
        )

        val analysis = assertNotNull(
            analyzer().analyzeCurrentHand(
                tableState = table,
                player = player,
                visibleTiles = player.hand.allTiles,
            ),
        )

        assertEquals(4, analysis.waitingTiles.single { it.tile == threeBamboo }.remainingCount)
    }

    @Test
    fun `discard analysis only returns candidates that leave the hand ready`() {
        val player = player(tenpaiTiles + floatingTile)
        val table = table(player)

        val analyses = analyzer().analyze(table, player, player.hand.allTiles)

        assertEquals(
            listOf(player.hand.standingTiles.single { it.tile == floatingTile }.id),
            analyses.map { it.discardTileId },
        )
        assertEquals(tenpaiTiles + floatingTile, player.hand.standingTiles.map { it.tile })
        assertEquals(emptyList(), player.discardPile.entries)
    }

    @Test
    fun `passed win remains only when the hypothetical discard is still a wait`() {
        val components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit)
        val retainingPlayer = player(tenpaiTiles + threeBamboo, components)
        val clearingPlayer = player(tenpaiTiles + floatingTile, components)

        val retaining = analyzer().analyze(table(retainingPlayer), retainingPlayer, retainingPlayer.hand.allTiles)
            .single { it.discardTileId == retainingPlayer.hand.standingTiles.last().id }
        val clearing = analyzer().analyze(table(clearingPlayer), clearingPlayer, clearingPlayer.hand.allTiles).single()

        assertEquals(TaiwanDiscardReadinessAnalyzer.StatusIds.PASSED_WIN, retaining.statusIndicatorId)
        assertNull(clearing.statusIndicatorId)
    }

    @Test
    fun `wait availability distinguishes tsumo only and below minimum tai`() {
        val player = playerAfterEarlierDiscard(mixedTenpaiTiles)

        val tsumoOnly = assertNotNull(
            analyzer(TaiwanRuleConfig(minimumWinConstraint = 2)).analyzeCurrentHand(
                tableState = table(player, TaiwanRuleConfig(minimumWinConstraint = 2)),
                player = player,
                visibleTiles = player.hand.allTiles,
            ),
        )
        val belowMinimum = assertNotNull(
            analyzer(TaiwanRuleConfig(minimumWinConstraint = 4)).analyzeCurrentHand(
                tableState = table(player, TaiwanRuleConfig(minimumWinConstraint = 4)),
                player = player,
                visibleTiles = player.hand.allTiles,
            ),
        )

        assertEquals(
            setOf(TaiwanDiscardReadinessAnalyzer.StatusIds.WIN_TSUMO_ONLY),
            tsumoOnly.waitingTiles.mapTo(linkedSetOf()) { it.winAvailability },
        )
        assertEquals(
            setOf(TaiwanDiscardReadinessAnalyzer.StatusIds.WIN_BELOW_MINIMUM),
            belowMinimum.waitingTiles.mapTo(linkedSetOf()) { it.winAvailability },
        )
    }

    @Test
    fun `declare ready analysis applies the official declaration tai before checking availability`() {
        val config = TaiwanRuleConfig(minimumWinConstraint = 2)
        val player = playerAfterEarlierDiscard(mixedTenpaiTiles + floatingTile)
        val table = table(player, config)
        val floatingId = player.hand.standingTiles.single { it.tile == floatingTile }.id

        val ordinary = analyzer(config).analyze(table, player, player.hand.allTiles).single { it.discardTileId == floatingId }
        val declared = analyzer(config).analyzeForAction(table, player, TAIWAN_DECLARE_READY_ACTION, player.hand.allTiles)
            .single { it.discardTileId == floatingId }

        assertEquals(
            setOf(TaiwanDiscardReadinessAnalyzer.StatusIds.WIN_TSUMO_ONLY),
            ordinary.waitingTiles.mapTo(linkedSetOf()) { it.winAvailability },
        )
        assertEquals(
            setOf(WaitingTileAvailabilityIds.WIN_AVAILABLE),
            declared.waitingTiles.mapTo(linkedSetOf()) { it.winAvailability },
        )
    }

    /** 建立不受起手胡情境影響、已經打過牌的測試玩家。 */
    private fun playerAfterEarlierDiscard(tiles: List<Tile>): MahjongPlayer = player(tiles).let { player ->
        player.copy(
            discardPile = player.discardPile.discardTile(
                FakeIdentifiedTileFactory.create(Tile.Honor.North),
            ),
        )
    }

    /** 建立指定手牌與規則組件的測試玩家。 */
    private fun player(
        tiles: List<Tile>,
        components: ComponentSet = ComponentSet.EMPTY,
    ): MahjongPlayer = FakeMahjongPlayerFactory.create(
        hand = FakeHandFactory.create(tiles),
        components = components,
    )

    /** 建立台灣麻將測試桌況。 */
    private fun table(
        player: MahjongPlayer,
        config: TaiwanRuleConfig = TaiwanRuleConfig(),
    ): TableState = FakeTableStateFactory.create(
        players = listOf(player),
        dealerPlayerId = player.id,
        config = config,
    )

    /** 建立指定設定的台灣麻將手牌分析器。 */
    private fun analyzer(config: TaiwanRuleConfig = TaiwanRuleConfig()): TaiwanDiscardReadinessAnalyzer = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, config).createDiscardReadinessAnalyzer()
}
