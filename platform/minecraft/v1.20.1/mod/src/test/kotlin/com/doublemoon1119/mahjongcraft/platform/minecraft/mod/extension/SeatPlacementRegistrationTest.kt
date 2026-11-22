package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.SeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.SeatPlacementProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character.AiCharacterCapabilityRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character.RoomParticipantRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.SeatPlacementRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.SeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop.TablePropKindRegistryImpl
import net.minecraft.world.item.ItemStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 放置能力必須遵守登記唯一性、凍結及安全排序。 */
class SeatPlacementRegistrationTest {
    /** 不執行世界操作的提供者。 */
    private val provider = object : SeatPlacementProvider {
        override fun supports(stack: ItemStack): Boolean = false

        override fun place(context: SeatPlacementContext): Boolean = false
    }

    /** 同優先值依完整 ID 排序，極值不以相減比較。 */
    @Test
    fun `definitions are ordered by descending priority and stable id`() {
        val registry = SeatPlacementRegistry()
        registry.register(
            definition = SeatPlacementDefinition(
                id = "example:z",
                provider = provider,
                priority = Int.MIN_VALUE,
            ),
        )
        registry.register(
            definition = SeatPlacementDefinition(
                id = "example:b",
                provider = provider,
                priority = 0,
            ),
        )
        registry.register(
            definition = SeatPlacementDefinition(
                id = "example:a",
                provider = provider,
                priority = 0,
            ),
        )
        registry.register(
            definition = SeatPlacementDefinition(
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
        val registry = SeatPlacementRegistry()
        registry.register(SeatPlacementDefinition("example:first", provider))
        val keys = registry.registrationKeys
        registry.register(SeatPlacementDefinition("example:second", provider))
        assertEquals(setOf("example:first"), keys)
        assertFailsWith<IllegalArgumentException> { registry.register(SeatPlacementDefinition("example:first", provider)) }
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(SeatPlacementDefinition("example:late", provider)) }
        assertFailsWith<IllegalArgumentException> { SeatPlacementDefinition("invalid", provider) }
    }

    /** 內建與第三方共用初始化路徑，來源診斷正確且完成後不可再登記。 */
    @Test
    fun `bootstrap records placement sources and freezes the registrar`() {
        val registries = NativeExtensionRegistries(
            tablePropKindRegistry = TablePropKindRegistryImpl(),
            seatProviderRegistry = SeatProviderRegistryImpl(),
            characterCapabilityRegistry = AiCharacterCapabilityRegistry(),
            seatPlacementRegistry = SeatPlacementRegistry(),
            roomParticipantRegistry = RoomParticipantRegistry(),
        )
        val builtIn = object : NativeMahjongExtension {
            override val id: String = "example:builtin"

            override fun registerSeatPlacements(registry: SeatPlacementRegistrar) {
                registry.register(SeatPlacementDefinition("example:builtin-seat", provider))
            }
        }
        val extension = object : NativeMahjongExtension {
            override val id: String = "example:extension"

            override fun registerSeatPlacements(registry: SeatPlacementRegistrar) {
                assertTrue("example:builtin-seat" in registry.registrationKeys)
                registry.register(SeatPlacementDefinition("example:third-party-seat", provider))
            }
        }
        val sources = NativeMahjongExtensionBootstrap.registerAndFreeze(
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
        val type = SeatPlacementContext::class.java
        assertTrue(type.constructors.none { !it.isSynthetic })
        val factory = type.declaredClasses.single { it.simpleName == "Companion" }
        val methods = factory.declaredMethods.filter { it.name.startsWith("create") }
        assertTrue(methods.isNotEmpty())
        assertTrue(methods.all { it.isSynthetic })
    }
}
