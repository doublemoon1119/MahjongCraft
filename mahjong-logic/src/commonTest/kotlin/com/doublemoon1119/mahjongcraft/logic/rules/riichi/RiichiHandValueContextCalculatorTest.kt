package com.doublemoon1119.mahjongcraft.logic.rules.riichi
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
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

    private fun createCalculator(): RiichiHandValueContextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig())

    private fun createPlayer(hand: Hand, riichiState: RiichiPlayerState? = null): MahjongPlayer = FakeMahjongPlayerFactory.create(
        hand = hand,
        playerRuleState = riichiState ?: RiichiPlayerState(),
    )

    private fun createPlayerWithKan(standingTiles: List<IdentifiedTile>, kanCount: Int): MahjongPlayer {
        val melds = mutableListOf<Meld>()
        for (i in 0 until kanCount) {
            val tileValue = (i % 9) + 1
            val kanMeld = Meld(
                MeldType.OPEN_KAN,
                listOf(
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, tileValue)),
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, tileValue)),
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, tileValue)),
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, tileValue)),
                ),
                sourceDirection = RelativeDirection.Across,
            )
            melds.add(kanMeld)
        }
        val hand = Hand(
            standingTiles.toMutableList(),
            melds,
        )
        return FakeMahjongPlayerFactory.create(
            hand = hand,
            playerRuleState = RiichiPlayerState(),
        )
    }

    /**
     * 測試海底撈月：當牌山剩餘牌數等於王牌數時，自摸應設定 isLastDraw 為 true。
     */
    @Test
    fun `test tsumo at last draw sets isLastDraw true`() {
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val player = createPlayer(hand)

        val wanPaiTiles = (1..14).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )

        assertTrue(context.isLastDraw)
        assertFalse(context.isLastDiscard)
    }

    /**
     * 測試河底撈魚：當牌山剩餘牌數等於王牌數時，榮和應設定 isLastDiscard 為 true。
     */
    @Test
    fun `test ron at last discard sets isLastDiscard true`() {
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val player = createPlayer(hand)

        val wanPaiTiles = (1..14).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = false,
            ),
        )

        assertFalse(context.isLastDraw)
        assertTrue(context.isLastDiscard)
    }

    /**
     * 測試海底撈月與河底撈魚標記不會在牌山還有剩餘時觸發。
     */
    @Test
    fun `test last draw discard flags not set when tiles remain`() {
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val player = createPlayer(hand)

        val wallTiles = (1..15).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = false,
            ),
        )

        assertFalse(context.isLastDraw)
        assertFalse(context.isLastDiscard)
    }

    /**
     * 測試無槓時有 1 張寶牌指示牌。
     */
    @Test
    fun `test dora indicator count with no kan`() {
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val player = createPlayer(hand)

        val normalTiles = (1..30).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..14).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )

        assertEquals(1, context.doraIndicators.size)
    }

    /**
     * 測試場上有 1 個槓時有 2 張寶牌指示牌。
     */
    @Test
    fun `test dora indicator count with one kan`() {
        val calculator = createCalculator()

        val kanMeld = Meld(
            MeldType.OPEN_KAN,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
            ),
            sourceDirection = RelativeDirection.Across,
        )
        val hand = Hand(
            mutableListOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 7)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 8)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
            ),
            mutableListOf(kanMeld),
        )
        val player = createPlayer(hand)

        val normalTiles = (1..30).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..14).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )

        assertEquals(2, context.doraIndicators.size)
    }

    /**
     * 測試場上有 4 個槓時有 5 張寶牌指示牌（最大值）。
     */
    @Test
    fun `test dora indicator count with four kan`() {
        val calculator = createCalculator()

        val kanMeld1 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
            ),
            sourceDirection = RelativeDirection.Across,
        )
        val kanMeld2 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2)),
            ),
            sourceDirection = RelativeDirection.Across,
        )
        val kanMeld3 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3)),
            ),
            sourceDirection = RelativeDirection.Across,
        )
        val kanMeld4 = Meld(
            MeldType.OPEN_KAN,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4)),
            ),
            sourceDirection = RelativeDirection.Across,
        )
        val hand = Hand(
            mutableListOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)),
            ),
            mutableListOf(kanMeld1, kanMeld2, kanMeld3, kanMeld4),
        )
        val player = createPlayer(hand)

        val normalTiles = (1..30).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val wanPaiTiles = (1..14).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1))
        }
        val allTiles = normalTiles + wanPaiTiles
        val tileWall = TileWall(allTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )

        assertEquals(5, context.doraIndicators.size)
    }

    /**
     * 測試未立直時不應包含裏寶牌指示牌。
     */
    @Test
    fun `test ura dora not included without riichi`() {
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val player = createPlayer(hand)

        val wanPaiTiles = (1..14).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )

        assertEquals(1, context.doraIndicators.size)
        assertTrue(context.uraDoraIndicators.isEmpty())
    }

    /**
     * 測試立直時應包含裏寶牌指示牌。
     */
    @Test
    fun `test ura dora included with riichi`() {
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val riichiTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val riichiState = RiichiPlayerState(riichiTile = riichiTile)
        val player = createPlayer(hand, riichiState)

        val wanPaiTiles = (1..14).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wanPaiTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
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
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val player = createPlayer(hand)
        val tileWall = TileWall(emptyList())

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )

        assertTrue(context.doraIndicators.isEmpty())
        assertTrue(context.uraDoraIndicators.isEmpty())
    }

    /**
     * 測試嶺上花的判定。
     */
    @Test
    fun `test rinshan kaihou detection`() {
        val calculator = createCalculator()

        val kanSourceTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val kanMeld = Meld(
            MeldType.OPEN_KAN,
            listOf(
                kanSourceTile,
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
            ),
            sourceTile = kanSourceTile,
            sourceDirection = RelativeDirection.Across,
        )
        val hand = Hand(
            mutableListOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 7)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 8)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
            ),
            mutableListOf(kanMeld),
        )

        val kanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val riichiState = RiichiPlayerState(riichiTile = kanTile)
        var player = createPlayer(hand, riichiState)
        player = player.recordAction(
            GameAction.Kan(
                type = GameAction.KanType.OPEN_KAN,
                tileId = kanSourceTile.id,
                withTiles = kanMeld.tiles.map { it.id },
            ),
        )
        player = player.recordAction(GameAction.Draw)

        val wallTiles = (1..30).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (it % 9) + 1))
        }
        val tileWall = TileWall(wallTiles)

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = tileWall,
            config = RiichiRuleConfig(),
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1))

        val context = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
                isRobbingKan = false,
            ),
        )

        assertTrue(context.isRinshanKaihou)
    }

    /**
     * 驗證不同槓數下的寶牌指示器計算邏輯是否正確。
     *
     * 資料來源是 [TableState.initialDeadWall]（固定 14 張、順序穩定），索引在整個對局期間都不會變動，
     * 見 [RiichiDynamicState.getDoraIndicators] KDoc。
     *
     * 計算公式：`baseIndex = 4 + (i * 2)`，其中 `indicatorCount = (1 + kanCount).coerceAtMost(5)`
     * - 0 槓：baseIndex = 4 → 寶牌 1 張
     * - 1 槓：baseIndex = 4, 6 → 寶牌 2 張
     * - 2 槓：baseIndex = 4, 6, 8 → 寶牌 3 張
     * - 3 槓：baseIndex = 4, 6, 8, 10 → 寶牌 4 張
     * - 4 槓：baseIndex = 4, 6, 8, 10, 12 → 寶牌 5 張
     */
    @Test
    fun `test dora indicators with different kan counts`() {
        val calculator = createCalculator()

        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val player = createPlayer(hand)

        /**
         * 王牌配置，依 [TableState.initialDeadWall] 的索引順序（0~13）直接排列，每個位置都用不同牌面
         * 以便驗證索引對應是否正確。
         *
         * ## 各位置在不同槓數下的 dora / uraDora
         * | wanPai | 0槓 | 1槓 | 2槓 | 3槓 | 4槓 |
         * | :---: | :---: | :---: | :---: | :---: | :---: |
         * | 4 | dora1 | dora1 | dora1 | dora1 | dora1 |
         * | 5 | uraDora1 | uraDora1 | uraDora1 | uraDora1 | uraDora1 |
         * | 6 | - | dora2 | dora2 | dora2 | dora2 |
         * | 7 | - | uraDora2 | uraDora2 | uraDora2 | uraDora2 |
         * | 8 | - | - | dora3 | dora3 | dora3 |
         * | 9 | - | - | uraDora3 | uraDora3 | uraDora3 |
         * | 10 | - | - | - | dora4 | dora4 |
         * | 11 | - | - | - | uraDora4 | uraDora4 |
         * | 12 | - | - | - | - | dora5 |
         * | 13 | - | - | - | - | uraDora5 |
         */
        val wanPaiTiles = listOf(
            FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character)), // wanPai[0]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)), // wanPai[1]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3)), // wanPai[2]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)), // wanPai[3]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)), // wanPai[4]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6)), // wanPai[5]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 7)), // wanPai[6]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 8)), // wanPai[7]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)), // wanPai[8]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)), // wanPai[9]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2)), // wanPai[10]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3)), // wanPai[11]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 4)), // wanPai[12]
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)), // wanPai[13]
        )

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = TileWall(wanPaiTiles),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 測試無槓：baseIndex = 4
        val context0Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )
        // 0 槓：baseIndex = 4 → wanPai[4] = 寶牌指示牌 1
        assertEquals(1, context0Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 5), context0Kan.doraIndicators[0]) // wanPai[4]

        // 測試 1 槓
        val player1Kan = createPlayerWithKan(
            hand.standingTiles.map { FakeIdentifiedTileFactory.create(id = it.id, tile = it.tile) },
            1,
        )
        val tableState1Kan = FakeTableStateFactory.create(
            players = listOf(player1Kan),
            tileWall = TileWall(wanPaiTiles),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )
        val context1Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState1Kan,
                player = player1Kan,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )
        // 1 槓：baseIndex = 4, 6
        assertEquals(2, context1Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 5), context1Kan.doraIndicators[0]) // wanPai[4]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 7), context1Kan.doraIndicators[1]) // wanPai[6]

        // 測試 2 槓
        val player2Kan = createPlayerWithKan(
            hand.standingTiles.map { FakeIdentifiedTileFactory.create(id = it.id, tile = it.tile) },
            2,
        )
        val tableState2Kan = FakeTableStateFactory.create(
            players = listOf(player2Kan),
            tileWall = TileWall(wanPaiTiles),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )
        val context2Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState2Kan,
                player = player2Kan,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )
        // 2 槓：baseIndex = 4, 6, 8
        assertEquals(3, context2Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 5), context2Kan.doraIndicators[0]) // wanPai[4]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 7), context2Kan.doraIndicators[1]) // wanPai[6]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 9), context2Kan.doraIndicators[2]) // wanPai[8]

        // 測試 3 槓
        val player3Kan = createPlayerWithKan(
            hand.standingTiles.map { FakeIdentifiedTileFactory.create(id = it.id, tile = it.tile) },
            3,
        )
        val tableState3Kan = FakeTableStateFactory.create(
            players = listOf(player3Kan),
            tileWall = TileWall(wanPaiTiles),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )
        val context3Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState3Kan,
                player = player3Kan,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )
        // 3 槓：baseIndex = 4, 6, 8, 10
        assertEquals(4, context3Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 5), context3Kan.doraIndicators[0]) // wanPai[4]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 7), context3Kan.doraIndicators[1]) // wanPai[6]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 9), context3Kan.doraIndicators[2]) // wanPai[8]
        assertEquals(Tile.Numeric(Tile.Suit.Dot, 2), context3Kan.doraIndicators[3]) // wanPai[10]

        // 測試 4 槓
        val player4Kan = createPlayerWithKan(
            hand.standingTiles.map { FakeIdentifiedTileFactory.create(id = it.id, tile = it.tile) },
            4,
        )
        val tableState4Kan = FakeTableStateFactory.create(
            players = listOf(player4Kan),
            tileWall = TileWall(wanPaiTiles),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            initialDeadWall = wanPaiTiles,
        )
        val context4Kan = calculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState4Kan,
                player = player4Kan,
                incomingTile = incomingTile,
                isTsumo = true,
            ),
        )
        // 4 槓：baseIndex = 4, 6, 8, 10, 12
        assertEquals(5, context4Kan.doraIndicators.size)
        assertEquals(Tile.Numeric(Tile.Suit.Character, 5), context4Kan.doraIndicators[0]) // wanPai[4]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 7), context4Kan.doraIndicators[1]) // wanPai[6]
        assertEquals(Tile.Numeric(Tile.Suit.Character, 9), context4Kan.doraIndicators[2]) // wanPai[8]
        assertEquals(Tile.Numeric(Tile.Suit.Dot, 2), context4Kan.doraIndicators[3]) // wanPai[10]
        assertEquals(Tile.Numeric(Tile.Suit.Dot, 4), context4Kan.doraIndicators[4]) // wanPai[12]
    }
}
