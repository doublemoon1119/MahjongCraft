package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證空席查詢主體間隔、全域限制與記錄容量。 */
class RoomDiscoveryBudgetTest {
    /** 換主體也不能超出全域額度，同一主體要等滿間隔。 */
    @Test
    fun `subject interval and global tick limits are both enforced`() {
        val budget = RoomDiscoveryBudget(20, 2, 40)
        val first = Uuid.random()
        assertTrue(budget.acquire(first, 0))
        assertFalse(budget.acquire(first, 0))
        assertTrue(budget.acquire(Uuid.random(), 0))
        assertFalse(budget.acquire(Uuid.random(), 0))
        assertFalse(budget.acquire(first, 19))
        assertTrue(budget.acquire(first, 20))
    }

    /** 記錄淘汰不會放行仍在間隔內的主體，容量也不隨查詢數成長。 */
    @Test
    fun `subject records stay bounded without forgetting an active interval`() {
        val budget = RoomDiscoveryBudget(2, 2, 4)
        val fresh = Uuid.random()
        repeat(2) { assertTrue(budget.acquire(Uuid.random(), 0)) }
        assertTrue(budget.acquire(fresh, 1))
        assertTrue(budget.acquire(Uuid.random(), 1))
        assertTrue(budget.acquire(Uuid.random(), 2))
        assertFalse(budget.acquire(fresh, 2))
        assertEquals(4, budget.trackedSubjects)
        assertTrue(budget.acquire(fresh, 3))
    }

    /** 太小的記錄容量會使限流失效，因此在建立政策時拒絕。 */
    @Test
    fun `insufficient capacity is rejected and sessions reset the clock`() {
        assertFailsWith<IllegalArgumentException> { RoomDiscoveryBudget(20, 16, 100) }
        val budget = RoomDiscoveryBudget(20, 1, 20)
        val actor = Uuid.random()
        assertTrue(budget.acquire(actor, 100))
        assertFalse(budget.acquire(actor, 101))
        budget.reset()
        assertTrue(budget.acquire(actor, 0))
        assertTrue(budget.acquire(actor, -1))
    }
}
