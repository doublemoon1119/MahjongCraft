package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [MahjongSeatingTableLayout] 的座位座標與朝向測試。 */
class MahjongSeatingTableLayoutTest {
    /** 四個座位分別位於桌子四個側邊中點（controller X／Z 座標其中一軸 ±2），並置中在方塊正中央。 */
    @Test
    fun `seats are placed at the four side midpoints two blocks from controller`() {
        val placements = MahjongSeatingTableLayout.seatPlacements(controllerX = 0, controllerY = 64, controllerZ = 0)

        val expectedCenters = setOf(2.5 to 0.5, 0.5 to 2.5, -1.5 to 0.5, 0.5 to -1.5)
        assertEquals(expectedCenters, placements.map { it.x to it.z }.toSet())
        assertTrue(placements.all { it.y == 64.0 }, "All seats should stay at controller height.")
    }

    /** 每個座位的朝向都應精確面向桌子中心（controller 置中座標）。 */
    @Test
    fun `every seat faces toward the table center`() {
        val controllerX = 10
        val controllerY = 70
        val controllerZ = -5
        val centerX = controllerX + 0.5
        val centerZ = controllerZ + 0.5

        val placements = MahjongSeatingTableLayout.seatPlacements(controllerX, controllerY, controllerZ)

        placements.forEach { placement ->
            val yawRadians = Math.toRadians(placement.yaw.toDouble())
            val lookX = -sin(yawRadians)
            val lookZ = cos(yawRadians)

            val toCenterX = centerX - placement.x
            val toCenterZ = centerZ - placement.z
            val magnitude = sqrt(toCenterX * toCenterX + toCenterZ * toCenterZ)
            val normalizedDot = (lookX * toCenterX + lookZ * toCenterZ) / magnitude

            assertTrue(normalizedDot > 0.999, "Seat at (${placement.x}, ${placement.z}) should face the table center.")
        }
    }

    /** 同樣的輸入必須產生同樣的座位順序，讓呼叫端能依固定 index 對應玩家座位。 */
    @Test
    fun `seat placements are stable across calls for the same controller position`() {
        val first = MahjongSeatingTableLayout.seatPlacements(3, 70, 3)
        val second = MahjongSeatingTableLayout.seatPlacements(3, 70, 3)

        assertEquals(first, second)
    }
}
