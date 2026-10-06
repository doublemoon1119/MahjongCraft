package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證擴充動作 AI handler 收到的規則模組與這一局的設定一致。 */
class ExtensionGameActionAiRegistryTest {
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** handler 收到的規則模組依決策情境的對局設定解析，不同規則的對局各自拿到自己的模組。 */
    @Test
    fun `handler receives the rule module of the game`() {
        val receivedModules = mutableListOf<MahjongRuleModule<*>>()
        val registry = ExtensionGameActionAiRegistry(moduleRegistry).apply {
            register(TestAction::class) { _, _, module ->
                receivedModules += module
                emptyList()
            }
            freeze()
        }
        val riichiConfig = RiichiRuleConfig()
        val taiwanConfig = TaiwanRuleConfig()

        registry.createCandidates(TestAction, contextWith(riichiConfig))
        registry.createCandidates(TestAction, contextWith(taiwanConfig))

        assertEquals(listOf(riichiConfig, taiwanConfig), receivedModules.map { it.config })
    }

    /** 沒有登記 handler 的動作不解析規則模組，直接回傳空清單。 */
    @Test
    fun `unregistered action returns no candidates without resolving a module`() {
        val registry = ExtensionGameActionAiRegistry(MahjongModuleRegistryImpl()).apply { freeze() }

        assertEquals(emptyList(), registry.createCandidates(TestAction, contextWith(RiichiRuleConfig())))
        assertNull(registry.registrationKeys.firstOrNull())
    }

    /** 以 [config] 對局建立的決策情境。 */
    private fun contextWith(config: MahjongRuleConfig): AiDecisionContext {
        val selfId = Uuid.random()
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create(id = selfId)), config = config)
        return AiDecisionContext(
            snapshot = table.toSnapshot(setOf(selfId)),
            selfId = selfId,
            phase = AiDecisionPhase.OwnTurn,
            legalActions = emptyList(),
        )
    }

    /** 測試用擴充動作。 */
    private data object TestAction : ExtensionGameAction {
        override val id: String = "test:action"
    }
}
