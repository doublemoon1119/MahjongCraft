package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricAiCharacterCapabilityRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricRoomParticipantRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatPlacementRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistryImpl
import net.minecraft.item.ItemStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 放置能力必須遵守登記唯一性、凍結及安全排序。 */
class FabricSeatPlacementRegistrationTest {
    /** 不執行世界操作的提供者。 */
    private val provider = object : FabricSeatPlacementProvider {
        override fun supports(stack: ItemStack): Boolean = false

        override fun place(context: FabricSeatPlacementContext): Boolean = false
    }

    /** 同優先值依完整 ID 排序，極值不以相減比較。 */
    @Test
    fun `definitions are ordered by descending priority and stable id`() {
        val registry = FabricSeatPlacementRegistry()
        registry.register(
            definition = FabricSeatPlacementDefinition(
                id = "example:z",
                provider = provider,
                priority = Int.MIN_VALUE,
            ),
        )
        registry.register(
            definition = FabricSeatPlacementDefinition(
                id = "example:b",
                provider = provider,
                priority = 0,
            ),
        )
        registry.register(
            definition = FabricSeatPlacementDefinition(
                id = "example:a",
                provider = provider,
                priority = 0,
            ),
        )
        registry.register(
            definition = FabricSeatPlacementDefinition(
                id = "example:first",
                provider = provider,
                priority = Int.MAX_VALUE,
            ),
        )
        assertEquals(listOf("example:first", "example:a", "example:b", "example:z"), registry.entries().map { it.id })
    }

    /** 外部取得的 keys 不受後續登記改動，重複與凍結後登記都拒絕。 */
    @Test
    fun `registry snapshots are detached and late registrations are rejected`() {
        val registry = FabricSeatPlacementRegistry()
        registry.register(FabricSeatPlacementDefinition("example:first", provider))
        val keys = registry.registrationKeys
        registry.register(FabricSeatPlacementDefinition("example:second", provider))
        assertEquals(setOf("example:first"), keys)
        assertFailsWith<IllegalArgumentException> { registry.register(FabricSeatPlacementDefinition("example:first", provider)) }
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(FabricSeatPlacementDefinition("example:late", provider)) }
        assertFailsWith<IllegalArgumentException> { FabricSeatPlacementDefinition("invalid", provider) }
    }

    /** 內建與第三方共用初始化路徑，來源診斷正確且完成後不可再登記。 */
    @Test
    fun `bootstrap records placement sources and freezes the registrar`() {
        val registries = FabricExtensionRegistries(
            tablePropKindRegistry = FabricTablePropKindRegistryImpl(),
            seatProviderRegistry = FabricSeatProviderRegistryImpl(),
            characterCapabilityRegistry = FabricAiCharacterCapabilityRegistry(),
            seatPlacementRegistry = FabricSeatPlacementRegistry(),
            roomParticipantRegistry = FabricRoomParticipantRegistry(),
        )
        val builtIn = object : FabricMahjongExtension {
            override val id: String = "example:builtin"

            override fun registerSeatPlacements(registry: FabricSeatPlacementRegistrar) {
                registry.register(FabricSeatPlacementDefinition("example:builtin-seat", provider))
            }
        }
        val extension = object : FabricMahjongExtension {
            override val id: String = "example:extension"

            override fun registerSeatPlacements(registry: FabricSeatPlacementRegistrar) {
                assertTrue("example:builtin-seat" in registry.registrationKeys)
                registry.register(FabricSeatPlacementDefinition("example:third-party-seat", provider))
            }
        }
        val sources = FabricMahjongExtensionBootstrap.registerAndFreeze(
            extensions = listOf(extension),
            registries = registries,
            builtIn = builtIn,
            failurePolicy = ExtensionFailurePolicy.Default,
        )
        assertEquals(listOf(null, "example:extension"), sources.map { it.extensionId })
        assertEquals(listOf("example:third-party-seat"), sources.last().categories.single().registrationIds)
        assertTrue(registries.seatPlacementRegistry.isFrozen)
    }

    /** 對外情境無 Java 可見建構子，內部建立入口保持 synthetic。 */
    @Test
    fun `placement context cannot be constructed through the public Java API`() {
        val type = FabricSeatPlacementContext::class.java
        assertTrue(type.constructors.none { !it.isSynthetic })
        val factory = type.declaredClasses.single { it.simpleName == "Companion" }
        val methods = factory.declaredMethods.filter { it.name.startsWith("create") }
        assertTrue(methods.isNotEmpty())
        assertTrue(methods.all { it.isSynthetic })
    }
}
