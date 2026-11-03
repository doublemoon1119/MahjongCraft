package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviewRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 客戶端使用相同的登記與凍結規則，不在初始化建立任何模型。 */
class FabricClientCharacterPreviewRegistrationTest {
    /** 工廠只登記，不因登記或診斷而被呼叫。 */
    @Test
    fun `client bootstrap registers definitions without invoking factories`() {
        val registry = ClientAiCharacterPreviewRegistry()
        val builtIn = extension("test:built_in", "test:base")
        val thirdParty = extension("test:third_party", "test:preview")
        val sources = FabricMahjongClientExtensionBootstrap.registerAndFreeze(listOf(thirdParty), registry, builtIn, ExtensionFailurePolicy.Default)
        assertEquals(listOf(null, "test:third_party"), sources.map { it.extensionId })
        assertEquals(listOf("test:base"), sources.first().categories.single().registrationIds)
        assertEquals(listOf("test:preview"), sources.last().categories.single().registrationIds)
        assertEquals(setOf("test:base", "test:preview"), registry.registrationKeys)
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(definition("test:late")) }
    }

    /** 同一 ID 的能力不能由後來的擴充覆寫，失敗來源保留在診斷。 */
    @Test
    fun `duplicate client capability rejects startup with extension identity`() {
        val registry = ClientAiCharacterPreviewRegistry()
        val cause = assertFailsWith<FabricMahjongExtensionRegistrationException> {
            FabricMahjongClientExtensionBootstrap.registerAndFreeze(
                extensions = listOf(extension("test:conflicting", "test:base")),
                registry = registry,
                builtIn = extension("test:built_in", "test:base"),
                failurePolicy = ExtensionFailurePolicy.Default,
            )
        }
        assertTrue(cause.message!!.contains("test:conflicting"))
        assertEquals(false, registry.isFrozen)
    }

    /**
     * 建立不得提前執行的測試工廠。
     *
     * @param id 完整能力識別碼。
     * @return 測試定義。
     */
    private fun definition(id: String): FabricAiCharacterPreviewDefinition = FabricAiCharacterPreviewDefinition(id) { _, _ -> error("Factory must remain deferred") }

    /**
     * 建立只登記一個能力的測試入口。
     *
     * @param id 擴充識別碼。
     * @param capability 能力識別碼。
     * @return 測試入口。
     */
    private fun extension(id: String, capability: String): FabricMahjongClientExtension = object : FabricMahjongClientExtension {
        override val id: String = id

        override fun registerCharacterPreviews(registry: FabricAiCharacterPreviewRegistrar) {
            registry.register(definition(capability))
        }
    }
}
