package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.AiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.TablePropPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.TablePropPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tableprop.ClientTablePropPreviewRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 客戶端使用相同的登記與凍結規則，不在初始化建立任何模型。 */
class ClientCharacterPreviewRegistrationTest {
    /** 工廠只登記，不因登記或診斷而被呼叫。 */
    @Test
    fun `client bootstrap registers definitions without invoking factories`() {
        val registry = ClientAiCharacterPreviewRegistry()
        val builtIn = extension("test:built_in", "test:base")
        val thirdParty = extension("test:third_party", "test:preview")
        val propRegistry = ClientTablePropPreviewRegistry()
        val sources = NativeMahjongClientExtensionBootstrap.registerAndFreeze(listOf(thirdParty), registry, propRegistry, builtIn, ExtensionFailurePolicy.Default)
        assertEquals(listOf(null, "test:third_party"), sources.map { it.extensionId })
        assertEquals(listOf("test:base"), sources.first().categories.single().registrationIds)
        assertEquals(listOf("test:preview"), sources.last().categories.single().registrationIds)
        assertEquals(setOf("test:base", "test:preview"), registry.registrationKeys)
        assertTrue(registry.isFrozen)
        assertTrue(propRegistry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(definition("test:late")) }
    }

    /** 同一 ID 的能力不能由後來的擴充覆寫，失敗來源保留在診斷。 */
    @Test
    fun `duplicate client capability rejects startup with extension identity`() {
        val registry = ClientAiCharacterPreviewRegistry()
        val cause = assertFailsWith<NativeMahjongExtensionRegistrationException> {
            NativeMahjongClientExtensionBootstrap.registerAndFreeze(
                extensions = listOf(extension("test:conflicting", "test:base")),
                registry = registry,
                tablePropRegistry = ClientTablePropPreviewRegistry(),
                builtIn = extension("test:built_in", "test:base"),
                failurePolicy = ExtensionFailurePolicy.Default,
            )
        }
        assertTrue(cause.message!!.contains("test:conflicting"))
        assertEquals(false, registry.isFrozen)
    }

    /** 桌上物件預覽與角色預覽一起登記、一起凍結，登記報告列出各來源新增的種類。 */
    @Test
    fun `client bootstrap registers table prop previews without invoking factories`() {
        val registry = ClientAiCharacterPreviewRegistry()
        val propRegistry = ClientTablePropPreviewRegistry()
        val propExtension = object : NativeMahjongClientExtension {
            override val id: String = "test:props"

            override fun registerCharacterPreviews(registry: AiCharacterPreviewRegistrar) = Unit

            override fun registerTablePropPreviews(registry: TablePropPreviewRegistrar) {
                registry.register(TablePropPreviewDefinition(BuiltInTablePropDefinitions.SCORING_STICK) { _, _ -> error("Factory must remain deferred") })
            }
        }
        val sources = NativeMahjongClientExtensionBootstrap.registerAndFreeze(
            extensions = listOf(propExtension),
            registry = registry,
            tablePropRegistry = propRegistry,
            builtIn = extension("test:built_in", "test:base"),
            failurePolicy = ExtensionFailurePolicy.Default,
        )
        assertEquals(expected = listOf(BuiltInTablePropDefinitions.SCORING_STICK.id), actual = sources.last().categories.single().registrationIds)
        assertEquals(expected = setOf(BuiltInTablePropDefinitions.SCORING_STICK.id), actual = propRegistry.definitions.keys)
        assertTrue(propRegistry.isFrozen)
        assertFailsWith<IllegalArgumentException> {
            ClientTablePropPreviewRegistry().apply {
                register(TablePropPreviewDefinition(BuiltInTablePropDefinitions.SCORING_STICK) { _, _ -> null })
                register(TablePropPreviewDefinition(BuiltInTablePropDefinitions.SCORING_STICK) { _, _ -> null })
            }
        }
    }

    /**
     * 建立不得提前執行的測試工廠。
     *
     * @param id 完整能力識別碼。
     * @return 測試定義。
     */
    private fun definition(id: String): AiCharacterPreviewDefinition = AiCharacterPreviewDefinition(id) { _, _ -> error("Factory must remain deferred") }

    /**
     * 建立只登記一個能力的測試入口。
     *
     * @param id 擴充識別碼。
     * @param capability 能力識別碼。
     * @return 測試入口。
     */
    private fun extension(id: String, capability: String): NativeMahjongClientExtension = object : NativeMahjongClientExtension {
        override val id: String = id

        override fun registerCharacterPreviews(registry: AiCharacterPreviewRegistrar) {
            registry.register(definition(capability))
        }
    }
}
