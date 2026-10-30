package com.doublemoon1119.mahjongcraft.logic.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** 驗證組件變更以預期舊值防止覆寫已改變的資料，且只能套用到正確對象。 */
class ComponentPatchExpectationTest {
    /** 測試用的對局數值。 */
    private val points = ComponentType("test:points", Int::class, { it }, target = BuiltInComponentOwners.TABLE)

    /** 不被這次變更觸及的對局數值。 */
    private val untouched = ComponentType("test:untouched", Int::class, { it }, target = BuiltInComponentOwners.TABLE)

    /** 舊值相符時套用；舊值已被改動時拒絕，不覆寫新值。 */
    @Test
    fun `stale expected value is rejected`() {
        val original = ComponentSet.EMPTY.with(points, 1000)
        val patch = ComponentPatch(
            values = ComponentSet.EMPTY.with(points, 2000),
            expected = ComponentSet.EMPTY.with(points, 1000),
        )

        assertEquals(2000, patch.applyTo(original, BuiltInComponentOwners.TABLE)[points])
        assertFailsWith<IllegalArgumentException> { patch.applyTo(original.with(points, 1500), BuiltInComponentOwners.TABLE) }
    }

    /** 設定值、移除與預期舊值都相同的變更相等；任一項不同就不相等。 */
    @Test
    fun `patches with the same changes are equal`() {
        val patch = ComponentPatch.basedOn(ComponentSet.EMPTY.with(points, 1000), values = ComponentSet.EMPTY.with(points, 2000), removals = setOf(untouched))
        val same = ComponentPatch.basedOn(ComponentSet.EMPTY.with(points, 1000), values = ComponentSet.EMPTY.with(points, 2000), removals = setOf(untouched))

        assertEquals(patch, same)
        assertEquals(patch.hashCode(), same.hashCode())
        assertNotEquals(patch, ComponentPatch.basedOn(ComponentSet.EMPTY.with(points, 1500), values = ComponentSet.EMPTY.with(points, 2000), removals = setOf(untouched)))
        assertNotEquals(patch, ComponentPatch.basedOn(ComponentSet.EMPTY.with(points, 1000), values = ComponentSet.EMPTY.with(points, 2000)))
        assertEquals(ComponentPatch(), ComponentPatch())
    }

    /** 沒有列出預期舊值的組件必須原本不存在。 */
    @Test
    fun `missing expected value requires the component to be absent`() {
        val patch = ComponentPatch(values = ComponentSet.EMPTY.with(points, 2000))

        assertEquals(2000, patch.applyTo(ComponentSet.EMPTY, BuiltInComponentOwners.TABLE)[points])
        assertFailsWith<IllegalArgumentException> { patch.applyTo(ComponentSet.EMPTY.with(points, 1000), BuiltInComponentOwners.TABLE) }
    }

    /** 從讀到的集合建立的變更只記下被改動組件的舊值；移除同樣比對舊值。 */
    @Test
    fun `based on captures only changed components`() {
        val current = ComponentSet.EMPTY.with(points, 1000).with(untouched, 7)
        val patch = ComponentPatch.basedOn(current, removals = setOf(points))

        assertEquals(ComponentSet.EMPTY.with(points, 1000), patch.expected)
        val changed = patch.applyTo(current.with(untouched, 8), BuiltInComponentOwners.TABLE)
        assertNull(changed[points])
        assertEquals(8, changed[untouched])
        assertFailsWith<IllegalArgumentException> { patch.applyTo(current.with(points, 999), BuiltInComponentOwners.TABLE) }
    }

    /** 預期舊值只能列出這次改動的組件。 */
    @Test
    fun `expected values outside the change are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ComponentPatch(
                values = ComponentSet.EMPTY.with(points, 1),
                expected = ComponentSet.EMPTY.with(untouched, 7),
            )
        }
    }

    /** 對局組件的變更不能套用到其他對象。 */
    @Test
    fun `patch for another owner is rejected`() {
        val patch = ComponentPatch(values = ComponentSet.EMPTY.with(points, 1))

        assertFailsWith<IllegalArgumentException> { patch.applyTo(ComponentSet.EMPTY, BuiltInComponentOwners.PLAYER) }
    }
}
