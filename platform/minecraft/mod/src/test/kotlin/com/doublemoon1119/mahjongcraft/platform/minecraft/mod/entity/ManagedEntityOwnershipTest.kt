package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ManagedEntityOwnership] 判斷擁有者是否還在的測試。 */
class ManagedEntityOwnershipTest {
    /** 擁有者還在時保留 entity。 */
    @Test
    fun `test entity stays while its owner exists`() {
        val ownerId = Uuid.random()

        assertFalse(ManagedEntityOwnership.isGone(ownerId, isDataAvailable = true, ownerIds = setOf(ownerId)))
    }

    /** 擁有者不在時移除 entity。 */
    @Test
    fun `test entity goes once its owner is gone`() {
        assertTrue(ManagedEntityOwnership.isGone(Uuid.random(), isDataAvailable = true, ownerIds = setOf(Uuid.random())))
    }

    /** 未指派擁有者的 entity 視為擁有者不在。 */
    @Test
    fun `test unassigned entity goes`() {
        assertTrue(ManagedEntityOwnership.isGone(null, isDataAvailable = true, ownerIds = emptySet()))
    }

    /** 麻將資料無法讀取時權威狀態是空的，不能據此移除。 */
    @Test
    fun `test entity stays while mahjong data is unavailable`() {
        assertFalse(ManagedEntityOwnership.isGone(Uuid.random(), isDataAvailable = false, ownerIds = emptySet()))
        assertFalse(ManagedEntityOwnership.isGone(null, isDataAvailable = false, ownerIds = emptySet()))
    }
}
