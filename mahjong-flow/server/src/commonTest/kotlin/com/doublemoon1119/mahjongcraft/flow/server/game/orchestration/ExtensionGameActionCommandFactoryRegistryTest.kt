package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證擴充動作命令 factory 註冊、解析與凍結行為。 */
class ExtensionGameActionCommandFactoryRegistryTest {
    /** 已註冊 factory 應收到原始動作與完整選牌順序。 */
    @Test
    fun `registered factory creates command from selected tiles`() {
        val registry = ExtensionGameActionCommandFactoryRegistry()
        val tileIds = listOf(Uuid.random(), Uuid.random())
        registry.register(TestAction::class) { action, selectedTileIds ->
            TestCommand(action.id, selectedTileIds)
        }

        assertEquals(TestCommand(TestAction.id, tileIds), registry.createCommand(TestAction, tileIds))
        assertTrue(registry.isRegistered(TestAction::class))
    }

    /** 未登記的擴充動作應安全回傳 null。 */
    @Test
    fun `unknown action returns null`() {
        assertNull(ExtensionGameActionCommandFactoryRegistry().createCommand(TestAction, emptyList()))
    }

    /** 同一動作型別不可重複註冊。 */
    @Test
    fun `duplicate action registration is rejected`() {
        val registry = ExtensionGameActionCommandFactoryRegistry()
        registry.register(TestAction::class) { _, _ -> TestCommand(TestAction.id, emptyList()) }

        assertFailsWith<IllegalArgumentException> {
            registry.register(TestAction::class) { _, _ -> TestCommand(TestAction.id, emptyList()) }
        }
    }

    /** 凍結後不可再加入新的 factory。 */
    @Test
    fun `registration after freeze is rejected`() {
        val registry = ExtensionGameActionCommandFactoryRegistry().apply { freeze() }

        assertFailsWith<IllegalStateException> {
            registry.register(TestAction::class) { _, _ -> TestCommand(TestAction.id, emptyList()) }
        }
    }

    /** 內建立直 factory 只接受恰好一張宣告牌。 */
    @Test
    fun `riichi factory requires exactly one selected tile`() {
        val registry = ExtensionGameActionCommandFactoryRegistry().apply {
            registerRiichiGameActionCommandFactory()
        }
        val tileId = Uuid.random()

        assertEquals(RiichiGameCommand(tileId), registry.createCommand(RiichiGameAction.Riichi, listOf(tileId)))
        assertNull(registry.createCommand(RiichiGameAction.Riichi, emptyList()))
        assertNull(registry.createCommand(RiichiGameAction.Riichi, listOf(tileId, Uuid.random())))
    }

    /** 測試用擴充動作。 */
    private data object TestAction : ExtensionGameAction {
        override val id: String = "test:select_tiles"
    }

    /** 測試用擴充命令。 */
    private data class TestCommand(val actionId: String, val selectedTileIds: List<Uuid>) : ExtensionGameCommand
}
