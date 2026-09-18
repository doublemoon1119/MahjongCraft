package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.ScoringStickTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 Fabric 規則桌面物件種類的登記順序、凍結與錯誤診斷。 */
class FabricTablePropKindRegistrationTest {
    /** 不碰世界的測試種類。 */
    private class FakeKind(override val id: String) : FabricTablePropKind {
        override fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity> = emptyList()

        override fun variantOf(entity: Entity): String? = null

        override fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity? = null
    }

    /** 登記指定種類的測試 extension。 */
    private class KindExtension(
        override val id: String,
        private val kinds: List<FabricTablePropKind>,
    ) : FabricMahjongExtension {
        override fun registerTablePropKinds(registry: FabricTablePropKindRegistry) = kinds.forEach(registry::register)
    }

    /** 內建點棒種類先登記，第三方種類接在後面，完成後凍結；回傳值只列第三方種類。 */
    @Test
    fun `built-in kind registers first and third-party kinds follow before freeze`() {
        val registry = FabricTablePropKindRegistryImpl()
        val custom = FakeKind("example:marker")

        val thirdPartyIds = registry.registerAndFreeze(listOf(KindExtension("example", listOf(custom))))

        assertEquals(listOf(BuiltInTablePropKinds.SCORING_STICK, "example:marker"), registry.kinds.map { it.id })
        assertSame(ScoringStickTablePropKind, registry.find(BuiltInTablePropKinds.SCORING_STICK))
        assertEquals(listOf("example:marker"), thirdPartyIds)
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(FakeKind("example:late")) }
    }

    /** 第三方重複登記內建種類時，錯誤會指出是哪個 extension。 */
    @Test
    fun `duplicate kind failure identifies the extension`() {
        val registry = FabricTablePropKindRegistryImpl()

        val failure = assertFailsWith<FabricMahjongExtensionRegistrationException> {
            registry.registerAndFreeze(
                listOf(KindExtension("example", listOf(FakeKind(BuiltInTablePropKinds.SCORING_STICK)))),
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
            registry.registerAndFreeze(listOf(KindExtension("example", emptyList()), KindExtension("example", emptyList())))
        }
    }

    /** 種類識別碼必須帶命名空間。 */
    @Test
    fun `kind without namespace is rejected`() {
        assertFailsWith<IllegalArgumentException> { FabricTablePropKindRegistryImpl().register(FakeKind("marker")) }
    }
}
