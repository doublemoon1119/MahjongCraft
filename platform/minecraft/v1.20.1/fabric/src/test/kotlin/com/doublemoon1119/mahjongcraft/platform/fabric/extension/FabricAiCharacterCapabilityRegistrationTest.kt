package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterCapabilityDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterContext
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricAiCharacterCapabilityRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricRoomParticipantRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatPlacementRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistryImpl
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 驗證角色能力與其他 registry 共用的登記契約，以及公開請求的 Java 可見性。 */
class FabricAiCharacterCapabilityRegistrationTest {
    /** 能力自帶完整身分，登記後供執行期查詢。 */
    @Test
    fun `capability registration is namespaced unique and frozen`() {
        val registry = FabricAiCharacterCapabilityRegistry()
        val definition = FabricAiCharacterCapabilityDefinition("example:actor", FabricAiCharacterProvider { null })
        registry.register(definition)
        assertSame(definition, registry.find(definition.id))
        assertEquals(setOf("example:actor"), registry.registrationKeys)
        assertFailsWith<IllegalArgumentException> { registry.register(definition) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register(FabricAiCharacterCapabilityDefinition("example:late", FabricAiCharacterProvider { null })) }
        assertFailsWith<IllegalArgumentException> { FabricAiCharacterCapabilityDefinition("invalid", FabricAiCharacterProvider { null }) }
    }

    /** 第三方回呼可看到內建能力，同一條 bootstrap 記錄來源並凍結登記。 */
    @Test
    fun `bootstrap reports third party character capabilities and freezes the registrar`() {
        val registries = registries()
        val builtIn = object : FabricMahjongExtension {
            override val id: String = "example:builtin"

            override fun registerAiCharacterCapabilities(registry: FabricAiCharacterCapabilityRegistrar) {
                registry.register(FabricAiCharacterCapabilityDefinition("example:builtin-actor", FabricAiCharacterProvider { null }))
            }
        }
        val extension = object : FabricMahjongExtension {
            override val id: String = "example:extension"

            override fun registerAiCharacterCapabilities(registry: FabricAiCharacterCapabilityRegistrar) {
                assertTrue("example:builtin-actor" in registry.registrationKeys)
                registry.register(FabricAiCharacterCapabilityDefinition("example:third-party-actor", FabricAiCharacterProvider { null }))
            }
        }
        val sources = FabricMahjongExtensionBootstrap.registerAndFreeze(
            extensions = listOf(extension),
            registries = registries,
            builtIn = builtIn,
            failurePolicy = ExtensionFailurePolicy.Default,
        )
        assertEquals(listOf(null, "example:extension"), sources.map { it.extensionId })
        assertEquals(listOf("example:third-party-actor"), sources.last().categories.single().registrationIds)
        assertTrue(registries.characterCapabilityRegistry.isFrozen)
    }

    /** 重複能力的錯誤須標示實際提供者，不能只留下原始重複 ID。 */
    @Test
    fun `duplicate character capability reports its extension source`() {
        val first = object : FabricMahjongExtension {
            override val id: String = "example:first"

            override fun registerAiCharacterCapabilities(registry: FabricAiCharacterCapabilityRegistrar) {
                registry.register(FabricAiCharacterCapabilityDefinition("example:duplicate", FabricAiCharacterProvider { null }))
            }
        }
        val second = object : FabricMahjongExtension {
            override val id: String = "example:second"

            override fun registerAiCharacterCapabilities(registry: FabricAiCharacterCapabilityRegistrar) {
                registry.register(FabricAiCharacterCapabilityDefinition("example:duplicate", FabricAiCharacterProvider { null }))
            }
        }
        val failure = assertFailsWith<FabricMahjongExtensionRegistrationException> {
            FabricMahjongExtensionBootstrap.registerAndFreeze(
                extensions = listOf(second),
                registries = registries(),
                builtIn = first,
                failurePolicy = ExtensionFailurePolicy.Default,
            )
        }
        assertTrue(failure.message.orEmpty().contains("example:second"))
        assertTrue(failure.cause is IllegalArgumentException)
    }

    /** 執行期缺少能力可降級為無世界角色。 */
    @Test
    fun `unknown capability has no guessed provider`() {
        assertNull(FabricAiCharacterCapabilityRegistry().find("example:absent"))
    }

    /** 只讀請求不提供 Java 建構入口，內部工廠為 synthetic。 */
    @Test
    fun `character context has no Java callable construction entry point`() {
        val type = FabricAiCharacterContext::class.java
        assertTrue(type.constructors.all { it.isSynthetic || !Modifier.isPublic(it.modifiers) })
        val factories = FabricAiCharacterContext.Companion::class.java.declaredMethods.filter { it.name.startsWith("create") }
        assertTrue(factories.isNotEmpty())
        assertTrue(factories.all { it.isSynthetic })
    }

    /**
     * 建立不共享全域狀態的原生登記集合。
     *
     * @return 本次測試獨立使用的 registry。
     */
    private fun registries(): FabricExtensionRegistries = FabricExtensionRegistries(
        tablePropKindRegistry = FabricTablePropKindRegistryImpl(),
        seatProviderRegistry = FabricSeatProviderRegistryImpl(),
        characterCapabilityRegistry = FabricAiCharacterCapabilityRegistry(),
        seatPlacementRegistry = FabricSeatPlacementRegistry(),
        roomParticipantRegistry = FabricRoomParticipantRegistry(),
    )
}
