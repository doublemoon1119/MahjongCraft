package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import net.minecraft.util.math.Box
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 交換位置只略過本批將離開的成員，不能讓外人或重疊目的位置通過。 */
class SeatCollisionScopeTest {
    /** 相同操作內略過合法換位者，外人、自己與重疊目的範圍仍不略過。 */
    @Test
    fun `batch exclusions require two distinct nonoverlapping destinations`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val outsider = Uuid.random()
        val left = Box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0)
        val right = Box(3.0, 0.0, 0.0, 4.0, 2.0, 1.0)
        SeatCollisionScope.withPlans(mapOf(first to left, second to right)) {
            assertTrue(SeatCollisionScope.ignores(first, second))
            assertFalse(SeatCollisionScope.ignores(first, outsider))
            assertFalse(SeatCollisionScope.ignores(outsider, first))
            assertFalse(SeatCollisionScope.ignores(first, first))
        }
        SeatCollisionScope.withPlans(mapOf(first to left, second to left)) {
            assertFalse(SeatCollisionScope.ignores(first, second))
        }
        assertFalse(SeatCollisionScope.ignores(first, second))
    }

    /** 巢狀操作失敗後恢復原範圍，外層結束後不留下碰撞例外。 */
    @Test
    fun `nested failure restores outer scope and leaves no stale exclusions`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val plans = mapOf(first to Box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0), second to Box(3.0, 0.0, 0.0, 4.0, 2.0, 1.0))
        SeatCollisionScope.withPlans(plans) {
            assertFailsWith<IllegalStateException> {
                SeatCollisionScope.withPlans(emptyMap()) {
                    assertFalse(SeatCollisionScope.ignores(first, second))
                    error("provider failed")
                }
            }
            assertTrue(SeatCollisionScope.ignores(first, second))
        }
        assertFalse(SeatCollisionScope.ignores(first, second))
    }
}
