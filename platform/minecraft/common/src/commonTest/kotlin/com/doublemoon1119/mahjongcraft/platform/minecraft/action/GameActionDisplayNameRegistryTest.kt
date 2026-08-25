package com.doublemoon1119.mahjongcraft.platform.minecraft.action

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證規則擴充動作的 Minecraft 顯示名稱註冊流程。 */
class GameActionDisplayNameRegistryTest {
    /** 驗證內建立直與第三方動作都透過相同 ID registry 解析。 */
    @Test
    fun `built-in and third-party actions resolve registered translation keys`() {
        val customAction = TestExtensionGameAction("example:flower")
        val registry = GameActionDisplayNameRegistryImpl().apply {
            registerRiichiGameActionDisplayName()
            register(customAction.id, "example.message.flower")
        }

        assertEquals("mahjongcraft.message.game_action_riichi", registry.find(RiichiGameAction.Riichi))
        assertEquals("example.message.flower", registry.find(customAction))
        assertNull(registry.find(TestExtensionGameAction("example:unknown")))
    }

    /** 驗證 registry 凍結後禁止第三方延遲修改。 */
    @Test
    fun `frozen registry rejects late registration`() {
        val registry = GameActionDisplayNameRegistryImpl().apply { freeze() }

        assertFailsWith<IllegalStateException> {
            registry.register("example:late", "example.message.late")
        }
    }
}

/** 測試使用的規則擴充動作。 */
private data class TestExtensionGameAction(override val id: String) : ExtensionGameAction
