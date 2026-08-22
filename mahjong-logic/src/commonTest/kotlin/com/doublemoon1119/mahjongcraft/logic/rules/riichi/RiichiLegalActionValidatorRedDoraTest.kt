package com.doublemoon1119.mahjongcraft.logic.rules.riichi
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 立直麻將合法動作驗證器之赤寶牌測試。
 *
 * 測試內容涵蓋赤寶牌（Red Dora）在吃、碰、槓等動作判定中的處理邏輯。
 * 赤寶牌與普通牌在動作判定上視為同張牌。
 *
 * @see RiichiLegalActionValidator
 */
class RiichiLegalActionValidatorRedDoraTest {

    private val validator = RiichiLegalActionValidator(
        shantenCalculator = RiichiShantenCalculator(),
        handValueCalculator = RiichiHandValueCalculator(),
        contextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig()),
    )

    /** 牌山還有牌可摸——鳴牌/槓牌測試預設用這個，避免被新加的「河底/海底不可鳴牌」限制誤擋。 */
    private val nonEmptyWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))))

    /**
     * 測試赤寶牌之情況：可執行碰牌動作（手牌有普通5萬，他家打赤5萬）。
     *
     * 赤寶牌與普通牌在動作判定上視為同張牌，故手牌有普通5萬時可碰赤5萬。
     */
    @Test
    fun `test can pon with red dora incoming`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 5), // 普通5萬
                Tile.Numeric(Tile.Suit.Character, 5), // 普通5萬
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )
        val incomingTile = FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character)) // 赤5萬

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
     * 測試赤寶牌之情況：可執行碰牌動作（手牌有赤5萬，他家打普通5萬）。
     *
     * 赤寶牌與普通牌在動作判定上視為同張牌，故手牌有赤5萬時可碰普通5萬。
     */
    @Test
    fun `test can pon with red dora in hand`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                RiichiTileTypes.redFive(Tile.Suit.Character), // 赤5萬
                RiichiTileTypes.redFive(Tile.Suit.Character), // 赤5萬
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )
        val incomingTile =
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)) // 普通5萬

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
     * 測試赤寶牌之情況：可執行大明槓動作。
     *
     * 手牌有三張普通5萬，可對赤5萬執行大明槓。
     */
    @Test
    fun `test can open kan with red dora`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )
        val incomingTile = FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character))

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
     * 測試赤寶牌之情況：可執行暗槓動作。
     *
     * 手牌有三張普通5萬，可對赤5萬執行暗槓。
     */
    @Test
    fun `test can closed kan with red dora`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )
        val incomingTile = FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.CLOSED_KAN })
    }

    /**
     * 測試赤寶牌之情況：可執行加槓動作。
     *
     * 已有普通5萬的碰牌，可對赤5萬執行加槓。
     */
    @Test
    fun `test can added kan with red dora`() {
        // 準備
        val ponMeld = Meld(
            MeldType.PON,
            listOf(
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)),
                FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)),
            ),
            sourceDirection = RelativeDirection.Left,
        )
        val playerHand = Hand(melds = mutableListOf(ponMeld))
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )
        val incomingTile = FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character))

        // 執行
        val actions = validator.getLegalActions(
            tableState = tableState,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )

        // 驗證
        assertTrue(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.ADDED_KAN })
    }

    /**
     * 測試赤寶牌之情況：可執行吃牌動作（手牌有普通4萬、6萬，他家打赤5萬）。
     *
     * 吃牌不受赤寶牌影響，但仍需正確處理。
     */
    @Test
    fun `test can chi with red dora incoming`() {
        // 準備
        val playerHand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 6),
            ),
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = playerHand,
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = nonEmptyWall,
        )
        val incomingTile = FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character))

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
}
