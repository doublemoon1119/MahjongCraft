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
 * 立直麻將合法動作驗證器之立直相關測試。
 *
 * 測試內容涵蓋立直宣告的條件判斷。
 *
 * @see RiichiLegalActionValidator
 */
class RiichiLegalActionValidatorRiichiTest {

    private val validator = RiichiLegalActionValidator(RiichiShantenCalculator())

    /**
     * 輔助函式，用於從 Tile 列表快速建立一個 Hand 物件。
     */
    private fun createHand(tiles: List<Tile>): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        return Hand(identifiedTiles.toMutableList())
    }

    /**
     * 測試可執行立直動作之情況（點數充足）。
     *
     * 當玩家聽牌、門前清且點數 >= 1000 時，應可執行立直動作。
     */
    @Test
    fun `test can riichi with sufficient score`() {
        // 準備
        // 手牌已聽牌
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
        player.score = 1000  // 點數剛好 1000（要在 TableState 建立後設定）

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = null
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Riichi })
    }

    /**
     * 測試可執行立直動作之情況（點數超過 1000）。
     *
     * 當玩家點數 > 1000 時，應可執行立直動作。
     */
    @Test
    fun `test can riichi with high score`() {
        // 準備
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
        player.score = 2500  // 點數超過 1000（要在 TableState 建立後設定）

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = null
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Riichi })
    }

    /**
     * 測試不可執行立直動作之情況（點數不足）。
     *
     * 當玩家點數 < 1000 時，不可執行立直動作。
     */
    @Test
    fun `test cannot riichi with insufficient score`() {
        // 準備
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
        player.score = 500  // 點數不足 1000（要在 TableState 建立後設定）

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = null
        )

        // 驗證
        assertFalse(actions.any { it is GameAction.Riichi })
    }

    /**
     * 測試不可執行立直動作之情況（點數為 0）。
     *
     * 當玩家點數為 0 時，不可執行立直動作。
     */
    @Test
    fun `test cannot riichi with zero score`() {
        // 準備
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
        player.score = 0  // 點數為 0（要在 TableState 建立後設定）

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = null
        )

        // 驗證
        assertFalse(actions.any { it is GameAction.Riichi })
    }
}
