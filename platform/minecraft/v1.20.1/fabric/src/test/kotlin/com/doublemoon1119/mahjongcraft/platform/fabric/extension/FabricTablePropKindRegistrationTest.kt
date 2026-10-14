package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.ScoringStickTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 Fabric 規則桌面物件種類的登記順序、凍結與錯誤診斷。 */
class FabricTablePropKindRegistrationTest {
    /** 與桌面物件 registry 一起傳入 registrar 的空座位 provider。 */
    private class FakeProvider : SeatProvider<ServerWorld, Entity> {
        override fun resolve(world: ServerWorld, request: SeatRequest): List<SeatTarget<ServerWorld, Entity>> = emptyList()
    }

    /**
     * 使用同一個 registrar 執行兩個 Fabric registry 的 bootstrap。
     *
     * @param extensions 依序登記的 Fabric extensions。
     * @param tablePropKindRegistry 要登記桌面物件種類的 registry。
     * @param seatProviderRegistry 要登記座位 provider 的 registry。
     * @return 依來源分組的登記資訊。
     */
    private fun register(
        extensions: Iterable<FabricMahjongExtension>,
        tablePropKindRegistry: FabricTablePropKindRegistry = FabricTablePropKindRegistryImpl(),
        seatProviderRegistry: FabricSeatProviderRegistry = FabricSeatProviderRegistryImpl(),
    ) = FabricMahjongExtensionRegistrar.registerAndFreeze(
        extensions = extensions,
        registries = FabricExtensionRegistries(tablePropKindRegistry, seatProviderRegistry),
        builtIn = BuiltInFabricMahjongExtension(FakeProvider()),
    )

    /**
     * 不讀寫世界的測試種類。
     *
     * @property id 桌面物件種類識別碼。
     */
    private class FakeKind(override val id: String) : FabricTablePropKind {
        override fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity> = emptyList()

        override fun variantOf(entity: Entity): String? = null

        override fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity? = null
    }

    /**
     * 登記指定種類的測試擴充。
     *
     * @property id 擴充識別碼。
     * @property kinds 此擴充提供的桌面物件種類。
     */
    private class KindExtension(
        override val id: String,
        private val kinds: List<FabricTablePropKind>,
    ) : FabricMahjongExtension {
        override fun registerTablePropKinds(registry: FabricTablePropKindRegistry) = kinds.forEach(registry::register)
    }

    /** 內建點棒種類先登記，第三方種類接在後面，完成後凍結；回傳值依來源分組。 */
    @Test
    fun `built-in kind registers first and third-party kinds follow before freeze`() {
        val registry = FabricTablePropKindRegistryImpl()
        val custom = FakeKind("example:marker")

        val sources = register(listOf(KindExtension("example:kind", listOf(custom))), tablePropKindRegistry = registry)

        assertEquals(listOf(BuiltInTablePropKinds.SCORING_STICK, "example:marker"), registry.kinds.map { it.id })
        assertSame(ScoringStickTablePropKind, registry.find(BuiltInTablePropKinds.SCORING_STICK))
        assertEquals(
            listOf(null to listOf(BuiltInTablePropKinds.SCORING_STICK), "example:kind" to listOf("example:marker")),
            sources.map { source ->
                source.extensionId to source.categories
                    .filter { it.id == "mahjongcraft:table_prop_kind" }
                    .flatMap { it.registrationIds }
            },
        )
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(FakeKind("example:late")) }
    }

    /** 兩個類別同時登記時維持回呼順序、來源分組及全部完成後才凍結。 */
    @Test
    fun `combined registration preserves callback and report ordering`() {
        val tablePropRegistry = FabricTablePropKindRegistryImpl()
        val seatProviderRegistry = FabricSeatProviderRegistryImpl()
        val callbacks = mutableListOf<String>()
        val builtInsVisible = mutableListOf<Boolean>()

        /**
         * 建立同時登記桌面物件與座位的測試擴充。
         *
         * @param id 擴充識別碼。
         * @param kindId 桌面物件種類識別碼。
         * @param providerId 座位提供者識別碼。
         * @return 可記錄回呼順序並檢查凍結時機的擴充。
         */
        fun extension(
            id: String,
            kindId: String,
            providerId: String,
        ) = object : FabricMahjongExtension {
            override val id: String = id

            override fun registerTablePropKinds(registry: FabricTablePropKindRegistry) {
                callbacks += "$id:table"
                builtInsVisible +=
                    BuiltInTablePropKinds.SCORING_STICK in registry.registrationKeys &&
                    "mahjongcraft:mahjong_stool" in seatProviderRegistry.registrationKeys
                assertFalse(tablePropRegistry.isFrozen)
                assertFalse(seatProviderRegistry.isFrozen)
                registry.register(FakeKind(kindId))
            }

            override fun registerSeatProviders(registry: FabricSeatProviderRegistry) {
                callbacks += "$id:seat"
                builtInsVisible +=
                    BuiltInTablePropKinds.SCORING_STICK in tablePropRegistry.registrationKeys &&
                    "mahjongcraft:mahjong_stool" in registry.registrationKeys
                assertFalse(tablePropRegistry.isFrozen)
                assertFalse(seatProviderRegistry.isFrozen)
                registry.register(RegisteredSeatProvider(providerId, FakeProvider()))
            }
        }

        val sources = FabricMahjongExtensionRegistrar.registerAndFreeze(
            extensions = listOf(
                extension("example:first", "example:first-kind", "example:first-seat"),
                extension("example:second", "example:second-kind", "example:second-seat"),
            ),
            registries = FabricExtensionRegistries(tablePropRegistry, seatProviderRegistry),
            builtIn = BuiltInFabricMahjongExtension(FakeProvider()),
        )

        assertEquals(
            expected = listOf(
                "example:first:table",
                "example:first:seat",
                "example:second:table",
                "example:second:seat",
            ),
            actual = callbacks,
        )
        assertTrue(builtInsVisible.all { it })
        assertTrue(tablePropRegistry.isFrozen)
        assertTrue(seatProviderRegistry.isFrozen)
        assertEquals(
            expected = listOf(
                null to listOf(
                    "mahjongcraft:table_prop_kind" to listOf(BuiltInTablePropKinds.SCORING_STICK),
                    "mahjongcraft:seat_provider" to listOf("mahjongcraft:mahjong_stool"),
                ),
                "example:first" to listOf(
                    "mahjongcraft:table_prop_kind" to listOf("example:first-kind"),
                    "mahjongcraft:seat_provider" to listOf("example:first-seat"),
                ),
                "example:second" to listOf(
                    "mahjongcraft:table_prop_kind" to listOf("example:second-kind"),
                    "mahjongcraft:seat_provider" to listOf("example:second-seat"),
                ),
            ),
            actual = sources.map { source ->
                source.extensionId to source.categories.map { it.id to it.registrationIds }
            },
        )
    }

    /** 第三方重複登記內建種類時，錯誤會指出是哪個 extension。 */
    @Test
    fun `duplicate kind failure identifies the extension`() {
        val registry = FabricTablePropKindRegistryImpl()

        val failure = assertFailsWith<FabricMahjongExtensionRegistrationException> {
            register(
                extensions = listOf(KindExtension("example:duplicate", listOf(FakeKind(BuiltInTablePropKinds.SCORING_STICK)))),
                tablePropKindRegistry = registry,
            )
        }

        assertTrue(failure.message.orEmpty().contains("example"))
        assertTrue(failure.cause is IllegalArgumentException)
    }

    /** 同一個 extension 識別碼出現兩次時拒絕登記。 */
    @Test
    fun `duplicate extension id fails registration`() {
        val registry = FabricTablePropKindRegistryImpl()

        assertFailsWith<FabricMahjongExtensionRegistrationException> {
            register(
                extensions = listOf(KindExtension("example:duplicate", emptyList()), KindExtension("example:duplicate", emptyList())),
                tablePropKindRegistry = registry,
            )
        }
    }

    /** 種類識別碼必須帶命名空間。 */
    @Test
    fun `kind without namespace is rejected`() {
        assertFailsWith<IllegalArgumentException> { FabricTablePropKindRegistryImpl().register(FakeKind("marker")) }
    }
}
