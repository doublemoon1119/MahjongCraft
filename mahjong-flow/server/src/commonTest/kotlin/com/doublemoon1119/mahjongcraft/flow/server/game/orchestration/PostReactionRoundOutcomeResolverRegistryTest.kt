package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [PostReactionRoundOutcomeResolverRegistry] 的順序、規則隔離與凍結測試。 */
class PostReactionRoundOutcomeResolverRegistryTest {
    /** 驗證 resolver 依 priority、ID 穩定排序，且只執行目前規則的項目。 */
    @Test
    fun `resolvers use stable order and matching rule module`() {
        val calls = mutableListOf<String>()
        val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
        val registry = PostReactionRoundOutcomeResolverRegistry().apply {
            register(recordingResolver("test:z", module.id, 20, calls))
            register(recordingResolver("test:foreign", "test:foreign_rule", 0, calls))
            register(recordingResolver("test:b", module.id, 10, calls))
            register(recordingResolver("test:a", module.id, 10, calls))
            freeze()
        }

        registry.resolve(FakeTableStateFactory.create(config = RiichiRuleConfig()), module)

        assertEquals(listOf("test:a", "test:b", "test:z"), calls)
    }

    /** 驗證凍結後拒絕任何額外登記。 */
    @Test
    fun `registration is rejected after freeze`() {
        val registry = PostReactionRoundOutcomeResolverRegistry().apply { freeze() }
        assertFailsWith<IllegalStateException> {
            registry.register(recordingResolver("test:late", "test:rule", 0, mutableListOf()))
        }
    }

    /** 建立只記錄呼叫、不產生 outcome 的測試 resolver。 */
    private fun recordingResolver(
        id: String,
        ruleModuleId: String,
        priority: Int,
        calls: MutableList<String>,
    ): PostReactionRoundOutcomeResolver = object : PostReactionRoundOutcomeResolver {
        override val id: String = id
        override val ruleModuleId: String = ruleModuleId
        override val priority: Int = priority

        override fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome? {
            calls += id
            return null
        }
    }
}
