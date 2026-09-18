package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
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
        contextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig()),
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
                RiichiTileTypes.redFive(Tile.Suit.Character), // 赤5萬
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        // 模擬玩家已經打了赤5萬（振聽）
        val fakeDiscardPile = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character)),
            ),
        )
        val riichiState = RiichiPlayerState()
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = fakeDiscardPile,
            playerRuleState = riichiState,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        // 他家打普通5萬
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
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
                Tile.Numeric(Tile.Suit.Character, 5), // 普通5萬
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        // 模擬玩家已經打了普通5萬（振聽）
        val fakeDiscardPile = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)),
            ),
        )
        val riichiState = RiichiPlayerState()
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = fakeDiscardPile,
            playerRuleState = riichiState,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        // 他家打赤5萬
        val incomingTile = FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證 - 因為打過普通5萬（視為同張牌），所以不可榮和
        assertFalse(actions.any { it is GameAction.Ron })
    }

    /**
     * 放過碰牌後，同巡再次出現同一張牌仍然可以碰：日麻沒有過水碰的限制。
     *
     * 放過清單只影響榮和（同巡振聽），因此同一個情境下一併驗證這張牌不能榮和，兩種限制不會混為一談。
     */
    @Test
    fun `test can pon after passing in same round`() {
        // 準備
        // 手牌聽 5 萬／9 萬（雙碰），且手上有兩張 5 萬可以碰；清一色確保榮和本身有役，
        // 「不能榮和」才是振聽造成的，不是無役
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )

        var player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            playerRuleState = RiichiPlayerState(),
        )
        // 建立已放過5萬的狀態
        player = player.addPassedTile(Tile.Numeric(Tile.Suit.Character, 5))

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        // 他家打5萬
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證 - 碰不受放過清單限制
        assertTrue(actions.any { it is GameAction.Pon })
        // 驗證 - 同一張牌仍在放過清單內，不能榮和
        assertFalse(actions.any { it is GameAction.Ron })
    }

    /**
     * 放過普通 5 萬後，他家打赤 5 萬仍然可以碰。
     *
     * 赤五與普通五視為同一張牌的判斷照舊，但那只影響榮和；碰不受放過清單限制。
     */
    @Test
    fun `test can pon after passing with red dora`() {
        // 準備
        // 手牌聽 5 萬／9 萬（雙碰），且手上有兩張 5 萬可以碰；清一色確保榮和本身有役，
        // 「不能榮和」才是振聽造成的，不是無役
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )

        var player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            playerRuleState = RiichiPlayerState(),
        )
        // 放過普通5萬
        player = player.addPassedTile(Tile.Numeric(Tile.Suit.Character, 5))

        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        // 他家打赤5萬
        val incomingTile = FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證 - 碰不受放過清單限制
        assertTrue(actions.any { it is GameAction.Pon })
        // 驗證 - 赤五與普通五視為同一張牌，仍不能榮和
        assertFalse(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試立直後永久振聽：即使 `passedTilesInRound` 已經被清空（模擬下一次摸牌後的狀態）、且這張進來
     * 的牌也不在牌河裡（單靠既有的 [RiichiPlayerState.getFuritenTiles] 判斷不會視為振聽），只要
     * [RiichiPlayerState.isPermanentlyFuriten] 為 `true`，該局就應該一路不能榮和。
     */
    @Test
    fun `test cannot ron when permanently furiten after riichi even without matching discard or passed tile`() {
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
        // 牌河跟 passedTilesInRound 都是乾淨的——沒有永久旗標的話，這張榮和牌本來應該合法。
        val riichiState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isPermanentlyFuriten = true)
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = FakeDiscardPile(),
            playerRuleState = riichiState,
        )
        val tableState = FakeTableStateFactory.create(players = listOf(player))
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        assertFalse(actions.any { it is GameAction.Ron })
    }

    /** 兩面聽的其中一張在自己牌河時，另一張和牌張同樣不能榮和（捨牌振聽）。 */
    @Test
    fun `cannot ron on any wait when another wait is in own discards`() {
        val player = twoSidedWaitPlayer(discardPile = discardPileOf(Tile.Numeric(Tile.Suit.Character, 1)))

        assertFalse(canRon(player, Tile.Numeric(Tile.Suit.Character, 4)), "4m should be furiten while 1m is in own discards")
        assertFalse(canRon(player, Tile.Numeric(Tile.Suit.Character, 1)), "1m should be furiten while it is in own discards")
    }

    /** 牌河沒有任何和牌張時，兩張和牌張都能榮和。 */
    @Test
    fun `can ron on both waits when no wait is in own discards`() {
        val player = twoSidedWaitPlayer(discardPile = discardPileOf(Tile.Honor.North))

        assertTrue(canRon(player, Tile.Numeric(Tile.Suit.Character, 1)), "1m should be a legal ron")
        assertTrue(canRon(player, Tile.Numeric(Tile.Suit.Character, 4)), "4m should be a legal ron")
    }

    /** 被他家鳴走的自己捨牌仍算在牌河裡，同樣造成捨牌振聽。 */
    @Test
    fun `a discard claimed by another player still causes furiten`() {
        val player = twoSidedWaitPlayer(discardPile = discardPileOf(Tile.Numeric(Tile.Suit.Character, 1)).takeLast())

        assertFalse(canRon(player, Tile.Numeric(Tile.Suit.Character, 4)), "A claimed 1m discard should still cause furiten")
    }

    /** 本巡放過其中一張和牌張時，另一張和牌張同樣不能榮和（同巡振聽）。 */
    @Test
    fun `cannot ron on any wait after passing another wait in the same go-around`() {
        val player = twoSidedWaitPlayer(discardPile = FakeDiscardPile())
            .addPassedTile(Tile.Numeric(Tile.Suit.Character, 1))

        assertFalse(canRon(player, Tile.Numeric(Tile.Suit.Character, 4)), "4m should be furiten after passing 1m this go-around")
    }

    /** 捨牌振聽時仍可自摸。 */
    @Test
    fun `can still tsumo while in discard furiten`() {
        val player = twoSidedWaitPlayer(discardPile = discardPileOf(Tile.Numeric(Tile.Suit.Character, 1)))
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))

        val actions = validator.getLegalActions(
            tableState = FakeTableStateFactory.create(players = listOf(player)),
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = drawnTile,
        )

        assertTrue(actions.any { it is GameAction.Tsumo }, "Tsumo should stay legal while in discard furiten")
    }

    /** 23萬＋456筒＋789筒＋中中中＋22條，聽 1-4 萬，門前有役牌。 */
    private fun twoSidedWaitPlayer(discardPile: FakeDiscardPile) = FakeMahjongPlayerFactory.create(
        hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
            ),
        ),
        discardPile = discardPile,
        playerRuleState = RiichiPlayerState(),
    )

    private fun discardPileOf(tile: Tile) = FakeDiscardPile().discard(FakeDiscardPile.FakeEntry(FakeIdentifiedTileFactory.create(tile)))

    private fun canRon(player: MahjongPlayer, tile: Tile): Boolean {
        val incomingTile = FakeIdentifiedTileFactory.create(tile)
        return validator.getLegalActions(
            tableState = FakeTableStateFactory.create(players = listOf(player)),
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        ).any { it is GameAction.Ron }
    }
}
