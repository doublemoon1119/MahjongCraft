package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeRiichiRuleConfig
import java.util.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 立直麻將合法動作驗證器之王牌、寶牌指示牌、海底撈月、河底撈魚測試。
 *
 * 測試內容涵蓋：
 * - 牌山最後一張牌時的海底撈月與河底撈魚判定
 * - 不同槓數下的寶牌指示牌計算
 * - 裏寶牌指示牌僅在立直後可得
 *
 * @see RiichiLegalActionValidator
 */
class RiichiLegalActionValidatorDoraTest {

    private val validator = RiichiLegalActionValidator(
        shantenCalculator = RiichiShantenCalculator(),
        handValueCalculator = RiichiHandValueCalculator()
    )

    private fun createHand(tiles: List<Tile>): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        return Hand(identifiedTiles.toMutableList())
    }

    /**
     * 測試海底撈月：當牌山剩餘牌數等於王牌數時，自摸應設定 isLastDraw 為 true。
     *
     * 場景：牌山剩餘 14 張王牌，自摸胡牌。
     */
    @Test
    fun `test tsumo at last draw sets isLastDraw true`() {
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
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wanPaiCount = 14
        val wallTiles = (1..wanPaiCount).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = wanPaiCount)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        assertTrue(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試河底撈魚：當牌山剩餘牌數等於王牌數時，榮和應設定 isLastDiscard 為 true。
     *
     * 場景：牌山剩餘 14 張王牌，榮和胡牌。
     */
    @Test
    fun `test ron at last discard sets isLastDiscard true`() {
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
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wanPaiCount = 14
        val wallTiles = (1..wanPaiCount).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = wanPaiCount)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        assertTrue(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試海底撈月與河底撈魚標記不會在牌山還有剩餘時觸發。
     *
     * 場景：牌山剩餘 15 張（多於王牌數 14），不應設定 isLastDraw/isLastDiscard。
     */
    @Test
    fun `test last draw discard flags not set when tiles remain`() {
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
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wanPaiCount = 14
        val wallTiles = (1..(wanPaiCount + 1)).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = wanPaiCount)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        assertTrue(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試王牌計算邏輯：牌山結構為 14 張王牌時，無槓應有 1 張寶牌指示牌。
     *
     * 日麻王牌配置（14張）：
     * - 位置 0-3（最左側）：第1組王牌（嶺上牌）
     * - 位置 4-5：第1組寶牌指示牌
     * - 位置 6-7：第2組王牌
     * - 位置 8-9：第2組寶牌指示牌
     * - 以此類推...
     *
     * 反轉後（由右至左取最後14張）：
     * - 索引 0 = 原位置 13（嶺上牌）
     * - 索引 1 = 原位置 12（王牌）
     * - 索引 2 = 原位置 11（嶺上牌）
     * - 索引 3 = 原位置 10（王牌）
     * - 索引 4 = 原位置 9（寶牌指示牌1）
     * - 索引 5 = 原位置 8（裏寶牌指示牌1）
     * - ...
     */
    @Test
    fun `test dora indicator count with no kan`() {
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
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wanPaiCount = 14
        val normalTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..wanPaiCount).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = wanPaiCount)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        assertTrue(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試王牌計算邏輯：場上有 1 個槓時，validator 仍能正確處理。
     *
     * 此測試驗證場上有 1 個槓時，validator 不會崩潰並能正常執行。
     * 手牌結構：1 個大明槓（面子）+ 10 張立牌（聽牌）
     * 牌型：111m(大明槓) + 23456789m + 11z(雀頭) = 14張
     */
    @Test
    fun `test dora indicator count with one kan`() {
        val kanTiles = listOf(
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
        )
        val kanMeld = Meld(
            MeldType.OPEN_KAN,
            kanTiles,
            sourceTile = kanTiles.first(),
            sourceDirection = RelativeDirection.Across
        )
        val playerHand = Hand(
            mutableListOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 3)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 4)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 6)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 7)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 8)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9)),
                IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                IdentifiedTile(UUID.randomUUID(), Tile.Honor.East)
            ),
            mutableListOf(kanMeld)
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wanPaiCount = 14
        val normalTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..wanPaiCount).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = wanPaiCount)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        assertTrue(actions.isNotEmpty())
    }

    /**
     * 測試王牌計算邏輯：場上有 4 個槓時，validator 仍能正確處理。
     *
     * 此測試驗證場上有 4 個槓時，validator 不會崩潰並能正常執行。
     * 手牌結構：4 個大明槓（面子）+ 1 張多餘牌
     * 牌型：111m(大明槓) + 222p(大明槓) + 333s(大明槓) + 444m(大明槓) + 5m
     * 此手牌非聽牌狀態，actions 應為空或不包含胡牌動作
     */
    @Test
    fun `test dora indicator count with four kan`() {
        val kanMeld1 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
            ),
            sourceDirection = RelativeDirection.Across
        )
        val kanMeld2 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2))
            ),
            sourceDirection = RelativeDirection.Across
        )
        val kanMeld3 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3))
            ),
            sourceDirection = RelativeDirection.Across
        )
        val kanMeld4 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 4)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 4)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 4)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 4))
            ),
            sourceDirection = RelativeDirection.Across
        )
        val playerHand = Hand(
            mutableListOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5))
            ),
            mutableListOf(kanMeld1, kanMeld2, kanMeld3, kanMeld4)
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wanPaiCount = 14
        val normalTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..wanPaiCount).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = wanPaiCount)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Honor.East)

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        assertFalse(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試裏寶牌指示牌：未立直時不應包含裏寶牌指示牌。
     *
     * 此測試使用預設的 RiichiPlayerState()，其 isRiichi 為 false（因 riichiTile 和 doubleRiichiTile 皆為 null）。
     */
    @Test
    fun `test ura dora not included without riichi`() {
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
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wanPaiCount = 14
        val wallTiles = (1..wanPaiCount).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = wanPaiCount)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        assertTrue(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試王牌計算邊界情況：wanPaiCount 為 0 時應正常運作。
     *
     * 當 wanPaiCount 為 0 時，取牌山最後 0 張作為王牌，
     * 迴圈不會執行，doraIndicators 應為空列表。
     */
    @Test
    fun `test dora calculation with zero wanpai count`() {
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
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )

        val wallTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val config = FakeRiichiRuleConfig(deadTileCount = 0)
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = config
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        assertTrue(actions.any { it is GameAction.Tsumo })
    }
}
