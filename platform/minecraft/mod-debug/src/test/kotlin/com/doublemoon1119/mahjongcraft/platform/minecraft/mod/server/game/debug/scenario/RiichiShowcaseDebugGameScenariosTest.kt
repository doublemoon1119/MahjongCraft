package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證宣傳截圖情境的桌面像打到中盤，而且和牌情境可以自摸。 */
class RiichiShowcaseDebugGameScenariosTest {
    /** 提供宣傳截圖情境的登錄表。 */
    private val registry = DebugGameScenarioRegistry()

    /** 檢查情境結果是否符合對局約束。 */
    private val scenarioValidator = DebugGameScenarioValidator(scenarioModuleRegistry)

    /** 兩個情境都登記在 registry 中，且在各種赤寶牌張數下都通過權威驗證。 */
    @Test
    fun `showcase scenarios are registered and valid`() {
        listOf(MIDGAME_ID, TSUMO_ID).forEach { id ->
            RED_DORA_COUNTS.forEach { redDoraCount ->
                val context = createRiichiScenarioContext(config = RiichiRuleConfig(redDoraCount = redDoraCount))
                val result = assertNotNull(registry.get(id), id).build(context)

                scenarioValidator.validate(context, result)
                assertEquals(context.invokingPlayerId, result.game.tableState.currentPlayer.id, id)
            }
        }
    }

    /** 每家牌河都有七張以上，對家立直並付出立直棒，呼叫者從上家碰了發。 */
    @Test
    fun `the midgame table has full rivers, a riichi and a pon`() {
        val state = build(MIDGAME_ID)

        assertEquals(listOf(8, 8, 8, 7), (0..3).map { offset -> state.seat(offset).discardPile.entries.size })
        assertNotNull(state.seat(2).riichiDeclaration)
        listOf(0, 1, 3).forEach { offset -> assertNull(state.seat(offset).riichiDeclaration, "seat $offset") }
        assertEquals(listOf(Tile.Honor.Green), state.currentPlayer.hand.melds.map { it.tiles.first().tile })
    }

    /** 全景情境剛摸到西風，還不能自摸。 */
    @Test
    fun `the midgame hand cannot win yet`() {
        val state = build(MIDGAME_ID)

        assertEquals(Tile.Honor.West, state.currentPlayer.hand.lastDrawn?.tile)
        assertFalse(GameAction.Tsumo in state.currentOwnTurnActions())
    }

    /** 和牌情境剛摸到二條，可以自摸。 */
    @Test
    fun `the tsumo hand can win on the drawn tile`() {
        val state = build(TSUMO_ID)

        assertEquals(TWO_BAMBOO, state.currentPlayer.hand.lastDrawn?.tile)
        assertTrue(GameAction.Tsumo in state.currentOwnTurnActions())
    }

    /**
     * 由 registry 建立指定情境的桌況。
     *
     * @param id 情境的完整識別碼。
     * @return 建立後的對局桌況。
     */
    private fun build(id: String): TableState = registry.get(id)!!.build(createRiichiScenarioContext()).game.tableState

    /** 宣傳截圖情境共用的識別碼與牌面。 */
    private companion object {
        /** 牌桌全景使用的情境。 */
        const val MIDGAME_ID: String = "mahjongcraft:riichi/debug/showcase_midgame"

        /** 和牌演出使用的情境。 */
        const val TSUMO_ID: String = "mahjongcraft:riichi/debug/showcase_tsumo"

        /** 四人日麻房間設定可選的赤寶牌張數。 */
        val RED_DORA_COUNTS: List<Int> = listOf(0, 3, 4)

        /** 二條，和牌情境剛摸到的牌。 */
        val TWO_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 2)
    }
}
