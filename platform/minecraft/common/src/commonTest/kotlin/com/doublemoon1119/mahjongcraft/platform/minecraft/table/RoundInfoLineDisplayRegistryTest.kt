package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證局況顯示行的翻譯資訊註冊流程。 */
class RoundInfoLineDisplayRegistryTest {
    /** 驗證內建日麻與第三方 key 都透過同一個 registry 解析。 */
    @Test
    fun `built-in and third-party keys resolve registered displays`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            registerBuiltInRiichiRoundInfoLineDisplays()
            register("example:custom", RoundInfoLineDisplay("example.message.custom"))
        }

        val title = registry.find(RiichiRuleModule.TITLE_KEY)
        assertEquals(
            listOf(RoundInfoLineArgumentKind.WIND, RoundInfoLineArgumentKind.NUMBER, RoundInfoLineArgumentKind.NUMBER),
            title?.argumentKinds,
        )
        assertEquals("example.message.custom", registry.find("example:custom")?.translationKey)
        assertNull(registry.find("example:unknown"))
    }

    /** 驗證 registry 凍結後禁止第三方延遲修改。 */
    @Test
    fun `frozen registry rejects late registration`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply { freeze() }

        assertFailsWith<IllegalStateException> {
            registry.register("example:late", RoundInfoLineDisplay("example.message.late"))
        }
    }

    /** 驗證重複登記同一個 key 會被拒絕，避免第三方無意間覆蓋內建顯示。 */
    @Test
    fun `duplicate key registration is rejected`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            register("example:duplicate", RoundInfoLineDisplay("example.message.a"))
        }

        assertFailsWith<IllegalArgumentException> {
            registry.register("example:duplicate", RoundInfoLineDisplay("example.message.b"))
        }
    }
}
