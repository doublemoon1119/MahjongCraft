package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [RiichiNagashiManganOutcomeResolver] 的付款、供託與連莊決策測試。 */
class RiichiNagashiManganOutcomeResolverTest {
    /** 日麻規則模組。 */
    private val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())

    /** 驗證莊家成立時收下供託、走 win-equivalent 並明確連莊。 */
    @Test
    fun `dealer achiever collects stick pot and repeats`() {
        val dealer = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            discardPile = allYaochuuDiscardPile(),
        ).copy(score = 25000)
        val others = listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map { wind ->
            FakeMahjongPlayerFactory.create(initialSeat = wind).copy(score = 25000)
        }
        val table = FakeTableStateFactory.create(
            players = listOf(dealer) + others,
            config = module.config,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
        )

        val outcome = RiichiNagashiManganOutcomeResolver().resolve(table, module)!!

        assertEquals(BuiltInRoundOutcomeIds.NAGASHI_MANGAN, outcome.id)
        assertEquals(RoundOutcomePresentationClassification.WIN_EQUIVALENT, outcome.presentationClassification)
        assertEquals(RoundTransitionDirective.REPEAT_DEALER, outcome.transitionDirective)
        assertEquals(setOf(dealer.id), outcome.stickPotCollectorPlayerIds)
        assertEquals(14000, outcome.scoreDeltas.getValue(dealer.id))
        others.forEach { assertEquals(-4000, outcome.scoreDeltas.getValue(it.id)) }
        assertEquals(0, (outcome.settledTableState.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /** 驗證牌河曾被鳴走時不成立。 */
    @Test
    fun `taken discard prevents outcome`() {
        val player = FakeMahjongPlayerFactory.create(discardPile = allYaochuuDiscardPile().takeLast())
        val table = FakeTableStateFactory.create(players = listOf(player), config = module.config)

        assertNull(RiichiNagashiManganOutcomeResolver().resolve(table, module))
    }

    /** 建立全部由么九牌構成、且未被鳴走的牌河。 */
    private fun allYaochuuDiscardPile(): RiichiDiscardPile = RiichiDiscardPile()
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)))
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)))
}
