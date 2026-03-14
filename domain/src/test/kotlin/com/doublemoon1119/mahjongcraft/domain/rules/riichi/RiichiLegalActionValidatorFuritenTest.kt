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

/**
 * 立直麻將合法動作驗證器之振聽測試。
 *
 * 測試內容涵蓋振聽（Furiten）狀態下的榮和限制。
 * 振聽是指玩家聽牌後打了胡牌張，他家再打同張牌時不可榮和。
 *
 * @see RiichiLegalActionValidator
 */
class RiichiLegalActionValidatorFuritenTest {

    private val validator = RiichiLegalActionValidator(RiichiShantenCalculator())

    /**
     * 輔助函式，用於從 Tile 列表快速建立一個 Hand 物件。
     */
    private fun createHand(tiles: List<Tile>): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        return Hand(identifiedTiles.toMutableList())
    }

    /**
     * 測試振聽之情況：振聽狀態下不可執行榮和動作。
     *
     * 玩家聽牌後打了赤5萬，他家打普通5萬時不可榮和（視為振聽）。
     * 赤寶牌與普通牌在振聽判定上視為同張牌。
     */
    @Test
    fun `test cannot ron when furiten with red dora`() {
        // 準備
        // 手牌已聽牌，聽普通5萬
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5, isRed = true),   // 赤5萬
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9)
            )
        )
        // 模擬玩家已經打了赤5萬（振聽）
        val fakeDiscardPile = FakeDiscardPile()
        fakeDiscardPile.discard(
            FakeDiscardPile.FakeEntry(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5, isRed = true))
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = fakeDiscardPile
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        // 他家打普通5萬
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5, isRed = false))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證 - 因為打過赤5萬（視為同張牌），所以不可榮和
        assertFalse(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試振聽之情況：振聽狀態下不可執行榮和動作（打普通5，進赤5）。
     *
     * 玩家聽牌後打了普通5萬，他家打赤5萬時不可榮和（視為振聽）。
     * 赤寶牌與普通牌在振聽判定上視為同張牌。
     */
    @Test
    fun `test cannot ron when furiten with red dora incoming`() {
        // 準備
        // 手牌已聽牌，聽普通5萬
        val playerHand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5, isRed = false),   // 普通5萬
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9)
            )
        )
        // 模擬玩家已經打了普通5萬（振聽）
        val fakeDiscardPile = FakeDiscardPile()
        fakeDiscardPile.discard(
            FakeDiscardPile.FakeEntry(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5, isRed = false))
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = fakeDiscardPile
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        // 他家打赤5萬
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5, isRed = true))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證 - 因為打過普通5萬（視為同張牌），所以不可榮和
        assertFalse(actions.any { it is GameAction.Ron })
    }
}
