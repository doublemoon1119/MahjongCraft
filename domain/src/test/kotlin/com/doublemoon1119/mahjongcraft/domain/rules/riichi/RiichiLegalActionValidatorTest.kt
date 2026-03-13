package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeMahjongRuleConfig
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 立直麻將合法動作驗證器之單元測試。
 *
 * 測試內容涵蓋吃、碰、槓、胡牌（自摸及榮和）等動作之合法性判斷。
 *
 * @see RiichiLegalActionValidator
 */
class RiichiLegalActionValidatorTest {

    private val validator = RiichiLegalActionValidator(RiichiShantenCalculator())

    /**
     * 輔助函式，用於從 Tile 列表快速建立一個 Hand 物件。
     *
     * @param tiles 組成手牌的牌列表。
     * @return 包含指定牌的手牌物件。
     */
    private fun createHand(tiles: List<Tile>): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        return Hand(identifiedTiles.toMutableList())
    }

    /**
     * 測試可執行碰牌動作之情況。
     *
     * 當手牌中有兩張與打出的牌相同時，應可執行碰牌動作。
     */
    @Test
    fun `test can pon`() {
        // 準備
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Pon && it.tileId == incomingTile.id })
    }

    /**
     * 測試可執行吃牌動作之情況。
     *
     * 當手牌中有可與打出的牌組成順子之兩張牌時，應可執行吃牌動作。
     */
    @Test
    fun `test can chi`() {
        // 準備
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Left,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Chi && it.tileId == incomingTile.id })
    }

    /**
     * 測試可執行大明槓動作之情況。
     *
     * 當手牌中有三張與打出的牌相同時，應可執行大明槓動作。
     */
    @Test
    fun `test can open kan`() {
        // 準備
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.OPEN_KAN })
    }

    /**
     * 測試可執行加槓動作之情況。
     *
     * 當已有碰牌副露，且摸入與該碰牌相同之牌時，應可執行加槓動作。
     */
    @Test
    fun `test can added kan`() {
        // 準備
        val ponMeld = Meld(
            MeldType.PON,
            listOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
            )
        )
        val playerHand = Hand(melds = mutableListOf(ponMeld))
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.ADDED_KAN })
    }

    /**
     * 測試可執行暗槓動作之情況。
     *
     * 當手牌中有三張相同之牌時，應可執行暗槓動作。
     */
    @Test
    fun `test can closed kan`() {
        // 準備
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.CLOSED_KAN })
    }

    /**
     * 測試可執行榮和動作之情況（標準型）。
     *
     * 當打出的牌可使手牌形成胡牌結構時，應可執行榮和動作。
     */
    @Test
    fun `test can ron`() {
        // 準備
        // 手牌: 111234567899m + incoming 9m = 111 234 567 899 99 (4面子+1雀頭=14張=胡牌)
        // 原本13張: 1,1,1,2,3,4,5,6,7,8,9,9,9 = 13張
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Ron && it.tileId == incomingTile.id })
    }

    /**
     * 測試可執行自摸動作之情況。
     *
     * 當摸入的牌可使手牌形成胡牌結構時，應可執行自摸動作。
     */
    @Test
    fun `test can tsumo`() {
        // 準備
        // 手牌: 111234567899m + incoming 9m = 111 234 567 899 99 (4面子+1雀頭=14張=胡牌)
        // 原本13張: 1,1,1,2,3,4,5,6,7,8,9,9,9 = 13張
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試可執行榮和動作之情況（七對子）。
     *
     * 當打出的牌可使手牌形成七對子胡牌結構時，應可執行榮和動作。
     */
    @Test
    fun `test can ron with seven pairs`() {
        // 準備
        // 手牌: 112233445566m (6對子=12張) + incoming 7m = 7對子 (胡牌)
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 7))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Ron && it.tileId == incomingTile.id })
    }

    /**
     * 測試有副露時不可執行七對子榮和動作之情況。
     *
     * 七對子必須門前清，當已有副露時，應不可執行七對子胡牌動作。
     */
    @Test
    fun `test cannot ron with seven pairs if exposed meld`() {
        // 準備
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5)
            )
        )
        val ponMeld = Meld(
            MeldType.PON,
            listOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 6)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 6)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 6))
            )
        )
        playerHand.call(ponMeld.type, ponMeld.tiles, ponMeld.sourceTile, ponMeld.sourceDirection)
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile()
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 7))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證
        assertFalse(actions.any { it is GameAction.Ron })
    }
}
