package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.applyRiichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證一發驗收情境的桌況能讓呼叫者在立直後一巡內和牌。 */
class RiichiIppatsuDebugGameScenariosTest {
    private val registry = DebugGameScenarioRegistry()
    private val scenarioValidator = DebugGameScenarioValidator(
        MahjongModuleRegistryImpl().apply {
            registerBundledRuleModules()
            freeze()
        },
    )

    /** 所有一發情境在各種赤寶牌張數下都通過權威驗證，呼叫者可以用剛摸到的西風宣告立直，且不是雙立直。 */
    @Test
    fun `every ippatsu scenario is valid and lets the invoking player declare riichi with the drawn tile`() {
        RiichiIppatsuDebugGameScenarios.all.forEach { scenario ->
            RED_DORA_COUNTS.forEach { redDoraCount ->
                val context = createRiichiScenarioContext(config = RiichiRuleConfig(redDoraCount = redDoraCount))
                val result = scenario.build(context)
                scenarioValidator.validate(context, result)
                val state = result.game.tableState
                val drawnTile = assertNotNull(state.currentPlayer.hand.lastDrawn, scenario.id)

                assertEquals(context.invokingPlayerId, state.currentPlayer.id, scenario.id)
                assertEquals(Tile.Honor.West, drawnTile.tile, scenario.id)
                assertTrue(RIICHI_GAME_ACTION in state.currentOwnTurnActions(), scenario.id)
                val riichiState = state.afterInvokerRiichi().seat(0).playerRuleState as RiichiPlayerState
                assertTrue(riichiState.isIppatsu, scenario.id)
                assertNull(riichiState.doubleRiichiTile, "${scenario.id} should not be a double riichi")
                assertTrue(
                    state.reservedWallTiles.none { it.tile == ONE_CHARACTER || it.tile == FOUR_CHARACTER },
                    "${scenario.id} should keep the waits out of the dead wall",
                )
            }
        }
    }

    /** 自摸一發：三家摸切的牌都不是和牌張，呼叫者立直後的下一次摸牌是四萬，可以自摸。 */
    @Test
    fun `tsumo scenario brings the wait on the first draw after riichi`() {
        val state = build("riichi_ippatsu_tsumo").afterInvokerRiichi()

        assertEquals(listOf(Tile.Honor.North, Tile.Honor.White, NINE_CHARACTER, FOUR_CHARACTER), state.liveWallFront(4))
        (1..3).forEach { discarderOffset ->
            assertFalse(state.canRon(offset = 0, discarderOffset = discarderOffset, tile = state.liveWallTile(discarderOffset - 1).tile))
        }
        assertTrue(GameAction.Tsumo in state.ownTurnActions(offset = 0, drawnTile = state.liveWallTile(3)))
    }

    /** 榮和一發：下家摸到四萬立刻打出，呼叫者可以榮和。 */
    @Test
    fun `ron scenario lets the downstream player discard the wait right after riichi`() {
        val state = build("riichi_ippatsu_ron").afterInvokerRiichi()

        assertEquals(listOf(FOUR_CHARACTER), state.liveWallFront(1))
        assertTrue(state.canRon(offset = 0, discarderOffset = 1, tile = FOUR_CHARACTER))
    }

    private fun build(idPath: String): TableState = registry.get("mahjongcraft:$idPath")!!
        .build(createRiichiScenarioContext())
        .game
        .tableState

    /** 呼叫者以剛摸到的牌宣告立直後的桌況；回合位置不變，座位偏移仍以呼叫者為 0。 */
    private fun TableState.afterInvokerRiichi(): TableState {
        val invoker = currentPlayer
        val discardResult = assertNotNull(invoker.hand.discardById(assertNotNull(invoker.hand.lastDrawn).id))
        val declaration = assertNotNull(applyRiichiDeclaration(this, invoker, discardResult))
        return copy(
            players = players.map { if (it.id == invoker.id) declaration.player else it },
            dynamicRuleState = declaration.dynamicRuleState,
        )
    }

    private companion object {
        /** 房間設定可選的赤寶牌張數。 */
        val RED_DORA_COUNTS: List<Int> = listOf(0, 3, 4)

        val ONE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 1)
        val FOUR_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 4)
        val NINE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 9)
    }
}
