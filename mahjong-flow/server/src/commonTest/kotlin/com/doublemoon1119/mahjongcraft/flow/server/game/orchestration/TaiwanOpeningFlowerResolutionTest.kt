package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.bundled.TaiwanFlowerRuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPendingFlowerFollowUp
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** 驗證台灣麻將局首補花：莊家先摸開門牌，再從莊家開始逐輪補花，並依處理順序回報呈現步驟。 */
class TaiwanOpeningFlowerResolutionTest {
    /** 莊家先摸一張，第一輪莊家一次補兩張、下家補一張；莊家補到的花在第二輪再補，補完莊家直接保留摸牌位的牌。 */
    @Test
    fun `the dealer draws first and flowers are replaced round by round`() {
        val flowers = TaiwanTileTypes.ALL.map { FakeIdentifiedTileFactory.create(Tile.Extension(it)) }
        val openingTile = ordinary()
        val replacements = listOf(flowers[3]) + List(3) { ordinary() }
        val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig())
        val players = Wind.entries.mapIndexed { index, wind ->
            val hand = when (index) {
                0 -> listOf(flowers[0], flowers[1]) + List(14) { ordinary() }
                1 -> listOf(flowers[2]) + List(15) { ordinary() }
                else -> List(16) { ordinary() }
            }
            FakeMahjongPlayerFactory.create(wind, hand = Hand(tiles = hand), discardPile = TaiwanDiscardPile())
        }
        val table = FakeTableStateFactory.create(
            players = players,
            dealerPlayerId = players[0].id,
            config = module.config,
            tileWall = TileWall(listOf(openingTile) + List(LIVE_TILES) { ordinary() }),
            reservedWallTiles = replacements + List(RESERVED_TILES - replacements.size) { ordinary() },
        )
        val step = assertNotNull(TaiwanFlowerRuleDecisionResolver.begin(RuleDecisionTiming.ROUND_START, table, module))

        val resolution = TaiwanFlowerRuleDecisionResolver.resolve(table, PendingRuleDecision.open(step, RuleDecisionTiming.ROUND_START), module)

        val (dealer, downstream) = players.map { it.id }
        assertEquals(
            listOf(
                RuleDecisionPlayerAreaPresentationStep.Draw(dealer, openingTile.id),
                RuleDecisionPlayerAreaPresentationStep.Replacement(dealer, listOf(flowers[0].id, flowers[1].id), listOf(replacements[0].id, replacements[1].id)),
                RuleDecisionPlayerAreaPresentationStep.Replacement(downstream, listOf(flowers[2].id), listOf(replacements[2].id)),
                RuleDecisionPlayerAreaPresentationStep.Replacement(dealer, listOf(flowers[3].id), listOf(replacements[3].id)),
            ),
            resolution.playerAreaPresentationSteps,
        )
        val after = resolution.tableState
        assertEquals(replacements[3], after.players[0].hand.lastDrawn)
        assertEquals(17, after.players[0].hand.standingTiles.size)
        assertEquals(null, after.players[1].hand.lastDrawn)
        assertEquals(16, after.players[1].hand.standingTiles.size)
    }

    /** 牌局中摸到的花已在摸牌時呈現；補到的牌又是花時以「摸到後從摸牌位移出並補牌」呈現，不再重複呈現第一張花。 */
    @Test
    fun `a mid-game chain does not present the drawn flower again`() {
        val flowers = TaiwanTileTypes.ALL.map { FakeIdentifiedTileFactory.create(Tile.Extension(it)) }
        val drawnFlower = flowers[0]
        val chainedFlower = flowers[1]
        val finalReplacement = ordinary()
        val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig())
        val players = Wind.entries.mapIndexed { index, wind ->
            val player = FakeMahjongPlayerFactory.create(wind, hand = Hand(tiles = List(16) { ordinary() }), discardPile = TaiwanDiscardPile())
            if (index == 0) player.copy(tileAreas = player.tileAreas.withArea(TaiwanPlayerTileAreaIds.FLOWERS, listOf(drawnFlower))) else player
        }
        val table = FakeTableStateFactory.create(
            players = players,
            dealerPlayerId = players[0].id,
            config = module.config,
            tileWall = TileWall(List(LIVE_TILES) { ordinary() }),
            reservedWallTiles = listOf(chainedFlower, finalReplacement) + List(RESERVED_TILES - 2) { ordinary() },
            components = ComponentSet.EMPTY.with(TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP, TaiwanPendingFlowerFollowUp(players[0].id, drawnFlower.id, replacementsDue = 1)),
        )
        val step = assertNotNull(TaiwanFlowerRuleDecisionResolver.begin(RuleDecisionTiming.DRAW_FOLLOW_UP, table, module))

        val resolution = TaiwanFlowerRuleDecisionResolver.resolve(table, PendingRuleDecision.open(step, RuleDecisionTiming.DRAW_FOLLOW_UP), module)

        val followUp = assertIs<RuleDecisionPlayerAreaPresentationStep.FollowUp>(resolution.playerAreaPresentationSteps.single())
        assertEquals(chainedFlower.id, followUp.followUpStep.setAsideTileId)
        assertEquals(listOf(finalReplacement.id), followUp.followUpStep.drawnTileIds)
        assertEquals(finalReplacement, resolution.tableState.players[0].hand.lastDrawn)
    }

    /**
     * 建立一張一般牌。
     *
     * @return 一筒。
     */
    private fun ordinary(): IdentifiedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))

    /** 測試常數。 */
    private companion object {
        /** 開門牌之後的活牌張數。 */
        const val LIVE_TILES = 20

        /** 保留區張數。 */
        const val RESERVED_TILES = 16
    }
}
