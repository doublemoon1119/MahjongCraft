package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證 Fabric 座位 provider 的登記順序、優先順序、凍結與 extension 來源診斷。 */
class FabricSeatProviderRegistrationTest {
    /** 不讀寫世界的測試 provider。 */
    private class FakeProvider : SeatProvider<ServerWorld, Entity> {
        override fun resolve(world: ServerWorld, request: SeatRequest): List<SeatTarget<ServerWorld, Entity>> = emptyList()
    }

    /**
     * 登記測試 provider 的 Fabric extension。
     *
     * @property id 測試擴充的識別碼。
     * @property registrations 要登記的 provider 識別碼、實例與優先順序。
     */
    private class ProviderExtension(
        override val id: String,
        private val registrations: List<Triple<String, SeatProvider<ServerWorld, Entity>, Int>>,
    ) : FabricMahjongExtension {
        override fun registerSeatProviders(registry: FabricSeatProviderRegistry) {
            registrations.forEach { (id, provider, priority) -> registry.register(id, provider, priority) }
        }
    }

    /** 高優先順序 provider 會排在低優先順序 provider 前面，且 extension 來源會被記錄。 */
    @Test
    fun `providers are sorted by priority and reported by extension`() {
        val registry = FabricSeatProviderRegistryImpl()
        val provider = FakeProvider()

        val sources = registry.registerAndFreeze(
            listOf(
                ProviderExtension(
                    id = "example:chairs",
                    registrations = listOf(
                        Triple("example:low", provider, 1),
                        Triple("example:high", provider, 10),
                    ),
                ),
            ),
            builtInProvider = provider,
        )

        assertEquals(
            listOf("example:high", "example:low", "mahjongcraft:mahjong_stool"),
            registry.entries().map { it.id },
        )
        assertEquals(
            listOf("mahjongcraft:mahjong_stool"),
            sources.first().categories.single().registrationIds,
        )
        assertEquals(
            listOf("example:high", "example:low"),
            sources[1].categories.single().registrationIds,
        )
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register("example:late", provider) }
    }

    /** provider 識別碼沒有命名空間時拒絕登記。 */
    @Test
    fun `provider without namespace is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            FabricSeatProviderRegistryImpl().register("chair", FakeProvider())
        }
    }

    /** 同一 extension 識別碼重複出現時拒絕 bootstrap。 */
    @Test
    fun `duplicate extension id fails registration`() {
        val provider = FakeProvider()
        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            FabricSeatProviderRegistryImpl().registerAndFreeze(
                listOf(
                    ProviderExtension("example:chairs", listOf(Triple("example:one", provider, 0))),
                    ProviderExtension("example:chairs", emptyList()),
                ),
            )
        }
    }

    /** 同一 provider 識別碼重複登記時拒絕 bootstrap。 */
    @Test
    fun `duplicate provider id fails registration`() {
        val provider = FakeProvider()
        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            FabricSeatProviderRegistryImpl().registerAndFreeze(
                listOf(
                    ProviderExtension("example:first", listOf(Triple("example:seat", provider, 0))),
                    ProviderExtension("example:second", listOf(Triple("example:seat", provider, 0))),
                ),
            )
        }
    }

    /** 沒有 extension 時也會凍結獨立 registry，避免 bootstrap 後仍可修改。 */
    @Test
    fun `empty registry is frozen after bootstrap`() {
        val registry = FabricSeatProviderRegistryImpl()

        registry.registerAndFreeze(emptyList())

        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register("example:late", FakeProvider()) }
    }
}
