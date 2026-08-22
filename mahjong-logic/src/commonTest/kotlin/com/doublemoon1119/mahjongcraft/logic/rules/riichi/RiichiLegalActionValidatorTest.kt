package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
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

    private val validator = RiichiLegalActionValidator(
        shantenCalculator = RiichiShantenCalculator(),
        handValueCalculator = RiichiHandValueCalculator(),
        contextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig()),
    )

    /**
     * 測試可執行碰牌動作之情況。
     *
     * 當手牌中有兩張與打出的牌相同時，應可執行碰牌動作。
     */
    @Test
    fun `test can pon`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
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
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Left,
            incomingTile = incomingTile,
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
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
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
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
            ),
            sourceDirection = RelativeDirection.Left,
        )
        val playerHand = Hand(melds = mutableListOf(ponMeld))
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
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
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.CLOSED_KAN })
    }

    /**
     * 測試全場槓子數已達上限（4 次）時不可再暗槓——即使這 4 次全部由同一位玩家達成（
     * `resolveSuukanNagare` 因此不會判定四槓散了，該玩家可能正在嘗試四槓子役滿），全場槓子總數的
     * 硬上限仍然是 4 次，這位玩家也不能繼續槓第 5 次。
     */
    @Test
    fun `test cannot closed kan when the table already has 4 kans by a single player`() {
        // 準備：這位玩家已經有 4 組暗槓副露（湊到全場上限），手牌另外湊了一組可以暗槓的第 5 組
        val existingKans = (1..4).map { value ->
            Meld(
                MeldType.CLOSED_KAN,
                List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, value)) },
                sourceDirection = RelativeDirection.Self,
            )
        }
        val playerHand = Hand(
            tiles = listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
            ).map { FakeIdentifiedTileFactory.create(it) },
            melds = existingKans,
        )
        val player = FakeMahjongPlayerFactory.create(hand = playerHand)
        val tableState = FakeTableStateFactory.create(players = listOf(player))
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：即使 3 張同種牌 + 摸到第 4 張的條件成立，全場槓子數已達上限，不應提供暗槓選項
        assertFalse(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.CLOSED_KAN })
    }

    /**
     * 測試全場槓子數已達上限（4 次，皆由另一位玩家達成）時，其他玩家也不能明槓——上限是全場共用的
     * 硬上限，不是「每位玩家各自 4 次」，即使這位玩家自己一次槓都還沒槓過。
     */
    @Test
    fun `test cannot open kan for a different player when someone else already has 4 kans`() {
        // 準備：另一位玩家已經獨得全場 4 組暗槓副露
        val otherPlayerKans = (1..4).map { value ->
            Meld(
                MeldType.CLOSED_KAN,
                List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, value)) },
                sourceDirection = RelativeDirection.Self,
            )
        }
        val otherPlayer = FakeMahjongPlayerFactory.create(hand = Hand(melds = otherPlayerKans))
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(hand = playerHand)
        val tableState = FakeTableStateFactory.create(players = listOf(otherPlayer, player))
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證：即使這位玩家自己手牌條件符合明槓，全場槓子數已達上限，不應提供明槓選項
        assertFalse(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.OPEN_KAN })
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
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
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
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
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
        val playerHand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 7),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 7))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
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
        val playerHand = FakeHandFactory.create(
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
            ),
        )
        val ponMeld = Meld(
            MeldType.PON,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6)),
            ),
            sourceDirection = RelativeDirection.Left,
        )
        playerHand.call(ponMeld.type, ponMeld.tiles, ponMeld.sourceTile, ponMeld.sourceDirection)
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 7))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Discard(incomingTile.id),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證
        assertFalse(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試最低胡牌番數限制 - 番數不足無法自摸。
     *
     * 當 minimumWinConstraint = 2 時，手牌番數為 1 則不可自摸。
     */
    @Test
    fun `test tsumo not allowed when han is insufficient`() {
        // 準備：手牌為 1 番 (門前清自摸) 的牌型
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.West,
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = FakeDiscardPile().discard(
                entry = FakeDiscardPile.FakeEntry(
                    tile = FakeIdentifiedTileFactory.create(Tile.Honor.East),
                    isTaken = false,
                ),
            ),
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(minimumWinConstraint = 2),
        )

        // 摸到胡牌張
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Honor.West)

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：不可自摸（番數不足）
        assertFalse(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試最低胡牌番數限制 - 赤寶牌不能拿來湊最低番數。
     *
     * 手牌結構跟「番數不足無法自摸」完全相同（門前清自摸 1 番），只把 456p 裡的普通 5 筒換成赤 5 筒，
     * 讓 `result.totalHan` 疊加赤寶牌後變成 2 番——如果直接拿 `totalHan` 去跟 `minimumWinConstraint`
     * 比較會誤判為合法，但赤寶牌不該計入這個門檻，役種本身仍然只有 1 番，所以應該跟原本一樣不可自摸。
     */
    @Test
    fun `test tsumo not allowed when han is insufficient even with aka dora`() {
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 4),
                RiichiTileTypes.redFive(Tile.Suit.Dot), // 赤5筒，取代普通5筒
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.West,
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = FakeDiscardPile().discard(
                entry = FakeDiscardPile.FakeEntry(
                    tile = FakeIdentifiedTileFactory.create(Tile.Honor.East),
                    isTaken = false,
                ),
            ),
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(minimumWinConstraint = 2),
        )

        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Honor.West)

        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        assertFalse(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試最低胡牌番數限制 - 番數足夠可以自摸。
     *
     * 當 minimumWinConstraint = 1 時，使用役滿牌型確保通過。
     */
    @Test
    fun `test tsumo allowed when han is sufficient`() {
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
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
            prevalentWind = Wind.EAST,
        )

        // 摸到胡牌張
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：可以自摸（役滿）
        assertTrue(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試最低胡牌番數限制 - 役滿必定可以胡牌。
     *
     * 當 minimumWinConstraint = 13 時，役滿仍可自摸。
     */
    @Test
    fun `test yakuman always allowed regardless of minimum constraint`() {
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
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
            prevalentWind = Wind.EAST,
        )

        // 摸到胡牌張
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：可以自摸（役滿不受最低番數限制）
        assertTrue(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試可執行九種九牌和局動作之情況。
     *
     * 當滿足以下條件時，應可執行九種九牌和局：
     * - 第一巡（場上無鳴牌、每人最多打 1 張牌、自己未打牌）
     * - 持有的么九牌總數達 9 種以上
     */
    @Test
    fun `test can kyuushu kyuuhai`() {
        // 準備：持有九種么九牌（東南西北發中白 + 19m + 19s + 19p = 13種取9種）
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
            ),
        )
        val discardPile = FakeDiscardPile()
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = discardPile,
        )
        // 建立其他玩家（每人只打1張以滿足第一巡條件）
        val discardPile1 = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
            ),
        )
        val otherPlayer1 = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = FakeHandFactory.create(listOf(Tile.Numeric(Tile.Suit.Bamboo, 1))),
            discardPile = discardPile1,
        )
        val discardPile2 = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2)),
            ),
        )
        val otherPlayer2 = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.WEST,
            hand = FakeHandFactory.create(listOf(Tile.Numeric(Tile.Suit.Bamboo, 2))),
            discardPile = discardPile2,
        )
        val discardPile3 = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3)),
            ),
        )
        val otherPlayer3 = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.NORTH,
            hand = FakeHandFactory.create(listOf(Tile.Numeric(Tile.Suit.Bamboo, 3))),
            discardPile = discardPile3,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player, otherPlayer1, otherPlayer2, otherPlayer3),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：可執行九種九牌
        assertTrue(actions.any { it is GameAction.ExhaustiveDraw })
    }

    /**
     * 測試不符合九種九牌條件時不可執行和局動作之情況。
     *
     * 當持有的么九牌不足9種時，不可執行九種九牌和局。
     */
    @Test
    fun `test cannot kyuushu kyuuhai with insufficient tile types`() {
        // 準備：只持有3種么九牌
        val playerHand = FakeHandFactory.create(
            listOf(
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
                Tile.Numeric(Tile.Suit.Dot, 4),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            discardPile = FakeDiscardPile(),
        )
        val discardPile1 = FakeDiscardPile().discard(
            FakeDiscardPile.FakeEntry(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
            ),
        )
        val otherPlayer1 = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = FakeHandFactory.create(listOf(Tile.Numeric(Tile.Suit.Bamboo, 1))),
            discardPile = discardPile1,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player, otherPlayer1),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證：不可執行九種九牌
        assertFalse(actions.any { it is GameAction.ExhaustiveDraw })
    }

    /**
     * 測試搶槓時可執行榮和動作之情況。
     *
     * 當手牌已听牌，且其他玩家執行加槓時，可搶槓榮和。
     * 此手牌為無役牌型（副露含老頭牌與字牌），但搶槓本身有 1 番，
     * 故在 minimumWinConstraint = 1 之條件下可榮和。
     *
     * 牌型：
     * - 副露 1：123m（吃）
     * - 副露 2：999p（碰）
     * - 副露 3：西西西（碰，非場風非自風非三元牌）
     * - 手中：55s（雀頭）、67s（聽 5s, 8s）
     */
    @Test
    fun `test can robbing kan`() {
        // 準備
        val chiMeld = Meld(
            MeldType.CHI,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3)),
            ),
            sourceDirection = RelativeDirection.Left,
        )
        val ponMeld1 = Meld(
            MeldType.PON,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
            ),
            sourceDirection = RelativeDirection.Across,
        )
        val ponMeld2 = Meld(
            MeldType.PON,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Honor.West),
                FakeIdentifiedTileFactory.create(Tile.Honor.West),
                FakeIdentifiedTileFactory.create(Tile.Honor.West),
            ),
            sourceDirection = RelativeDirection.Right,
        )
        val playerHand = Hand(
            mutableListOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 6)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 7)),
            ),
            mutableListOf(chiMeld, ponMeld1, ponMeld2),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(minimumWinConstraint = 1),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))

        // 執行：模擬其他玩家加槓 5s，觸發搶槓判定
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, incomingTile.id, emptyList()),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證：搶槓可榮和
        assertTrue(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試搶暗槓時可執行榮和動作之情況（國士無雙）。
     *
     * 當手牌為國士無雙聽牌，且其他玩家執行暗槓時，可搶暗槓榮和。
     * 國士無雙為役滿牌型，可搶暗槓。
     *
     * 牌型：國士無雙（19 萬筒索＋東南西北白發中各一張，再加 1 張東風作雀頭）
     */
    @Test
    fun `test can robbing closed kan with kokushi musou`() {
        // 準備：國士無雙聽牌（13張幺九牌 + 1張東風雀頭）
        // 聽牌為其他任意幺九牌，暗槓東風後手牌變為 14 張（東風刻子 + 13 張幺九牌）
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red,
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)

        // 執行：模擬其他玩家暗槓東風，觸發搶暗槓判定
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingTile.id, emptyList()),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證：國士無雙可搶暗槓榮和
        assertTrue(actions.any { it is GameAction.Ron })
    }

    /**
     * 測試搶暗槓時不可執行榮和動作之情況（非國士無雙）。
     *
     * 當手牌為非國士無雙的聽牌，且其他玩家執行暗槓時，不可搶暗槓榮和。
     * 只有國士無雙可以搶暗槓。
     *
     * 牌型：非國士無雙聽牌
     */
    @Test
    fun `test cannot robbing closed kan without kokushi musou`() {
        // 準備：九蓮寶燈 (聽 2 萬)
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 3),
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
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
        )
        val incomingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2))

        // 執行：模擬其他玩家暗槓 2m，觸發搶暗槓判定
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingTile.id, emptyList()),
            sourceDirection = RelativeDirection.Across,
            incomingTile = incomingTile,
        )

        // 驗證：非國士無雙手牌不可搶暗槓榮和
        assertFalse(actions.any { it is GameAction.Ron })
    }
}
