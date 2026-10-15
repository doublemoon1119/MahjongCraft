package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 Fabric 座位 provider 的登記順序、優先順序、凍結與 extension 來源診斷。 */
class FabricSeatProviderRegistrationTest {
    /** 不讀寫世界的測試 provider。 */
    private class FakeProvider : SeatProvider<ServerWorld, Entity> {
        override fun resolve(world: ServerWorld, request: SeatRequest): List<SeatTarget<ServerWorld, Entity>> = emptyList()
    }

    /**
     * 與座位 provider registry 一起傳入 registrar 的空桌面物件種類 registry。
     *
     * @param extensions 依序登記的 Fabric extensions。
     * @param seatProviderRegistry 要登記座位 provider 的 registry。
     * @return 依來源分組的登記資訊。
     */
    private fun register(
        extensions: Iterable<FabricMahjongExtension>,
        seatProviderRegistry: FabricSeatProviderRegistryImpl = FabricSeatProviderRegistryImpl(),
    ) = FabricMahjongExtensionRegistrar.registerAndFreeze(
        extensions = extensions,
        registries = FabricExtensionRegistries(FabricTablePropKindRegistryImpl(), seatProviderRegistry),
        builtIn = BuiltInFabricMahjongExtension(FakeProvider()),
        failurePolicy = ExtensionFailurePolicy.Default,
    )

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
            registrations.forEach { (id, provider, priority) -> registry.register(RegisteredSeatProvider(id, provider, priority)) }
        }
    }

    /** 高優先順序 provider 會排在低優先順序 provider 前面，且 extension 來源會被記錄。 */
    @Test
    fun `providers are sorted by priority and reported by extension`() {
        val registry = FabricSeatProviderRegistryImpl()
        val provider = FakeProvider()

        val sources = register(
            extensions = listOf(
                ProviderExtension(
                    id = "example:chairs",
                    registrations = listOf(
                        Triple("example:low", provider, 1),
                        Triple("example:high", provider, 10),
                    ),
                ),
            ),
            seatProviderRegistry = registry,
        )

        assertEquals(
            expected = listOf("example:high", "example:low", "mahjongcraft:mahjong_stool"),
            actual = registry.entries().map { it.id },
        )
        assertEquals(
            expected = listOf("mahjongcraft:mahjong_stool"),
            actual = sources.first().categories.first { it.id == "mahjongcraft:seat_provider" }.registrationIds,
        )
        assertEquals(
            expected = listOf("example:high", "example:low"),
            actual = sources[1].categories.single().registrationIds,
        )
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(RegisteredSeatProvider("example:late", provider)) }
    }

    /** 擴充取得的 registry 只能登記與查看狀態，不能凍結或讀取其他擴充登記的 provider。 */
    @Test
    fun `public registry exposes registration only`() {
        val methods = FabricSeatProviderRegistry::class.java.methods.map { it.name }

        assertTrue("register" in methods)
        assertTrue(methods.none { it == "freeze" || it == "entries" }, "Unexpected methods: $methods")
    }

    /** provider 識別碼沒有命名空間時拒絕登記。 */
    @Test
    fun `provider without namespace is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            FabricSeatProviderRegistryImpl().register(RegisteredSeatProvider("chair", FakeProvider()))
        }
    }

    /** 同一 extension 識別碼重複出現時拒絕 bootstrap。 */
    @Test
    fun `duplicate extension id fails registration`() {
        val provider = FakeProvider()
        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            register(
                extensions = listOf(
                    ProviderExtension("example:chairs", listOf(Triple("example:one", provider, 0))),
                    ProviderExtension("example:chairs", emptyList()),
                ),
            )
        }
    }

    /** extension 識別碼沒有命名空間時拒絕 bootstrap。 */
    @Test
    fun `extension without namespace fails registration`() {
        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            register(listOf(ProviderExtension("invalid", emptyList())))
        }
    }

    /** 第三方 extension 不得佔用內建 extension 的保留識別碼。 */
    @Test
    fun `built-in extension id is reserved`() {
        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            register(listOf(ProviderExtension("mahjongcraft:builtin", emptyList())))
        }
    }

    /** 內建 extension 識別碼無效時拒絕 bootstrap。 */
    @Test
    fun `invalid built-in id fails registration`() {
        val invalidBuiltIn = object : FabricMahjongExtension {
            override val id: String = "invalid"
        }
        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            FabricMahjongExtensionRegistrar.registerAndFreeze(
                extensions = emptyList(),
                registries = FabricExtensionRegistries(
                    tablePropKindRegistry = FabricTablePropKindRegistryImpl(),
                    seatProviderRegistry = FabricSeatProviderRegistryImpl(),
                ),
                builtIn = invalidBuiltIn,
                failurePolicy = ExtensionFailurePolicy.Default,
            )
        }
    }

    /** 同一 provider 識別碼重複登記時拒絕 bootstrap。 */
    @Test
    fun `duplicate provider id fails registration`() {
        val provider = FakeProvider()
        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            register(
                extensions = listOf(
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

        register(emptyList(), registry)

        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(RegisteredSeatProvider("example:late", FakeProvider())) }
    }

    /** 座位回呼失敗時兩個 registry 都不應凍結，且桌面物件回呼先於座位回呼。 */
    @Test
    fun `seat callback failure leaves both registries unfrozen and reports callback order`() {
        val tablePropRegistry = FabricTablePropKindRegistryImpl()
        val seatRegistry = FabricSeatProviderRegistryImpl()
        val callbacks = mutableListOf<String>()
        val extension = object : FabricMahjongExtension {
            override val id: String = "example:failing"

            override fun registerTablePropKinds(registry: FabricTablePropKindRegistry) {
                callbacks += "table"
            }

            override fun registerSeatProviders(registry: FabricSeatProviderRegistry) {
                callbacks += "seat"
                error("seat failure")
            }
        }

        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            FabricMahjongExtensionRegistrar.registerAndFreeze(
                extensions = listOf(extension),
                registries = FabricExtensionRegistries(tablePropRegistry, seatRegistry),
                builtIn = BuiltInFabricMahjongExtension(FakeProvider()),
                failurePolicy = ExtensionFailurePolicy.Default,
            )
        }
        assertEquals(listOf("table", "seat"), callbacks)
        assertFalse(tablePropRegistry.isFrozen)
        assertFalse(seatRegistry.isFrozen)
    }
}
