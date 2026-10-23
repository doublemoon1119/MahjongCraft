package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.DebugNotenDeclarationRuleDecisionResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugNotenDeclarationRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugNotenDeclarationRiichiRuleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證可宣告不聽情境的排列。 */
class RiichiRuleDecisionDebugGameScenariosTest {
    /** 活牌只剩三張東；流局時呼叫者與下家可以宣告不聽，已立直的對家與不聽的上家不參與。 */
    @Test
    fun `noten declaration scenario reaches an exhaustive draw with two players to choose`() {
        val result = DebugGameScenarioRegistry().get("mahjongcraft:noten_declaration_exhaustive_draw")!!.build(createRiichiScenarioContext())
        val state = result.game.tableState
        val config = assertIs<DebugNotenDeclarationRiichiRuleConfig>(state.config)

        assertEquals(Tile.Honor.West, state.currentPlayer.hand.lastDrawn?.tile)
        assertEquals(listOf(Tile.Honor.East, Tile.Honor.East, Tile.Honor.East), state.liveWallFront(state.tileWall.remainingCount))
        assertTrue((state.seat(2).playerRuleState as RiichiPlayerState).isRiichi)

        val afterDiscard = state.copy(
            players = state.players.map { player -> player.copy(hand = player.hand.copy(lastDrawn = null)) },
        )
        val module = DebugNotenDeclarationRiichiRuleModule(DebugNotenDeclarationRiichiRuleModule.ID, config)
        val step = assertNotNull(
            DebugNotenDeclarationRuleDecisionResolver().begin(RuleDecisionTiming.BEFORE_EXHAUSTIVE_DRAW, afterDiscard, module),
        )
        assertEquals(setOf(state.seat(0).id, state.seat(1).id), step.inputSpecsByPlayerId.keys)
    }
}
