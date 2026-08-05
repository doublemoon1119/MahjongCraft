package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 立直麻將合法動作驗證器之振聽測試。
 *
 * 測試內容涵蓋振聽（Furiten）狀態下的榮和限制。
 * 振聽是指玩家聽牌後打了胡牌張，他家再打同張牌時不可榮和。
 *
 * @see RiichiLegalActionValidator
 */
class RiichiLegalActionValidatorFuritenTest {

    private val validator = RiichiLegalActionValidator(
        shantenCalculator = RiichiShantenCalculator(),
        handValueCalculator = RiichiHandValueCalculator(),
        contextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig())
    )

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
        val playerHand = FakeHandFactory.create(
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
        val fakeDiscardPile = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = true))
            )
        )
        val riichiState = RiichiPlayerState()
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = fakeDiscardPile,
            playerRuleState = riichiState
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player)
        )
        // 他家打普通5萬
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = false))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
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
        val playerHand = FakeHandFactory.create(
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
        val fakeDiscardPile = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = false))
            )
        )
        val riichiState = RiichiPlayerState()
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = fakeDiscardPile,
            playerRuleState = riichiState
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player)
        )
        // 他家打赤5萬
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = true))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證 - 因為打過普通5萬（視為同張牌），所以不可榮和
        assertFalse(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試過水碰之情況：放過碰牌後，同巡再次出現相同機會不可碰。
     *
     * 玩家在巡迴中放過了碰5萬的機會，後續再出現5萬時不可碰。
     */
    @Test
    fun `test cannot pon after passing in same round`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5)
            )
        )

        var player = FakeMahjongPlayerFactory.create(
            hand = playerHand
        )
        // 建立已放過5萬的狀態
        player = player.addPassedTile(Tile.Numeric(Tile.Suit.Character, 5))

        val tableState = FakeTableStateFactory.create(
            players = listOf(player)
        )
        // 他家打5萬
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證 - 已經放過，不可碰
        assertFalse(actions.any { it is GameAction.Pon })
    }

    /**
     * 測試過水碰之情況：摸牌後清除過水碰記錄。
     *
     * 玩家放過碰牌機會後，下次摸牌時應清除記錄，可再次碰牌。
     * 注意：此測試需要在 Use Case 層實作清除邏輯後才能正確運作。
     */
    @Test
    fun `test can pon after clearing passed tiles manually`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5)
            )
        )

        var player = FakeMahjongPlayerFactory.create(
            hand = playerHand
        )
        // 建立已放過5萬的狀態
        player = player.addPassedTile(Tile.Numeric(Tile.Suit.Character, 5))

        val tableState = FakeTableStateFactory.create(
            players = listOf(player)
        )

        // 手動清除記錄（模擬 Use Case 層的行為）
        player = player.clearPassedTiles()

        // 再測試他家打5萬
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證 - 清除記錄後，可以碰
        assertTrue(actions.any { it is GameAction.Pon })
    }

    /**
     * 測試過水碰之情況：過水碰不受赤寶牌影響。
     *
     * 玩家放過普通5萬，他家打赤5萬時也不可碰（視為同張牌）。
     */
    @Test
    fun `test cannot pon after passing with red dora`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5)
            )
        )

        var player = FakeMahjongPlayerFactory.create(
            hand = playerHand
        )
        // 放過普通5萬
        player = player.addPassedTile(Tile.Numeric(Tile.Suit.Character, 5, isRed = false))

        val tableState = FakeTableStateFactory.create(
            players = listOf(player)
        )
        // 他家打赤5萬
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = true))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile
        )

        // 驗證 - 放過普通5萬，赤5萬也不能碰
        assertFalse(actions.any { it is GameAction.Pon })
    }
}
