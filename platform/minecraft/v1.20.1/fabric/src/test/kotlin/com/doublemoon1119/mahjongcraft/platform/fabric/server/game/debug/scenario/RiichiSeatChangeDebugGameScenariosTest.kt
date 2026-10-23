package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugWinnerDealerRiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證和牌者坐莊、每局換位情境的排列。 */
class RiichiSeatChangeDebugGameScenariosTest {
    /** 載入後改用開發用規則；下家用和牌優先的腳本 AI，可以榮和呼叫者剛摸到的西，其他家不行。 */
    @Test
    fun `winner dealer scenario lets the downstream player ron the drawn west`() {
        val registry = DebugGameScenarioRegistry()
        val result = registry.get("mahjongcraft:winner_dealer_seat_rotation")!!.build(createRiichiScenarioContext())
        val state = result.game.tableState

        assertIs<DebugWinnerDealerRiichiRuleConfig>(state.config)
        assertEquals(Tile.Honor.West, state.currentPlayer.hand.lastDrawn?.tile)
        assertEquals(DebugScriptedAiStrategy.WIN_FIRST_KEY, result.game.aiPlayerStrategyKeys[state.seat(1).id])
        assertTrue(state.canRon(offset = 1, discarderOffset = 0, tile = Tile.Honor.West))
        assertFalse(state.canRon(offset = 2, discarderOffset = 0, tile = Tile.Honor.West))
    }
}
