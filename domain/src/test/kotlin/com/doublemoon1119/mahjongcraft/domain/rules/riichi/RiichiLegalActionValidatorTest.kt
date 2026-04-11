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

    private val validator = RiichiLegalActionValidator(
        shantenCalculator = RiichiShantenCalculator(),
        handValueCalculator = RiichiHandValueCalculator()
    )

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
            ),
            sourceDirection = RelativeDirection.Left
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
            ),
            sourceDirection = RelativeDirection.Left
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

    /**
     * 測試最低胡牌番數限制 - 番數不足無法自摸。
     *
     * 當 minimumWinConstraint = 2 時，手牌番數為 1 則不可自摸。
     */
    @Test
    fun `test tsumo not allowed when han is insufficient`() {
        // 準備：手牌為 1 番 (門前清自摸) 的牌型
        val playerHand = createHand(
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
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            hand = playerHand,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().apply {
                discard(
                    entry = FakeDiscardPile.FakeEntry(
                        tile = IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        isTaken = false
                    )
                )
            }
        )
        val tableState = TableState(
            players = listOf(player),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig(minimumWinConstraint = 2)
        )

        // 摸到胡牌張
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Honor.West)

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        // 驗證：不可自摸（番數不足）
        assertFalse(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試最低胡牌番數限制 - 番數足夠可以自摸。
     *
     * 當 minimumWinConstraint = 1 時，使用役滿牌型確保通過。
     */
    @Test
    fun `test tsumo allowed when han is sufficient`() {
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
            config = FakeMahjongRuleConfig(),
            prevalentWind = Wind.EAST
        )

        // 摸到胡牌張
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = incomingTile
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
            config = FakeMahjongRuleConfig(),
            prevalentWind = Wind.EAST
        )

        // 摸到胡牌張
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        // 驗證：可以自摸（役滿不受最低番數限制）
        assertTrue(actions.any { it is GameAction.Tsumo })
    }

    /**
     * 測試可執行九種九牌和局動作之情況。
     *
     * 當滿足以下條件時，應可執行九種九牌和局：
     * - 第一巡（場上無鳴牌、每人最多打1張牌、自己未打牌）
     * - 持有的字牌或數牌總數達9種以上
     */
    @Test
    fun `test can kyuushu kyuuhai`() {
        // 準備：持有九種牌（東南西北發中白 + 1-9m = 16種取9種）
        val playerHand = createHand(
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
                Tile.Numeric(Tile.Suit.Character, 6)
            )
        )
        val discardPile = FakeDiscardPile()
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            initialSeat = Wind.EAST,
            hand = playerHand,
            discardPile = discardPile
        )
        // 建立其他玩家（每人只打1張以滿足第一巡條件）
        val discardPile1 = FakeDiscardPile()
        discardPile1.discard(
            FakeDiscardPile.FakeEntry(
                IdentifiedTile(
                    UUID.randomUUID(),
                    Tile.Numeric(Tile.Suit.Bamboo, 1)
                )
            )
        )
        val otherPlayer1 = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "Other1",
            initialSeat = Wind.SOUTH,
            hand = createHand(listOf(Tile.Numeric(Tile.Suit.Bamboo, 1))),
            discardPile = discardPile1
        )
        val discardPile2 = FakeDiscardPile()
        discardPile2.discard(
            FakeDiscardPile.FakeEntry(
                IdentifiedTile(
                    UUID.randomUUID(),
                    Tile.Numeric(Tile.Suit.Bamboo, 2)
                )
            )
        )
        val otherPlayer2 = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "Other2",
            initialSeat = Wind.WEST,
            hand = createHand(listOf(Tile.Numeric(Tile.Suit.Bamboo, 2))),
            discardPile = discardPile2
        )
        val discardPile3 = FakeDiscardPile()
        discardPile3.discard(
            FakeDiscardPile.FakeEntry(
                IdentifiedTile(
                    UUID.randomUUID(),
                    Tile.Numeric(Tile.Suit.Bamboo, 3)
                )
            )
        )
        val otherPlayer3 = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "Other3",
            initialSeat = Wind.NORTH,
            hand = createHand(listOf(Tile.Numeric(Tile.Suit.Bamboo, 3))),
            discardPile = discardPile3
        )
        val tableState = TableState(
            players = listOf(player, otherPlayer1, otherPlayer2, otherPlayer3),
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

        // 驗證：可執行九種九牌
        assertTrue(actions.any { it is GameAction.ExhaustiveDraw })
    }

    /**
     * 測試不符合九種九牌條件時不可執行和局動作之情況。
     *
     * 當持有的牌不足9種時，不可執行九種九牌和局。
     */
    @Test
    fun `test cannot kyuushu kyuuhai with insufficient tile types`() {
        // 準備：只持有5種牌
        val playerHand = createHand(
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
                Tile.Numeric(Tile.Suit.Dot, 4)
            )
        )
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "TestPlayer",
            initialSeat = Wind.EAST,
            hand = playerHand,
            discardPile = FakeDiscardPile()
        )
        val discardPile1 = FakeDiscardPile()
        discardPile1.discard(
            FakeDiscardPile.FakeEntry(
                IdentifiedTile(
                    UUID.randomUUID(),
                    Tile.Numeric(Tile.Suit.Bamboo, 1)
                )
            )
        )
        val otherPlayer1 = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "Other1",
            initialSeat = Wind.SOUTH,
            hand = createHand(listOf(Tile.Numeric(Tile.Suit.Bamboo, 1))),
            discardPile = discardPile1
        )
        val tableState = TableState(
            players = listOf(player, otherPlayer1),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )
        val incomingTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 5))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            source = RelativeDirection.Self,
            incomingTile = incomingTile
        )

        // 驗證：不可執行九種九牌
        assertFalse(actions.any { it is GameAction.ExhaustiveDraw })
    }
}
