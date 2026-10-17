package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 透過正式合法動作入口，驗證立直後暗槓的等待與面子結構限制。 */
class RiichiClosedKanAfterRiichiTest {
    /** 實際牌譜中的手牌：三餅對子、五餅刻子及條子兩面等待。 */
    private val recordedHand = tiles(dots = "33555", bamboos = "34667788")

    /** 同花色鄰近對子不影響獨立刻子，普通五與赤五皆可暗槓。 */
    @Test
    fun `recorded hand allows plain and red five dot closed kan`() {
        val normalFive = Tile.Numeric(Tile.Suit.Dot, 5)
        val redFive = RiichiTileTypes.redFive(Tile.Suit.Dot)
        assertEquals(tiles(bamboos = "25").toSet(), waits(recordedHand))
        listOf(normalFive, redFive).forEach { incoming ->
            val actions = closedKans(recordedHand, incoming)
            assertEquals(1, actions.size, "Both plain and red five must allow the recorded closed kan.")
            assertEquals(3, actions.single().withTiles.size)
        }
        val handWithRedFive = recordedHand.toMutableList().apply {
            this[indexOf(normalFive)] = redFive
        }
        assertEquals(1, closedKans(handWithRedFive, normalFive).size)
    }

    /** 三人日麻透過自己的正式模組使用相同的暗槓限制。 */
    @Test
    fun `three player riichi allows the recorded red five closed kan`() {
        assertEquals(
            1,
            closedKans(recordedHand, RiichiTileTypes.redFive(Tile.Suit.Dot), threePlayer = true).size,
        )
    }

    /** 摸牌已放入手牌時，合法動作入口會分離摸牌，不將第四張牌重複計數。 */
    @Test
    fun `drawn tile stored in hand allows the recorded closed kan`() {
        assertEquals(
            1,
            closedKans(
                standing = recordedHand,
                incoming = RiichiTileTypes.redFive(Tile.Suit.Dot),
                incomingStoredInHand = true,
            ).size,
        )
    }

    /** 已有暗槓時，立牌只需組成剩餘面子，不能當作缺牌拒絕。 */
    @Test
    fun `existing closed kan counts as a completed meld`() {
        val existing = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.East) },
            sourceDirection = RelativeDirection.Self,
        )
        val hand = tiles(dots = "33555", bamboos = "34678")
        assertEquals(tiles(bamboos = "25").toSet(), waits(hand, listOf(existing)))
        assertEquals(1, closedKans(hand, RiichiTileTypes.redFive(Tile.Suit.Dot), listOf(existing)).size)
    }

    /** 獨立五條暗槓前後維持六、九條等待，附近的七條不構成禁止條件。 */
    @Test
    fun `independent bamboo triplet keeps six and nine waits`() {
        val hand = tiles(characters = "123", dots = "456", bamboos = "5557899")
        assertEquals(tiles(bamboos = "69").toSet(), waits(hand))
        assertEquals(1, closedKans(hand, Tile.Numeric(Tile.Suit.Bamboo, 5)).size)
    }

    /** 暗槓獨立一條刻子，仍維持一、四萬的兩面等待。 */
    @Test
    fun `independent triplet keeps an unrelated two sided wait`() {
        val hand = tiles(characters = "23789", dots = "567", bamboos = "11188")
        assertEquals(tiles(characters = "14").toSet(), waits(hand))
        assertEquals(1, closedKans(hand, Tile.Numeric(Tile.Suit.Bamboo, 1)).size)
    }

    /** 三萬暗槓會使一、二、四萬等待縮為二萬，必須拒絕。 */
    @Test
    fun `closed kan that removes winning tiles is rejected`() {
        val hand = tiles(characters = "2333", dots = "456", bamboos = "222789")
        assertEquals(tiles(characters = "124").toSet(), waits(hand))
        assertEquals(tiles(characters = "2").toSet(), waitsAfterKan(hand, Tile.Numeric(Tile.Suit.Character, 3)))
        assertTrue(closedKans(hand, Tile.Numeric(Tile.Suit.Character, 3)).isEmpty())
    }

    /** 六萬暗槓會改變多面等待，不能只因看得到一組刻子就允許。 */
    @Test
    fun `overlapping sequence and triplet change the winning tile set`() {
        val hand = tiles(characters = "2345666", dots = "123", bamboos = "888")
        assertEquals(tiles(characters = "12457").toSet(), waits(hand))
        assertEquals(tiles(characters = "25").toSet(), waitsAfterKan(hand, Tile.Numeric(Tile.Suit.Character, 6)))
        assertTrue(closedKans(hand, Tile.Numeric(Tile.Suit.Character, 6)).isEmpty())
    }

    /** 等待相同，但一餅可改作雀頭與順子時，仍不可暗槓。 */
    @Test
    fun `unchanged waits do not permit alternative pair and sequence interpretations`() {
        val hand = tiles(characters = "56", dots = "11123444", bamboos = "222")
        val expected = tiles(characters = "47").toSet()
        assertEquals(expected, waits(hand))
        assertEquals(expected, waitsAfterKan(hand, Tile.Numeric(Tile.Suit.Dot, 1)))
        assertTrue(closedKans(hand, Tile.Numeric(Tile.Suit.Dot, 1)).isEmpty())
    }

    /** 同一組九張牌可拆成三刻子或三順子時，不能只採第一個刻子拆法。 */
    @Test
    fun `all triplets interpretation does not hide an alternative sequence interpretation`() {
        val hand = tiles(characters = "222333444", dots = "567", bamboos = "9")
        val expected = tiles(bamboos = "9").toSet()
        assertEquals(expected, waits(hand))
        assertEquals(expected, waitsAfterKan(hand, Tile.Numeric(Tile.Suit.Character, 2)))
        assertTrue(closedKans(hand, Tile.Numeric(Tile.Suit.Character, 2)).isEmpty())
    }

    /** 張數不足、沒有等待或沒有三張槓牌時，不接受不完整的結構證據。 */
    @Test
    fun `incomplete structure evidence is rejected`() {
        val five = Tile.Numeric(Tile.Suit.Dot, 5)
        assertTrue(!RiichiClosedKanStructure.preservesTriplet(recordedHand.dropLast(1), setOf(Tile.Honor.East), five, 0))
        assertTrue(!RiichiClosedKanStructure.preservesTriplet(recordedHand, emptySet(), five, 0))
        assertTrue(!RiichiClosedKanStructure.preservesTriplet(recordedHand, setOf(Tile.Honor.East), Tile.Honor.Red, 0))
    }

    /** 原始手牌順序不影響所有面子拆法的檢查。 */
    @Test
    fun `structure decision is independent of standing tile order`() {
        val random = Random(20261017)
        val five = Tile.Numeric(Tile.Suit.Dot, 5)
        val winning = waits(recordedHand)
        repeat(30) {
            assertTrue(RiichiClosedKanStructure.preservesTriplet(recordedHand.shuffled(random), winning, five, 0))
        }
    }

    /**
     * 以正式四人或三人規則判斷立直後的暗槓，摸入牌另外傳入以免重複計數。
     *
     * @param standing 立直後、不含本次摸牌的立牌。
     * @param incoming 本次摸到的牌種。
     * @param melds 已有的暗槓。
     * @param threePlayer 是否使用三人日麻模組。
     * @param incomingStoredInHand 是否將摸牌放在手牌內，而非透過獨立參數傳入。
     * @return 目前提供的暗槓動作。
     */
    private fun closedKans(
        standing: List<Tile>,
        incoming: Tile,
        melds: List<Meld> = emptyList(),
        threePlayer: Boolean = false,
        incomingStoredInHand: Boolean = false,
    ): List<GameAction.Kan> {
        val module = if (threePlayer) {
            ThreePlayerRiichiRuleModule("test:riichi_three", ThreePlayerRiichiRuleConfig())
        } else {
            RiichiRuleModule("test:riichi_four", RiichiRuleConfig())
        }
        val drawn = FakeIdentifiedTileFactory.create(incoming)
        val player = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(tiles = standing, melds = melds).copy(
                lastDrawn = drawn.takeIf { incomingStoredInHand },
            ),
            playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)),
        )
        val table = FakeTableStateFactory.create(
            config = module.config,
            players = listOf(player),
            tileWall = TileWall(List(30) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }),
        )
        return module.createLegalActionValidator().getLegalActions(
            tableState = table,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = drawn.takeUnless { incomingStoredInHand },
        ).filterIsInstance<GameAction.Kan>().filter { it.type == GameAction.KanType.CLOSED_KAN }.also { actions ->
            actions.forEach { action ->
                assertEquals(drawn.id, action.tileId)
                assertEquals(3, action.withTiles.distinct().size)
            }
        }
    }

    /** 取得完整等待集合，不以是否具有役種或剩餘牌數裁切。 */
    private fun waits(standing: List<Tile>, melds: List<Meld> = emptyList()): Set<Tile> {
        val result = RiichiShantenCalculator().calculate(FakeHandFactory.create(tiles = standing, melds = melds))
        return (result as? ShantenResult.Tenpai)?.winningTiles?.toSet() ?: error("Expected a tenpai fixture: $standing")
    }

    /** 移出三張立牌並加入本次摸牌，建立暗槓後的等待集合。 */
    private fun waitsAfterKan(standing: List<Tile>, kanTile: Tile): Set<Tile> {
        val kan = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = List(4) { FakeIdentifiedTileFactory.create(kanTile) },
            sourceDirection = RelativeDirection.Self,
        )
        return waits(standing.filterNot { it == kanTile }, listOf(kan))
    }

    /** 以分花色數字字串建立不含赤牌的固定測試牌型。 */
    private fun tiles(characters: String = "", dots: String = "", bamboos: String = ""): List<Tile> = buildList {
        listOf(Tile.Suit.Character to characters, Tile.Suit.Dot to dots, Tile.Suit.Bamboo to bamboos).forEach { (suit, values) ->
            values.forEach { value -> add(Tile.Numeric(suit, value.digitToInt())) }
        }
    }
}
