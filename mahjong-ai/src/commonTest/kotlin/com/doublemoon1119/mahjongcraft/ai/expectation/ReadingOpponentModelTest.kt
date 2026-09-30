package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiOpponentModel
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證進階深度時套在規則模型外面的共用讀牌。 */
class ReadingOpponentModelTest {
    private val riichi = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig())
    private val registry = OpponentModelRegistry().apply { registerRiichiOpponentModel() }
    private val advanced = registry.create(riichi, ReadingDepth.ADVANCED)
    private val basic = registry.create(riichi, ReadingDepth.BASIC)

    /** 對手立直後，別人打出而他沒有胡的牌完全安全；他自己打過的牌也完全安全。 */
    @Test
    fun `tiles the rule excludes from ron are safe`() {
        val self = riichiSeat(Wind.SOUTH, discards = listOf(m(1), m(2), m(3), m(4)))
        val declarer = riichiDeclarer(discardsBefore = listOf(Tile.Honor.North))
        val view = riichiView(listOf(declarer, self), self)

        assertEquals(0.0, advanced.discardDanger(view, declarer.id, m(4)))
        assertTrue(basic.discardDanger(view, declarer.id, m(4)) > 0.0, "basic depth does not use passed tiles")
        assertEquals(0.0, advanced.discardDanger(view, declarer.id, Tile.Honor.North))
    }

    /** 對手的副露都是筒子、牌河沒有筒子時，筒子比基本深度危險、萬子比基本深度安全。 */
    @Test
    fun `reading factors scale the rule model danger`() {
        val self = riichiSeat(Wind.SOUTH)
        val flush = riichiSeat(
            Wind.EAST,
            discards = listOf(m(9), Tile.Honor.North, s(1), m(1), s(8)),
            melds = listOf(pon(p(3)), pon(p(7))),
        )
        val view = riichiView(listOf(flush, self), self)

        assertTrue(advanced.discardDanger(view, flush.id, p(5)) > basic.discardDanger(view, flush.id, p(5)))
        assertTrue(advanced.discardDanger(view, flush.id, m(5)) < basic.discardDanger(view, flush.id, m(5)))
    }

    /** 沒有登記專屬模型的規則，讀牌倍率限制在溫和的範圍內。 */
    @Test
    fun `unregistered rules keep reading factors within mild bounds`() {
        val taiwan = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig())
        val model = registry.create(taiwan, ReadingDepth.ADVANCED)
        val self = FakeMahjongPlayerFactory.create(Wind.SOUTH)
        val active = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.WEST,
            hand = Hand(melds = listOf(pon(p(1)), pon(p(2)))),
            discardPile = (1..6).fold(FakeDiscardPile()) { pile, value -> pile.discardTile(FakeIdentifiedTileFactory.create(m(value))) },
        )
        val view = PositionView(
            snapshot = FakeTableStateFactory.create(players = listOf(active, self)).toSnapshot(visibleHandPlayerIds = setOf(self.id)),
            evaluatorId = self.id,
        )
        val range = OpponentModelRegistry.NEUTRAL_READING_FACTOR_RANGE

        assertEquals(NeutralOpponentModel.UNIFORM_DISCARD_DANGER * range.endInclusive, model.discardDanger(view, active.id, p(5)), absoluteTolerance = 1e-12)
        assertEquals(NeutralOpponentModel.UNIFORM_DISCARD_DANGER * range.start, model.discardDanger(view, active.id, m(1)), absoluteTolerance = 1e-12)
    }

    /** 危險度以外的估計直接使用規則模型的結果。 */
    @Test
    fun `other estimates come from the rule model`() {
        val self = riichiSeat(Wind.SOUTH)
        val declarer = riichiDeclarer(discardsBefore = listOf(Tile.Honor.North))
        val view = riichiView(listOf(declarer, self), self)

        assertEquals(basic.threat(view, declarer.id), advanced.threat(view, declarer.id))
        assertEquals(basic.baselineWinValue(view, self.id), advanced.baselineWinValue(view, self.id))
        assertEquals(ReadingDepth.ADVANCED, advanced.readingDepth)
    }

    /** 東家對手，打出 [discardsBefore] 之後以西風宣告立直。 */
    private fun riichiDeclarer(discardsBefore: List<Tile>): MahjongPlayer {
        val riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.West)
        val pile = discardsBefore
            .fold(RiichiDiscardPile()) { pile, tile -> pile.discardTile(FakeIdentifiedTileFactory.create(tile)) }
            .discard(RiichiDiscardEntry(riichiTile, isRiichi = true))
        return FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, discardPile = pile, playerRuleState = RiichiPlayerState(riichiTile = riichiTile))
    }

    private fun riichiSeat(
        wind: Wind,
        discards: List<Tile> = emptyList(),
        melds: List<Meld> = emptyList(),
    ): MahjongPlayer = FakeMahjongPlayerFactory.create(
        initialSeat = wind,
        hand = Hand(melds = melds),
        discardPile = discards.fold(RiichiDiscardPile()) { pile, tile -> pile.discardTile(FakeIdentifiedTileFactory.create(tile)) },
        playerRuleState = RiichiPlayerState(),
    )

    private fun riichiView(players: List<MahjongPlayer>, self: MahjongPlayer): PositionView = PositionView(
        snapshot = FakeTableStateFactory.create(players = players, config = RiichiRuleConfig(), dynamicRuleState = RiichiDynamicState())
            .toSnapshot(visibleHandPlayerIds = setOf(self.id)),
        evaluatorId = self.id,
    )

    private fun pon(tile: Tile): Meld = Meld(
        type = MeldType.PON,
        tiles = List(3) { FakeIdentifiedTileFactory.create(tile) },
        sourceDirection = RelativeDirection.Across,
    )

    private fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    private fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    private fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)
}
