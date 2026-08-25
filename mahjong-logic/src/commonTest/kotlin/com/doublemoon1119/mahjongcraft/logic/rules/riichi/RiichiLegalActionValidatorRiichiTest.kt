package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
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

    private val validator = RiichiLegalActionValidator(
        shantenCalculator = RiichiShantenCalculator(),
        handValueCalculator = RiichiHandValueCalculator(),
        contextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig()),
    )

    /** 牌山還有牌可摸——立直/暗槓測試預設用這個，避免被新加的「牌山剩餘不足」限制誤擋。 */
    private val nonEmptyWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))))

    /**
     * 測試可執行立直動作之情況（點數充足）。
     *
     * 當玩家聽牌、門前清且點數 >= 1000 時，應可執行立直動作。
     */
    @Test
    fun `test can riichi with sufficient score`() {
        // 準備
        // 手牌已聽牌
        val playerHand = FakeHandFactory.create(
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
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        ).copy(score = 1000) // 點數剛好 1000
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = null,
        )

        // 驗證
        assertTrue(actions.any { it == RIICHI_GAME_ACTION })
    }

    /**
     * 測試可執行立直動作之情況（點數超過 1000）。
     *
     * 當玩家點數 > 1000 時，應可執行立直動作。
     */
    @Test
    fun `test can riichi with high score`() {
        // 準備
        val playerHand = FakeHandFactory.create(
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
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        ).copy(score = 2500) // 點數超過 1000
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = null,
        )

        // 驗證
        assertTrue(actions.any { it == RIICHI_GAME_ACTION })
    }

    /**
     * 測試不可執行立直動作之情況（牌山剩餘張數不足以讓自己再摸一次）。
     *
     * 四人局裡，牌山剩餘張數必須 >= 玩家人數（4），確保這位宣告者輪到自己之前牌山不會先摸盡；
     * 剩餘 3 張（< 4）時即使聽牌、門前清、點數充足，也不可立直。
     */
    @Test
    fun `test cannot riichi when wall remaining is below player count`() {
        // 準備
        val playerHand = FakeHandFactory.create(
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
        val player = FakeMahjongPlayerFactory.create(hand = playerHand).copy(score = 2500)
        val otherPlayers = List(3) { FakeMahjongPlayerFactory.create() }
        val tableState = FakeTableStateFactory.create(
            players = listOf(player) + otherPlayers,
            tileWall = TileWall(List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)) }),
        )

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = null,
        )

        // 驗證
        assertFalse(actions.any { it == RIICHI_GAME_ACTION })
    }

    /**
     * 測試可執行立直動作之情況（牌山剩餘張數恰好等於玩家人數，邊界值）。
     */
    @Test
    fun `test can riichi when wall remaining equals player count`() {
        // 準備
        val playerHand = FakeHandFactory.create(
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
        val player = FakeMahjongPlayerFactory.create(hand = playerHand).copy(score = 2500)
        val otherPlayers = List(3) { FakeMahjongPlayerFactory.create() }
        val tableState = FakeTableStateFactory.create(
            players = listOf(player) + otherPlayers,
            tileWall = TileWall(List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)) }),
        )

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = null,
        )

        // 驗證
        assertTrue(actions.any { it == RIICHI_GAME_ACTION })
    }

    /**
     * 測試不可執行立直動作之情況（點數不足）。
     *
     * 當玩家點數 < 1000 時，不可執行立直動作。
     */
    @Test
    fun `test cannot riichi with insufficient score`() {
        // 準備
        val playerHand = FakeHandFactory.create(
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
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        ).copy(score = 500) // 點數不足 1000
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = null,
        )

        // 驗證
        assertFalse(actions.any { it == RIICHI_GAME_ACTION })
    }

    /**
     * 測試不可執行立直動作之情況（點數為 0）。
     *
     * 當玩家點數為 0 時，不可執行立直動作。
     */
    @Test
    fun `test cannot riichi with zero score`() {
        // 準備
        val playerHand = FakeHandFactory.create(
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
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        ).copy(score = 0) // 點數為 0
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = null,
        )

        // 驗證
        assertFalse(actions.any { it == RIICHI_GAME_ACTION })
    }

    /**
     * 測試立直後暗槓 - 牌型改變則不可暗槓。
     *
     * 立直後暗槓，必須暗槓前後的牌型相同才能暗槓。
     */
    @Test
    fun `test closed kan not allowed after riichi when tenpai changes`() {
        // 手牌： 111 餅 + 23 餅 + 444 餅 + 222 條 + 56 萬 (聽牌中)
        // 聽牌： 兩面聽 4 萬、7 萬
        // 摸到： 1 餅
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
            ),
        )

        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
            ),
        )

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )

        // 摸到 1 餅（暗槓後改變聽牌）
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：暗槓 1 餅會改變牌型，不可暗槓
        val closedKanActions = actions.filterIsInstance<GameAction.Kan>()
        val hasClosedKan = closedKanActions.any { it.type == GameAction.KanType.CLOSED_KAN }
        assertFalse(hasClosedKan)
    }

    /**
     * 測試立直後暗槓 - 聽牌不變則可暗槓。
     *
     * 立直後暗槓，若暗槓前後的聽牌列表完全相同才能暗槓。
     */
    @Test
    fun `test closed kan allowed after riichi when tenpai unchanged`() {
        // 手牌： 111 萬 + 234 餅 + 567 索 + 東東 + 89 萬
        // 聽牌： 7 萬
        // 摸到： 1 萬
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )

        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
            ),
        )

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )

        // 摸到 1 萬（暗槓後聽牌不變，仍只聽 7 萬）
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：暗槓 1 萬不影響聽牌（仍只聽 7 萬），可暗槓
        val closedKanActions = actions.filterIsInstance<GameAction.Kan>()
        val hasClosedKan = closedKanActions.any { it.type == GameAction.KanType.CLOSED_KAN }
        assertTrue(hasClosedKan)
    }
}
