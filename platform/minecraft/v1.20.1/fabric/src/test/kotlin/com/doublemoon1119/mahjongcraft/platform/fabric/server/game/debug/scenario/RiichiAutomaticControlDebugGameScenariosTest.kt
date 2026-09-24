package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證自動操作驗收情境的桌況能重現每個控制要觀察的局面。 */
class RiichiAutomaticControlDebugGameScenariosTest {
    private val registry = DebugGameScenarioRegistry()
    private val scenarioValidator = DebugGameScenarioValidator(scenarioModuleRegistry)

    /** 四個情境都登記在 registry 中，且在各種赤寶牌張數下都通過權威驗證。 */
    @Test
    fun `every automatic control scenario is registered and valid`() {
        val ids = RiichiAutomaticControlDebugGameScenarios.all.map { it.id }

        assertEquals(
            listOf(
                "mahjongcraft:automatic_auto_win_tsumo",
                "mahjongcraft:automatic_auto_win_ron",
                "mahjongcraft:automatic_decline_calls_keeps_win",
                "mahjongcraft:automatic_tsumogiri_plain_draw",
            ),
            ids,
        )
        ids.forEach { id ->
            RED_DORA_COUNTS.forEach { redDoraCount ->
                val context = createRiichiScenarioContext(config = RiichiRuleConfig(redDoraCount = redDoraCount))
                val scenario = registry.get(id) ?: error("$id is not registered")
                val result = scenario.build(context)

                scenarioValidator.validate(context, result)
                assertEquals(context.invokingPlayerId, result.game.tableState.currentPlayer.id, id)
                assertTrue(
                    result.game.enabledAutomaticControlIdsByPlayerId.isEmpty(),
                    "$id must not pre-enable any automatic control",
                )
            }
        }
    }

    /** 自動和牌（自摸）：呼叫者的下一次摸牌就是有役的和牌張，中間三家的捨牌不打斷。 */
    @Test
    fun `auto win tsumo scenario draws a winning tile on the next own turn`() {
        val state = build("automatic_auto_win_tsumo").game.tableState

        assertEquals(listOf(NINE_BAMBOO, NINE_CHARACTER, Tile.Honor.North, FOUR_CHARACTER), state.liveWallFront(4))
        (0..2).forEach { index ->
            val discarded = state.liveWallTile(index)
            val reactions = state.reactionsOf(offset = 0, discarderOffset = index + 1, tile = discarded)

            assertTrue(
                reactions.all { it == GameAction.Pass },
                "${discarded.tile} must not interrupt the invoking player before the winning draw",
            )
        }
        assertTrue(GameAction.Tsumo in state.ownTurnActions(offset = 0, drawnTile = state.liveWallTile(3)))
    }

    /** 自動和牌（榮和）：下家的第一張捨牌就是有役的和牌張，且呼叫者沒有振聽。 */
    @Test
    fun `auto win ron scenario offers ron on the first opponent discard`() {
        val state = build("automatic_auto_win_ron").game.tableState

        assertEquals(FOUR_CHARACTER, state.liveWallFront(1).single())
        assertTrue(state.canRon(offset = 0, discarderOffset = 1, tile = FOUR_CHARACTER))
        assertTrue(
            state.seat(0).discardPile.entries.none { it.tile.tile == ONE_CHARACTER || it.tile.tile == FOUR_CHARACTER },
            "the invoking player must not be furiten",
        )
    }

    /** 不吃碰槓：同一張捨牌同時可碰與可榮和，控制只該拿掉碰。 */
    @Test
    fun `decline calls scenario offers pon and ron on the same discard`() {
        val state = build("automatic_decline_calls_keeps_win").game.tableState
        val discarded = state.liveWallTile(0)

        assertEquals(Tile.Numeric(Tile.Suit.Bamboo, 2), discarded.tile)
        val reactions = state.reactionsOf(offset = 0, discarderOffset = 1, tile = discarded)

        assertTrue(
            state.currentOwnTurnActions().isEmpty(),
            "the invoking player must not be offered a riichi declaration that would forbid the pon",
        )
        assertTrue(reactions.any { it is GameAction.Pon }, "the scenario must offer a declinable pon")
        assertTrue(reactions.any { it is GameAction.Ron }, "reactions=$reactions hand=${state.seat(0).hand.standingTiles.map { it.tile }} discards=${state.seat(0).discardPile.entries.map { it.tile.tile }}")
    }

    /** 自動摸切：呼叫者摸到的牌沒有任何特殊選項，摸切才不會蓋掉玩家的決定。 */
    @Test
    fun `tsumogiri scenario leaves the invoking player without any special action`() {
        val state = build("automatic_tsumogiri_plain_draw").game.tableState

        assertEquals(NINE_BAMBOO, state.currentPlayer.hand.lastDrawn?.tile)
        assertEquals(emptyList(), state.currentOwnTurnActions())
    }

    /** 摸到可暗槓的牌時仍有特殊選項，既有的暗槓情境正是這個對照組。 */
    @Test
    fun `an existing closed kan scenario keeps a special action on the same draw`() {
        val state = build("riichi_before_ankan_1").game.tableState
        val actions = state.currentOwnTurnActions()

        assertFalse(actions.isEmpty(), "the closed kan scenario must keep a special action available")
        assertTrue(actions.any { it is GameAction.Kan && it.type == GameAction.KanType.CLOSED_KAN })
    }

    private fun build(
        idPath: String,
        context: DebugGameScenarioContext = createRiichiScenarioContext(),
    ): DebugGameScenarioResult = registry.get("mahjongcraft:$idPath")!!.build(context)

    private companion object {
        /** 房間設定可選的赤寶牌張數。 */
        val RED_DORA_COUNTS: List<Int> = listOf(0, 3, 4)

        val ONE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 1)
        val FOUR_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 4)
        val NINE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 9)
        val NINE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 9)
    }
}
