package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證額外對象不需修改共用集合，且不能冒充另一種對象。 */
class ComponentOwnerTypeTest {
    /**
     * 測試用額外對象，不屬於正式遊戲模型。
     *
     * @property components 此對象的組件。
     */
    private data class ExtraOwner(val components: ComponentSet)

    /** 額外對象可登記、保存獨立值，原牌張不能使用它的組件。 */
    @Test
    fun `custom owner uses the same collection without accepting tile components`() {
        val owner = ComponentOwnerType("test:extra_owner", ExtraOwner::class)
        val type = ComponentType(
            id = "test:extra_value",
            valueClass = String::class,
            copyValue = { it },
            target = owner,
        )
        val registry = ComponentRegistryImpl()
        assertNull(registry.findOwner(owner.id))
        assertFailsWith<IllegalArgumentException> { registry.register(type) }
        registry.registerOwner(owner)
        registry.register(type)
        val value = ComponentSet.EMPTY.with(type, "value")
        owner.requireInstance(ExtraOwner(value))
        value.requireTarget(owner)
        registry.validate(value)
        assertEquals("value", value[type])
        assertFailsWith<IllegalArgumentException> { IdentifiedTile(Uuid.random(), Tile.Honor.East, value) }
        assertFailsWith<IllegalArgumentException> { owner.requireInstance(Tile.Honor.East) }
    }

    /** 名稱相同也不能換掉對象定義，凍結涵蓋對象登記。 */
    @Test
    fun `owner definitions reject duplicate identities and freeze`() {
        val registry = ComponentRegistryImpl()
        val owner = ComponentOwnerType("test:owner", ExtraOwner::class)
        registry.registerOwner(owner)
        val captured = registry.getAllOwners()
        assertFailsWith<IllegalArgumentException> { registry.registerOwner(ComponentOwnerType(owner.id, String::class)) }
        val impostor = ComponentOwnerType(owner.id, ExtraOwner::class)
        val type = ComponentType("test:value", String::class, { it }, target = impostor)
        assertFailsWith<IllegalArgumentException> { registry.register(type) }
        registry.registerOwner(BuiltInComponentOwners.TILE)
        assertEquals(listOf(owner), captured)
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.registerOwner(BuiltInComponentOwners.PLAYER) }
    }
}
