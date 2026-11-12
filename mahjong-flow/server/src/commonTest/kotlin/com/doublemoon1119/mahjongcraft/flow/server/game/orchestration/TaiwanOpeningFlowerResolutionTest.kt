package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.event.PublicGameActionIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.TileReplacementRemoval
import com.doublemoon1119.mahjongcraft.flow.common.game.model.TileReplacementRound
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.server.game.taiwan.TaiwanFlowerRuleDecisionResolver
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_REVEAL_FLOWER_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerRuleDecisionIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPendingFlowerFollowUp
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.tileArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證台灣麻將局首補花：莊家先摸開門牌，再從莊家開始逐輪補花，並依處理順序回報呈現步驟。 */
class TaiwanOpeningFlowerResolutionTest {
    /** 起手八張花時，胡牌的選項沿用自摸並展示這八張花；放棄的選項不展示牌，以一般的跳過呈現。 */
    @Test
    fun `the eight flowers choice shows the flowers on the win option`() {
        val flowers = TaiwanTileTypes.ALL.map { FakeIdentifiedTileFactory.create(Tile.Extension(it)) }
        val module = TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig())
        val players = Wind.entries.mapIndexed { index, wind ->
            val hand = if (index == 0) flowers + List(8) { ordinary() } else List(16) { ordinary() }
            FakeMahjongPlayerFactory.create(wind, hand = Hand(tiles = hand), discardPile = TaiwanDiscardPile())
        }
        val table = FakeTableStateFactory.create(
            players = players,
            dealerPlayerId = players[0].id,
            config = module.config,
            tileWall = TileWall(List(LIVE_TILES) { ordinary() }),
            reservedWallTiles = List(RESERVED_TILES) { ordinary() },
        )
        val step = assertNotNull(TaiwanFlowerRuleDecisionResolver.begin(RuleDecisionTiming.ROUND_START, table, module))

        val resolution = TaiwanFlowerRuleDecisionResolver.resolve(table, PendingRuleDecision.open(step, RuleDecisionTiming.ROUND_START), module)

        val choice = assertIs<RuleDecisionInputSpec.SingleChoice>(assertNotNull(resolution.nextStep).inputSpecsByPlayerId.getValue(players[0].id))
        assertEquals(listOf(PublicGameActionIds.TSUMO, TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN), choice.optionIds)
        assertEquals(flowers.map { it.id }.toSet(), choice.previewTileIdsByOptionId.getValue(PublicGameActionIds.TSUMO).toSet())
        assertEquals(null, choice.previewTileIdsByOptionId[TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN])
        assertEquals(TaiwanFlowerRuleDecisionIds.PASS_FLOWER_WIN, choice.passOptionId, "Passing is shown as the usual skip.")
    }

    /** 莊家先摸一張，第一輪莊家一次補兩張、下家補一張；莊家補到的花在第二輪再補，補完莊家直接保留摸牌位的牌；每一輪各家移出的花都留下紀錄。 */
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
        // 莊家摸牌，之後每一家的亮花與補牌各一步；同一家一次亮出的花與一次補到的牌在同一步。補完之後各家一起理牌。
        val steps = resolution.steps
        assertEquals(8, steps.size)
        assertEquals(TileArea(TileRegion.DRAWN, dealer), steps[0].tableState.areaOf(openingTile))
        listOf(flowers[0], flowers[1]).forEach { assertEquals(TileArea(TileRegion.SET_ASIDE, dealer), steps[1].tableState.areaOf(it)) }
        listOf(replacements[0], replacements[1]).forEach { assertEquals(dealer, steps[2].tableState.tileArea(it.id)?.ownerId) }
        assertEquals(TileArea(TileRegion.SET_ASIDE, downstream), steps[3].tableState.areaOf(flowers[2]))
        assertEquals(TileArea(TileRegion.DRAWN, downstream), steps[4].tableState.areaOf(replacements[2]), "Replacements land in the drawn slot like a draw.")
        assertEquals(TileArea(TileRegion.SET_ASIDE, dealer), steps[5].tableState.areaOf(flowers[3]))
        assertEquals(TileArea(TileRegion.DRAWN, dealer), steps[6].tableState.areaOf(replacements[3]))
        assertTrue(steps.dropLast(1).all { it.events.isEmpty() }, "Opening replacements are not declarations.")
        assertEquals(listOf(TableStepEvent.HandsSorted(players.mapTo(mutableSetOf()) { it.id })), steps.last().events)
        assertEquals(TileArea(TileRegion.HAND, downstream), steps.last().tableState.areaOf(replacements[2]), "Sorting takes the drawn replacement into the hand.")
        val after = resolution.tableState
        assertEquals(replacements[3], after.players[0].hand.lastDrawn)
        assertEquals(17, after.players[0].hand.standingTiles.size)
        assertEquals(null, after.players[1].hand.lastDrawn)
        assertEquals(16, after.players[1].hand.standingTiles.size)
        assertEquals(0, after.currentPlayerIndex, "Replacing other players' flowers must not pass the turn.")
        assertEquals(TurnPhase.AwaitingAction, after.turnPhase, "The dealer already drew and must not draw again.")
        assertEquals(
            listOf(
                TileReplacementRound(
                    round = 1,
                    removals = listOf(
                        TileReplacementRemoval(dealer, listOf(flowers[0].id, flowers[1].id)),
                        TileReplacementRemoval(downstream, listOf(flowers[2].id)),
                    ),
                ),
                TileReplacementRound(round = 2, removals = listOf(TileReplacementRemoval(dealer, listOf(flowers[3].id)))),
            ),
            resolution.replacementRounds,
            "Each round records the flowers every player set aside.",
        )
    }

    /** 牌局中摸到的花已經亮出；補到的牌又是花時依序呈現補牌、亮花與再補牌，第一張花不再移動也不再宣告。 */
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
            components = ComponentSet.EMPTY.with(TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP, TaiwanPendingFlowerFollowUp(players[0].id, drawnFlower.id, replacementsDue = 1, resumeRound = 1)),
            turnPhase = TurnPhase.AwaitingAction,
        )
        val step = assertNotNull(TaiwanFlowerRuleDecisionResolver.begin(RuleDecisionTiming.DRAW_FOLLOW_UP, table, module))

        val resolution = TaiwanFlowerRuleDecisionResolver.resolve(table, PendingRuleDecision.open(step, RuleDecisionTiming.DRAW_FOLLOW_UP), module)

        val steps = resolution.steps
        val revealer = players[0].id
        assertTrue(steps.all { it.tableState.areaOf(drawnFlower) == TileArea(TileRegion.SET_ASIDE, revealer) })
        val declaration = steps.single { step -> step.events.isNotEmpty() }
        assertEquals(listOf<TableStepEvent>(TableStepEvent.ActionDeclared(revealer, TAIWAN_REVEAL_FLOWER_ACTION)), declaration.events)
        assertEquals(TileArea(TileRegion.SET_ASIDE, revealer), declaration.tableState.areaOf(chainedFlower))
        assertEquals(TileArea(TileRegion.DRAWN, revealer), steps.last().tableState.areaOf(finalReplacement))
        assertEquals(finalReplacement, resolution.tableState.players[0].hand.lastDrawn)
        assertEquals(
            listOf(TileReplacementRound(round = 2, removals = listOf(TileReplacementRemoval(revealer, listOf(chainedFlower.id))))),
            resolution.replacementRounds,
            "The drawn flower was declared as an action in the first round; only the chained flower is a replacement record.",
        )
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

    /**
     * 牌的區域與所屬玩家。
     *
     * @property region 區域種類。
     * @property ownerId 所屬玩家。
     */
    private data class TileArea(val region: TileRegion, val ownerId: Uuid?)

    /** [tile] 在這份桌況的區域與所屬玩家。 */
    private fun TableState.areaOf(tile: IdentifiedTile): TileArea? = tileArea(tile.id)?.let { TileArea(it.region, it.ownerId) }
}
