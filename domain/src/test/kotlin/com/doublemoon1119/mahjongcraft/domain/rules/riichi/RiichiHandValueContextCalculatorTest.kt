package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeRiichiRuleConfig
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 日本麻將役種上下文計算機之單元測試。
 *
 * 測試內容涵蓋：
 * - 海底撈月與河底撈魚的判定
 * - 寶牌指示牌的計算
 * - 裏寶牌指示牌的計算（立直/非立直）
 * - 不同槓數下的指示牌數量
 * - 邊界情況處理
 *
 * @see RiichiHandValueContextCalculator
 */
class RiichiHandValueContextCalculatorTest {

    private fun createCalculator(deadTileCount: Int = 14): RiichiHandValueContextCalculator {
        val config = FakeRiichiRuleConfig(deadTileCount = deadTileCount)
        return RiichiHandValueContextCalculator(config)
    }

    private fun createHand(tiles: List<Tile>): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        return Hand(identifiedTiles.toMutableList())
    }

    private fun createPlayer(hand: Hand, riichiState: RiichiPlayerState? = null): MahjongPlayer {
        return MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = hand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile(),
            playerRuleState = riichiState ?: RiichiPlayerState()
        )
    }

    private fun createPlayerWithKan(standingTiles: List<IdentifiedTile>, kanCount: Int): MahjongPlayer {
        val melds = mutableListOf<Meld>()
        for (i in 0 until kanCount) {
            val tileValue = (i % 9) + 1
            val kanMeld = Meld(
                MeldType.OPEN_KAN,
                listOf(
                    IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, tileValue)),
                    IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, tileValue)),
                    IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, tileValue)),
                    IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, tileValue))
                ),
                sourceDirection = RelativeDirection.Across
            )
            melds.add(kanMeld)
        }
        val hand = Hand(
            standingTiles.toMutableList(),
            melds
        )
        return MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = hand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )
    }

    /**
     * 測試海底撈月：當牌山剩餘牌數等於王牌數時，自摸應設定 isLastDraw 為 true。
     */
    @Test
    fun `test tsumo at last draw sets isLastDraw true`() {
        val calculator = createCalculator(deadTileCount = 14)

        val hand = createHand(
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
        val player = createPlayer(hand)

        val wanPaiTiles = (1..14).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )

        assertTrue(context.isLastDraw)
        assertFalse(context.isLastDiscard)
    }

    /**
     * 測試河底撈魚：當牌山剩餘牌數等於王牌數時，榮和應設定 isLastDiscard 為 true。
     */
    @Test
    fun `test ron at last discard sets isLastDiscard true`() {
        val calculator = createCalculator(deadTileCount = 14)

        val hand = createHand(
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
        val player = createPlayer(hand)

        val wanPaiTiles = (1..14).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = false
            )
        )

        assertFalse(context.isLastDraw)
        assertTrue(context.isLastDiscard)
    }

    /**
     * 測試海底撈月與河底撈魚標記不會在牌山還有剩餘時觸發。
     */
    @Test
    fun `test last draw discard flags not set when tiles remain`() {
        val calculator = createCalculator(deadTileCount = 14)

        val hand = createHand(
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
        val player = createPlayer(hand)

        val wallTiles = (1..15).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = false
            )
        )

        assertFalse(context.isLastDraw)
        assertFalse(context.isLastDiscard)
    }

    /**
     * 測試無槓時有 1 張寶牌指示牌。
     */
    @Test
    fun `test dora indicator count with no kan`() {
        val calculator = createCalculator(deadTileCount = 14)

        val hand = createHand(
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
        val player = createPlayer(hand)

        val normalTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..14).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )

        assertEquals(1, context.doraIndicators.size)
    }

    /**
     * 測試場上有 1 個槓時有 2 張寶牌指示牌。
     */
    @Test
    fun `test dora indicator count with one kan`() {
        val calculator = createCalculator(deadTileCount = 14)

        val kanMeld = Meld(
            MeldType.OPEN_KAN,
            listOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
            ),
            sourceDirection = RelativeDirection.Across
        )
        val hand = Hand(
            mutableListOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 3)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 4)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 6)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 7)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 8)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
            ),
            mutableListOf(kanMeld)
        )
        val player = createPlayer(hand)

        val normalTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..14).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )

        assertEquals(2, context.doraIndicators.size)
    }

    /**
     * 測試場上有 4 個槓時有 5 張寶牌指示牌（最大值）。
     */
    @Test
    fun `test dora indicator count with four kan`() {
        val calculator = createCalculator(deadTileCount = 14)

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
        val hand = Hand(
            mutableListOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5))
            ),
            mutableListOf(kanMeld1, kanMeld2, kanMeld3, kanMeld4)
        )
        val player = createPlayer(hand)

        val normalTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..14).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )

        assertEquals(5, context.doraIndicators.size)
    }

    /**
     * 測試未立直時不應包含裏寶牌指示牌。
     */
    @Test
    fun `test ura dora not included without riichi`() {
        val calculator = createCalculator(deadTileCount = 14)

        val hand = createHand(
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
        val player = createPlayer(hand)

        val wanPaiTiles = (1..14).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )

        assertEquals(1, context.doraIndicators.size)
        assertTrue(context.uraDoraIndicators.isEmpty())
    }

    /**
     * 測試立直時應包含裏寶牌指示牌。
     */
    @Test
    fun `test ura dora included with riichi`() {
        val calculator = createCalculator(deadTileCount = 14)

        val hand = createHand(
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
        val riichiTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
        val riichiState = RiichiPlayerState(riichiTile = riichiTile)
        val player = createPlayer(hand, riichiState)

        val wanPaiTiles = (1..14).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )

        assertEquals(1, context.doraIndicators.size)
        assertEquals(1, context.uraDoraIndicators.size)
        assertTrue(context.isRiichi)
    }

    /**
     * 測試 wanPaiCount 為 0 時正常運作。
     */
    @Test
    fun `test dora calculation with zero wanpai count`() {
        val calculator = createCalculator(deadTileCount = 0)

        val hand = createHand(
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
        val player = createPlayer(hand)

        val wallTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 0)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )

        assertTrue(context.doraIndicators.isEmpty())
        assertTrue(context.uraDoraIndicators.isEmpty())
    }

    /**
     * 測試嶺上花的判定。
     */
    @Test
    fun `test rinshan kaihou detection`() {
        val calculator = createCalculator(deadTileCount = 14)

        val kanSourceTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
        val kanMeld = Meld(
            MeldType.OPEN_KAN,
            listOf(
                kanSourceTile,
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
            ),
            sourceTile = kanSourceTile,
            sourceDirection = RelativeDirection.Across
        )
        val hand = Hand(
            mutableListOf(
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 3)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 4)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 6)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 7)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 8)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9)),
                IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
            ),
            mutableListOf(kanMeld)
        )

        val kanTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
        val riichiState = RiichiPlayerState(riichiTile = kanTile)
        val player = createPlayer(hand, riichiState)
        player.recordAction(
            GameAction.Kan(
                type = GameAction.KanType.OPEN_KAN,
                tileId = kanSourceTile.id,
                withTiles = kanMeld.tiles.map { it.id },
            )
        )
        player.recordAction(GameAction.Draw)

        val wallTiles = (1..30).map {
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
                isRobbingKan = false
            )
        )

        assertTrue(context.isRinshanKaihou)
    }

    /**
     * 驗證不同槓數下的寶牌指示器計算邏輯是否正確。
     *
     * 日麻王牌結構（反轉後）：
     * - 索引 0-3：嶺上牌（共 4 張）
     * - 索引 4, 6, 8, 10, 12：寶牌指示牌（上層，共 5 張）
     * - 索引 5, 7, 9, 11, 13：裏寶牌指示牌（下層，共 5 張）
     *
     * 計算公式：`baseIndex = (4 - kanCount) + (i * 2)`
     * - 0 槓：baseIndex = 4 → 寶牌 1 張
     * - 1 槓：baseIndex = 3, 5 → 寶牌 2 張
     * - 2 槓：baseIndex = 2, 4, 6 → 寶牌 3 張
     * - 3 槓：baseIndex = 1, 3, 5, 7 → 寶牌 4 張
     * - 4 槓：baseIndex = 0, 2, 4, 6, 8 → 寶牌 5 張
     */
    @Test
    fun `test dora indicators with different kan counts`() {
        val calculator = createCalculator(deadTileCount = 14)

        val hand = createHand(
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
        val player = createPlayer(hand)

        /**
         * 王牌配置（tileWall.takeLast(14).reversed()）。
         *
         * ## 計算公式
         * `baseIndex = (4 - kanCount) + (i * 2)`，其中 `indicatorCount = (1 + kanCount).coerceAtMost(5)`
         *
         * ## 各位置在不同槓數下的 dora / uraDora
         * | wanPai | 0槓 | 1槓 | 2槓 | 3槓 | 4槓 |
         * | :---: | :---: | :---: | :---: | :---: | :---: |
         * | 0 | - | - | - | - | dora1 |
         * | 1 | - | - | - | dora1 | uraDora1 |
         * | 2 | - | - | dora1 | uraDora1 | dora2 |
         * | 3 | - | dora1 | uraDora1 | dora2 | uraDora2 |
         * | 4 | dora1 | uraDora1 | dora2 | uraDora2 | dora3 |
         * | 5 | uraDora1 | dora2 | uraDora2 | dora3 | uraDora3 |
         * | 6 | dora2 | uraDora2 | dora3 | uraDora3 | dora4 |
         * | 7 | uraDora2 | dora3 | uraDora3 | dora4 | uraDora4 |
         * | 8 | dora3 | uraDora3 | dora4 | uraDora4 | dora5 |
         * | 9 | uraDora3 | dora4 | uraDora4 | dora5 | uraDora5 |
         * | 10 | dora4 | uraDora4 | dora5 | uraDora5 | - |
         * | 11 | uraDora4 | dora5 | uraDora5 | - | - |
         * | 12 | dora5 | uraDora5 | - | - | - |
         * | 13 | uraDora5 | - | - | - | - |
         */
        val wanPaiTiles = listOf(
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9)),  // wanPai[13]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9)),  // wanPai[12]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 3)),  // wanPai[11]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),  // wanPai[10]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),  // wanPai[9]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),  // wanPai[8]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),  // wanPai[7]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),  // wanPai[6]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),  // wanPai[5]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),  // wanPai[4]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),  // wanPai[3]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 3)),  // wanPai[2]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 9)),  // wanPai[1]
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5, isRed = true))  // wanPai[0]
        )
        val tileWall = TileWall(wanPaiTiles.toMutableList())

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )

        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 測試無槓：baseIndex = 4
        val context0Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )
        // 0 槓：baseIndex = 4 → wanPai[4] = 寶牌指示牌 1
        assertEquals(1, context0Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 1), context0Kan.doraIndicators[0]) // wanPai[4]

        // 測試 1 槓
        val player1Kan = createPlayerWithKan(hand.standingTiles.map { IdentifiedTile(it.id, it.tile) }, 1)
        val tableState1Kan = TableState(
            players = listOf(player1Kan),
            tileWall = TileWall(wanPaiTiles.toMutableList()).apply { repeat(1) { this.drawLast() } },
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )
        val context1Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState1Kan,
                player = player1Kan,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )
        // 1 槓：baseIndex = 3, 5
        assertEquals(2, context1Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 1), context1Kan.doraIndicators[0]) // wanPai[3]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 2), context1Kan.doraIndicators[1]) // wanPai[5]

        // 測試 2 槓
        val player2Kan = createPlayerWithKan(hand.standingTiles.map { IdentifiedTile(it.id, it.tile) }, 2)
        val tableState2Kan = TableState(
            players = listOf(player2Kan),
            tileWall = TileWall(wanPaiTiles.toMutableList()).apply { repeat(2) { this.drawLast() } },
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )
        val context2Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState2Kan,
                player = player2Kan,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )
        // 2 槓：baseIndex = 2, 4, 6
        assertEquals(3, context2Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 1), context2Kan.doraIndicators[0]) // wanPai[2]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 2), context2Kan.doraIndicators[1]) // wanPai[4]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 2), context2Kan.doraIndicators[2]) // wanPai[6]

        // 測試 3 槓
        val player3Kan = createPlayerWithKan(hand.standingTiles.map { IdentifiedTile(it.id, it.tile) }, 3)
        val tableState3Kan = TableState(
            players = listOf(player3Kan),
            tileWall = TileWall(wanPaiTiles.toMutableList()).apply { repeat(3) { this.drawLast() } },
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )
        val context3Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState3Kan,
                player = player3Kan,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )
        // 3 槓：baseIndex = 1, 3, 5, 7
        assertEquals(4, context3Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 1), context3Kan.doraIndicators[0]) // wanPai[1]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 2), context3Kan.doraIndicators[1]) // wanPai[3]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 2), context3Kan.doraIndicators[2]) // wanPai[5]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 1), context3Kan.doraIndicators[3]) // wanPai[7]

        // 測試 4 槓
        val player4Kan = createPlayerWithKan(hand.standingTiles.map { IdentifiedTile(it.id, it.tile) }, 4)
        val tableState4Kan = TableState(
            players = listOf(player4Kan),
            tileWall = TileWall(wanPaiTiles.toMutableList()).apply { repeat(4) { this.drawLast() } },
            config = FakeRiichiRuleConfig(deadTileCount = 14)
        )
        val context4Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState4Kan,
                player = player4Kan,
                incomingTile = incomingTile,
                isTsumo = true
            )
        )
        // 4 槓：baseIndex = 0, 2, 4, 6, 8
        assertEquals(5, context4Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 1), context4Kan.doraIndicators[0]) // wanPai[0]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 2), context4Kan.doraIndicators[1]) // wanPai[2]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 2), context4Kan.doraIndicators[2]) // wanPai[4]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 1), context4Kan.doraIndicators[3]) // wanPai[6]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 9), context4Kan.doraIndicators[4]) // wanPai[8]
    }
}
