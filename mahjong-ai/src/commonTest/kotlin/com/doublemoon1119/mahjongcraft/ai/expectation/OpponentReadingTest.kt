package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.BONUS_NEIGHBOR_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.BONUS_TILE_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.DECLARATION_NEIGHBOR_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.FAR_OUTSIDE_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.FLUSH_SUIT_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.MIDDLE_FAR_OUTSIDE_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.MIDDLE_NEAR_OUTSIDE_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.NEAR_OUTSIDE_FACTOR
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentReading.Companion.OFF_SUIT_FACTOR
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.NeutralPositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TileWallSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.IdentityTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證所有規則共用的讀牌：各種跡象的判斷與倍率。 */
class OpponentReadingTest {
    private val reading = OpponentReading(interpretation = IdentityTileInterpretationPolicy, rules = NeutralPositionRules)
    private val riichiRules = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig()).createPositionRules()
    private val riichiReading = OpponentReading(interpretation = RiichiTileInterpretationPolicy, rules = riichiRules)

    /** 早打四萬時，緊鄰的三萬、二萬折扣較強，較遠的一萬折扣較弱；內側的五萬與四萬本身不受影響。 */
    @Test
    fun `tiles just outside an early discard get the strongest discount`() {
        val opponent = snapshotOf(player(discards = listOf(m(4))))

        assertEquals(NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(3)))
        assertEquals(NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(2)))
        assertEquals(FAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(1)))
        assertEquals(1.0, reading.earlyOutsideFactor(opponent, m(5)))
        assertEquals(1.0, reading.earlyOutsideFactor(opponent, m(4)))
    }

    /** 早打七萬時，外側是較大的八萬、九萬。 */
    @Test
    fun `the outside of a high early discard is toward nine`() {
        val opponent = snapshotOf(player(discards = listOf(m(7))))

        assertEquals(NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(8)))
        assertEquals(NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(9)))
        assertEquals(1.0, reading.earlyOutsideFactor(opponent, m(6)))
    }

    /** 早打五萬時兩側都是外側，但效果較弱。 */
    @Test
    fun `an early five discounts both sides more mildly`() {
        val opponent = snapshotOf(player(discards = listOf(m(5))))

        assertEquals(MIDDLE_NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(4)))
        assertEquals(MIDDLE_NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(6)))
        assertEquals(MIDDLE_FAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(1)))
        assertEquals(MIDDLE_FAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(9)))
    }

    /** 第 7 張以後打出的牌不算早巡；不同花色與字牌不受早外影響。 */
    @Test
    fun `late discards other suits and honors are not affected`() {
        val late = snapshotOf(player(discards = List(6) { Tile.Honor.North } + m(4)))
        val early = snapshotOf(player(discards = listOf(m(4))))

        assertEquals(1.0, reading.earlyOutsideFactor(late, m(3)))
        assertEquals(1.0, reading.earlyOutsideFactor(early, p(3)))
        assertEquals(1.0, reading.earlyOutsideFactor(early, Tile.Honor.East))
    }

    /** 多張早打的牌同時符合時只取最強的折扣，不連乘。 */
    @Test
    fun `several early discards do not compound`() {
        val opponent = snapshotOf(player(discards = listOf(m(4), m(3))))

        assertEquals(NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(2)))
        assertEquals(NEAR_OUTSIDE_FACTOR, reading.earlyOutsideFactor(opponent, m(1)))
    }

    /** 牌面先以規則的正規化比較：早打赤五筒與早打五筒效果相同。 */
    @Test
    fun `tiles are compared after the rule normalizes them`() {
        val opponent = snapshotOf(player(discards = listOf(RiichiTileTypes.redFive(Tile.Suit.Dot))))

        assertEquals(MIDDLE_NEAR_OUTSIDE_FACTOR, riichiReading.earlyOutsideFactor(opponent, p(4)))
    }

    /** 副露都是筒子、牌河幾乎沒有筒子時：筒子較危險、其他花色較安全，副露沒有字牌時字牌不變。 */
    @Test
    fun `a one-suit hand makes its suit dangerous and other suits safe`() {
        val opponent = snapshotOf(
            player(
                discards = listOf(m(1), s(9), m(8), Tile.Honor.North, s(2), p(1), m(5)),
                melds = listOf(pon(p(3)), chi(p(5), p(6), p(7))),
            ),
        )

        assertEquals(OpponentReading.FlushSignal(suit = Tile.Suit.Dot, withHonors = false), reading.flushSignal(opponent))
        assertEquals(FLUSH_SUIT_FACTOR, reading.flushFactor(opponent, p(2)))
        assertEquals(OFF_SUIT_FACTOR, reading.flushFactor(opponent, m(2)))
        assertEquals(1.0, reading.flushFactor(opponent, Tile.Honor.East))
    }

    /** 副露含字牌時像在做混一色，字牌也較危險。 */
    @Test
    fun `an honor meld makes honors dangerous too`() {
        val opponent = snapshotOf(
            player(
                discards = listOf(m(1), s(9), m(8), s(2), m(5)),
                melds = listOf(pon(Tile.Honor.Red), pon(p(3))),
            ),
        )

        assertEquals(FLUSH_SUIT_FACTOR, reading.flushFactor(opponent, Tile.Honor.East))
    }

    /** 副露混了花色、牌河中那個花色太多、捨牌太少，或只有暗槓時都不算染手訊號。 */
    @Test
    fun `mixed melds suit discards few discards or only closed kans give no signal`() {
        val offSuitDiscards = listOf(m(1), s(9), m(8), Tile.Honor.North, s(2), m(5), s(4))
        val closedKan = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = List(4) { FakeIdentifiedTileFactory.create(p(3)) },
            sourceDirection = RelativeDirection.Self,
        )

        assertNull(reading.flushSignal(snapshotOf(player(discards = offSuitDiscards, melds = listOf(pon(p(3)), pon(m(7)))))))
        assertNull(reading.flushSignal(snapshotOf(player(discards = listOf(m(1), p(9), m(8), p(2), m(5), s(4), p(1)), melds = listOf(pon(p(3)))))))
        assertNull(reading.flushSignal(snapshotOf(player(discards = listOf(m(1), s(9), m(8), s(2)), melds = listOf(pon(p(3)))))))
        assertNull(reading.flushSignal(snapshotOf(player(discards = offSuitDiscards, melds = listOf(closedKan)))))
        assertNotNull(reading.flushSignal(snapshotOf(player(discards = offSuitDiscards, melds = listOf(pon(p(3)))))))
    }

    /** 用五萬宣告聽牌時，三萬、四萬、六萬、七萬比較危險；更遠的牌、其他花色，以及沒有宣告的對手都不受影響。 */
    @Test
    fun `tiles next to the declaration tile are more dangerous`() {
        val declarer = riichiPlayer(discardsBefore = listOf(Tile.Honor.North), declarationTile = m(5))
        val quiet = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, playerRuleState = RiichiPlayerState())
        val view = riichiView(listOf(declarer, quiet))

        listOf(m(3), m(4), m(6), m(7)).forEach { tile ->
            assertEquals(DECLARATION_NEIGHBOR_FACTOR, riichiReading.declarationNeighborFactor(view, declarer.id, tile), "$tile")
        }
        listOf(m(2), m(8), m(5), p(4), Tile.Honor.East).forEach { tile ->
            assertEquals(1.0, riichiReading.declarationNeighborFactor(view, declarer.id, tile), "$tile")
        }
        assertEquals(1.0, riichiReading.declarationNeighborFactor(view, quiet.id, m(4)))
    }

    /** 宣告時打出的是字牌時，沒有「旁邊的牌」。 */
    @Test
    fun `an honor declaration tile has no neighbors`() {
        val declarer = riichiPlayer(discardsBefore = emptyList(), declarationTile = Tile.Honor.West)
        val view = riichiView(listOf(declarer))

        assertEquals(1.0, riichiReading.declarationNeighborFactor(view, declarer.id, Tile.Honor.North))
    }

    /** 寶牌是三萬時：三萬本身、一二四五萬比較危險，本身的倍率不和旁邊的倍率連乘；其他牌不受影響。 */
    @Test
    fun `bonus tiles and their neighbors are more dangerous`() {
        val view = riichiView(listOf(FakeMahjongPlayerFactory.create(Wind.EAST)), doraIndicators = listOf(m(2)))

        assertEquals(BONUS_TILE_FACTOR, riichiReading.bonusNeighborFactor(view, m(3)))
        listOf(m(1), m(2), m(4), m(5)).forEach { tile ->
            assertEquals(BONUS_NEIGHBOR_FACTOR, riichiReading.bonusNeighborFactor(view, tile), "$tile")
        }
        listOf(m(6), p(3), Tile.Honor.East).forEach { tile ->
            assertEquals(1.0, riichiReading.bonusNeighborFactor(view, tile), "$tile")
        }
    }

    /** 規則沒有依牌張加計的打點時，沒有任何牌受影響。 */
    @Test
    fun `rules without bonus tiles never raise danger`() {
        val self = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val view = PositionView(
            snapshot = FakeTableStateFactory.create(players = listOf(self)).toSnapshot(visibleHandPlayerIds = setOf(self.id), setAsideTiles = { emptyList() }),
            evaluatorId = self.id,
        )

        assertEquals(1.0, reading.bonusNeighborFactor(view, m(3)))
    }

    /** 整體倍率是各項倍率的乘積。 */
    @Test
    fun `the danger factor combines every reading`() {
        val opponent = player(
            discards = listOf(m(4), s(9), m(8), Tile.Honor.North, s(2), m(5), s(4)),
            melds = listOf(pon(p(3))),
        )
        val self = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val view = PositionView(
            snapshot = FakeTableStateFactory.create(players = listOf(opponent, self)).toSnapshot(visibleHandPlayerIds = setOf(self.id), setAsideTiles = { emptyList() }),
            evaluatorId = self.id,
        )

        assertEquals(NEAR_OUTSIDE_FACTOR * OFF_SUIT_FACTOR, reading.dangerFactor(view, opponent.id, m(3)), absoluteTolerance = 1e-12)
    }

    /** 東家對手，依序打出 [discards]，並有 [melds] 副露。 */
    private fun player(discards: List<Tile>, melds: List<Meld> = emptyList()): MahjongPlayer = FakeMahjongPlayerFactory.create(
        initialSeat = Wind.EAST,
        hand = Hand(melds = melds),
        discardPile = discards.fold(FakeDiscardPile()) { pile, tile -> pile.discardTile(FakeIdentifiedTileFactory.create(tile)) },
    )

    /** 東家對手，打出 [discardsBefore] 之後以 [declarationTile] 宣告立直。 */
    private fun riichiPlayer(discardsBefore: List<Tile>, declarationTile: Tile): MahjongPlayer {
        val riichiTile = FakeIdentifiedTileFactory.create(declarationTile)
        val pile = discardsBefore
            .fold(RiichiDiscardPile()) { pile, tile -> pile.discardTile(FakeIdentifiedTileFactory.create(tile)) }
            .discard(RiichiDiscardEntry(riichiTile, isRiichi = true))
        return FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            discardPile = pile,
            playerRuleState = RiichiPlayerState(riichiTile = riichiTile),
        )
    }

    /** 日麻桌上以南家為觀察者的視角；[doraIndicators] 不為空時，牌山只包含這些公開的寶牌指示牌。 */
    private fun riichiView(players: List<MahjongPlayer>, doraIndicators: List<Tile> = emptyList()): PositionView {
        val self = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState())
        val snapshot = FakeTableStateFactory.create(
            players = players + self,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        ).toSnapshot(visibleHandPlayerIds = setOf(self.id), setAsideTiles = { emptyList() })
        val withIndicators = if (doraIndicators.isEmpty()) {
            snapshot
        } else {
            snapshot.copy(tileWall = TileWallSnapshot(doraIndicators.map { IdentifiedTileSnapshot(id = Uuid.random(), tile = it) }))
        }
        return PositionView(snapshot = withIndicators, evaluatorId = self.id)
    }

    /** 以南家為觀察者時 [player] 的快照。 */
    private fun snapshotOf(player: MahjongPlayer): MahjongPlayerSnapshot {
        val self = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        return FakeTableStateFactory.create(players = listOf(player, self))
            .toSnapshot(visibleHandPlayerIds = setOf(self.id), setAsideTiles = { emptyList() })
            .players
            .first { it.id == player.id }
    }

    private fun pon(tile: Tile): Meld = Meld(
        type = MeldType.PON,
        tiles = List(3) { FakeIdentifiedTileFactory.create(tile) },
        sourceDirection = RelativeDirection.Across,
    )

    private fun chi(vararg tiles: Tile): Meld = Meld(
        type = MeldType.CHI,
        tiles = tiles.map(FakeIdentifiedTileFactory::create),
        sourceDirection = RelativeDirection.Left,
    )

    private fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    private fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    private fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)
}
