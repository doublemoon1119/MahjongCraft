package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [PostActionExhaustiveDrawResolverRegistry] 的順序、規則隔離與凍結測試。 */
class PostActionExhaustiveDrawResolverRegistryTest {
    /** 驗證 resolver 依 priority、ID 穩定排序，且只執行目前規則的項目。 */
    @Test
    fun `resolvers use stable order and matching rule module`() {
        val calls = mutableListOf<String>()
        val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
        val actorPlayerId = Uuid.random()
        val context = CompletedGameActionContext(
            actorPlayerId = actorPlayerId,
            action = GameAction.Discard(Uuid.random()),
            tableState = FakeTableStateFactory.create(config = RiichiRuleConfig()),
        )
        val registry = PostActionExhaustiveDrawResolverRegistry().apply {
            register(recordingResolver("test:z", module.id, 20, calls))
            register(recordingResolver("test:foreign", "test:foreign_rule", 0, calls))
            register(recordingResolver("test:b", module.id, 10, calls))
            register(recordingResolver("test:a", module.id, 10, calls))
            freeze()
        }

        registry.resolve(context, module)

        assertEquals(listOf("test:a", "test:b", "test:z"), calls)
    }

    /** 驗證凍結後拒絕任何額外登記。 */
    @Test
    fun `registration is rejected after freeze`() {
        val registry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() }
        assertFailsWith<IllegalStateException> {
            registry.register(recordingResolver("test:late", "test:rule", 0, mutableListOf()))
        }
    }

    /** 驗證同一個 ID 不得重複登記。 */
    @Test
    fun `duplicate id is rejected`() {
        val registry = PostActionExhaustiveDrawResolverRegistry()
        registry.register(recordingResolver("test:dup", "test:rule", 0, mutableListOf()))

        assertFailsWith<IllegalArgumentException> {
            registry.register(recordingResolver("test:dup", "test:rule", 0, mutableListOf()))
        }
    }

    /** 驗證第三方 resolver 可直接辨認自己的 extension action，不需要擴充通用 context 型別。 */
    @Test
    fun `extension action is delivered through generic context`() {
        val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
        val extensionAction = TestExtensionAction
        var received = false
        val registry = PostActionExhaustiveDrawResolverRegistry().apply {
            register(
                object : PostActionExhaustiveDrawResolver {
                    override val id: String = "test:extension_action"
                    override val ruleModuleId: String = module.id
                    override val priority: Int = 0

                    override fun resolve(
                        context: CompletedGameActionContext,
                        ruleModule: MahjongRuleModule<*>,
                    ): ExhaustiveDrawReason? {
                        received = context.action == GameAction.Extension(extensionAction)
                        return null
                    }
                },
            )
            freeze()
        }

        registry.resolve(
            CompletedGameActionContext(
                actorPlayerId = Uuid.random(),
                action = GameAction.Extension(extensionAction),
                tableState = FakeTableStateFactory.create(config = RiichiRuleConfig()),
            ),
            module,
        )

        assertTrue(received)
    }

    /** 建立只記錄呼叫、不產生流局原因的測試 resolver。 */
    private fun recordingResolver(
        id: String,
        ruleModuleId: String,
        priority: Int,
        calls: MutableList<String>,
    ): PostActionExhaustiveDrawResolver = object : PostActionExhaustiveDrawResolver {
        override val id: String = id
        override val ruleModuleId: String = ruleModuleId
        override val priority: Int = priority

        override fun resolve(
            context: CompletedGameActionContext,
            ruleModule: MahjongRuleModule<*>,
        ): ExhaustiveDrawReason? {
            calls += id
            return null
        }
    }

    /** Registry 通用 context 測試使用的第三方動作。 */
    private data object TestExtensionAction : ExtensionGameAction {
        override val id: String = "test:post_action"
    }
}
