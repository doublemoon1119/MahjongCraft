package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證情境載入不會讓本局自動操作的生命週期倒退。 */
class DebugScenarioAutomaticControlsTest {
    private val registry = DebugGameScenarioRegistry()

    /** 載入沿用玩家已啟用的控制，版本號繼續往前一格。 */
    @Test
    fun `loading keeps the enabled controls and advances the revision`() {
        val context = createRiichiScenarioContext()
        val playerId = context.invokingPlayerId
        val enabled = setOf(BuiltInAutomaticControlIds.AUTO_WIN)
        val currentGame = context.currentGame.copy(
            enabledAutomaticControlIdsByPlayerId = mapOf(playerId to enabled),
            automaticControlRevision = 4L,
        )
        val scenario = registry.get("mahjongcraft:automatic_auto_win_tsumo")!!

        val loaded = scenario
            .build(DebugGameScenarioContext(currentGame, playerId))
            .continueAutomaticControls(currentGame)

        assertEquals(mapOf(playerId to enabled), loaded.game.enabledAutomaticControlIdsByPlayerId)
        assertEquals(5L, loaded.game.automaticControlRevision)
    }

    /** 玩家沒有啟用任何控制時，載入後仍讓版本號前進，客戶端才收得到這份新快照。 */
    @Test
    fun `loading advances the revision even without any enabled control`() {
        val context = createRiichiScenarioContext()
        val currentGame = context.currentGame.copy(automaticControlRevision = 2L)
        val scenario = registry.get("mahjongcraft:automatic_tsumogiri_plain_draw")!!

        val loaded = scenario
            .build(DebugGameScenarioContext(currentGame, context.invokingPlayerId))
            .continueAutomaticControls(currentGame)

        assertTrue(loaded.game.enabledAutomaticControlIdsByPlayerId.isEmpty())
        assertEquals(3L, loaded.game.automaticControlRevision)
    }

    /** 情境自行建立的遊戲物件會把版本號歸零，因此每個情境都必須經過這一步。 */
    @Test
    fun `a freshly built scenario would otherwise reset the revision`() {
        val context = createRiichiScenarioContext()
        val currentGame = context.currentGame.copy(automaticControlRevision = 7L)
        val scenario = registry.get("mahjongcraft:automatic_auto_win_ron")!!

        val built = scenario.build(DebugGameScenarioContext(currentGame, context.invokingPlayerId))

        assertEquals(0L, built.game.automaticControlRevision)
    }
}
