package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 檢查組件型別、不可變資料與 registry 邊界。 */
class TileComponentSetTest {
    /** 名單存入及讀出都複製，不能從來源或回傳集合改掉牌上組件。 */
    @Test
    fun `access values detach caller collections on write and read`() {
        val player = Uuid.random()
        val other = Uuid.random()
        val source = mutableSetOf(player, other)
        val set = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Players(source))
        source.clear()
        val first = set[BuiltInTileComponents.FACE_ACCESS] as TileFaceAccess.Players
        assertEquals(setOf(player, other), first.playerIds)
        (first.playerIds as? MutableSet<Uuid>)?.clear()
        assertEquals(setOf(player, other), (set[BuiltInTileComponents.FACE_ACCESS] as TileFaceAccess.Players).playerIds)
    }

    /** 名稱相同的另一個定義不能改變讀取時的型別或複製行為。 */
    @Test
    fun `duplicate names cannot impersonate a registered type`() {
        val type = ComponentType("test:value", String::class, { it }, target = BuiltInComponentOwners.TILE)
        val other = ComponentType("test:value", String::class, { it }, target = BuiltInComponentOwners.TILE)
        val set = ComponentSet.EMPTY.with(type, "original")
        assertFailsWith<IllegalArgumentException> { set[other] }
        assertFailsWith<IllegalArgumentException> { set.with(other, "replacement") }
        assertEquals("original", set[type])
    }

    /** 設定、移除及插入順序不改變其他值或舊集合。 */
    @Test
    fun `patches preserve old values and reject conflicting operations`() {
        val original = ComponentSet.EMPTY.with(BuiltInTileComponents.POSE, TilePose.FACE_DOWN)
        val replacement = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
        val patch = ComponentPatch(replacement, setOf(BuiltInTileComponents.POSE), expected = original)
        val changed = patch.applyTo(original, BuiltInComponentOwners.TILE)
        assertEquals(TilePose.FACE_DOWN, original[BuiltInTileComponents.POSE])
        assertNull(changed[BuiltInTileComponents.POSE])
        assertEquals(TileFaceAccess.Public, changed[BuiltInTileComponents.FACE_ACCESS])
        assertFailsWith<IllegalArgumentException> { ComponentPatch(original, setOf(BuiltInTileComponents.POSE)) }
        val left = original.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
        val right = replacement.with(BuiltInTileComponents.POSE, TilePose.FACE_DOWN)
        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    /** 登記先驗證名稱與重複定義，凍結後不能新增。 */
    @Test
    fun `registry validates definitions and freezes registration`() {
        val registry = ComponentRegistryImpl().apply { BuiltInComponentOwners.ALL.forEach(::registerOwner) }
        registry.register(BuiltInTileComponents.FACE_ACCESS)
        val snapshot = registry.getAll()
        registry.register(BuiltInTileComponents.POSE)
        assertEquals(listOf(BuiltInTileComponents.FACE_ACCESS), snapshot)
        registry.validate(ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public))
        assertFailsWith<IllegalArgumentException> { registry.register(BuiltInTileComponents.POSE) }
        assertFailsWith<IllegalArgumentException> { registry.validate(ComponentSet.EMPTY.with(BuiltInTileComponents.APPEARANCE, "test:public")) }
        assertFailsWith<IllegalArgumentException> { ComponentType("invalid", String::class, { it }, target = BuiltInComponentOwners.TILE) }
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> { registry.register(BuiltInTileComponents.APPEARANCE) }
    }
}
