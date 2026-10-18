package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [MahjongSeatingTableLayout] 的座位座標與朝向測試。 */
class MahjongSeatingTableLayoutTest {
    /** 四個座位分別位於桌子四個側邊中點（controller X／Z 座標其中一軸 ±2），並置中在方塊正中央。 */
    @Test
    fun `seats are placed at the four side midpoints two blocks from controller`() {
        val placements = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.FOUR_PLAYER,
            controllerX = 0,
            controllerY = 64,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
        )

        val expectedCenters = setOf(2.5 to 0.5, 0.5 to 2.5, -1.5 to 0.5, 0.5 to -1.5)
        assertEquals(expectedCenters, placements.map { it.x to it.z }.toSet())
        assertTrue(placements.all { it.y == 64.0 }, "All seats should stay at controller height.")
    }

    /** 每個座位的朝向都應精確面向桌子中心（controller 置中座標），對每一種桌子世界朝向都成立。 */
    @Test
    fun `every seat faces toward the table center`() {
        val controllerX = 10
        val controllerY = 70
        val controllerZ = -5
        val centerX = controllerX + 0.5
        val centerZ = controllerZ + 0.5

        MahjongTableFacing.entries.forEach { tableFacing ->
            val placements =
                MahjongSeatingTableLayout.seatPlacements(
                    layout = MahjongTableSeatLayout.FOUR_PLAYER,
                    controllerX = controllerX,
                    controllerY = controllerY,
                    controllerZ = controllerZ,
                    tableFacing = tableFacing,
                )

            placements.forEach { placement ->
                val yawRadians = Math.toRadians(placement.yaw.toDouble())
                val lookX = -sin(yawRadians)
                val lookZ = cos(yawRadians)

                val toCenterX = centerX - placement.x
                val toCenterZ = centerZ - placement.z
                val magnitude = sqrt(toCenterX * toCenterX + toCenterZ * toCenterZ)
                val normalizedDot = (lookX * toCenterX + lookZ * toCenterZ) / magnitude

                assertTrue(
                    normalizedDot > 0.999,
                    "Seat at (${placement.x}, ${placement.z}) should face the table center for facing $tableFacing.",
                )
            }
        }
    }

    /** 同樣的輸入必須產生同樣的座位順序，讓呼叫端能依固定 index 對應玩家座位。 */
    @Test
    fun `seat placements are stable across calls for the same controller position`() {
        val first = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.FOUR_PLAYER,
            controllerX = 3,
            controllerY = 70,
            controllerZ = 3,
            tableFacing = MahjongTableFacing.NORTH,
        )
        val second = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.FOUR_PLAYER,
            controllerX = 3,
            controllerY = 70,
            controllerZ = 3,
            tableFacing = MahjongTableFacing.NORTH,
        )

        assertEquals(first, second)
    }

    /** 座位 index 0 的座標必須隨桌子世界朝向旋轉，跟 [MahjongTileTableLayout.handPlacement] 用同一套 [MahjongTableFacing] 慣例，兩者才不會像先前那樣對不上。 */
    @Test
    fun `seat position rotates with table facing`() {
        val positionsByFacing = MahjongTableFacing.entries.associateWith { tableFacing ->
            MahjongSeatingTableLayout.seatPlacements(
                layout = MahjongTableSeatLayout.FOUR_PLAYER,
                controllerX = 0,
                controllerY = 64,
                controllerZ = 0,
                tableFacing = tableFacing,
            ).first().let { it.x to it.z }
        }

        assertEquals(4, positionsByFacing.values.toSet().size)
        assertNotEquals(
            positionsByFacing.getValue(MahjongTableFacing.NORTH),
            positionsByFacing.getValue(MahjongTableFacing.EAST),
        )
    }

    /** 固定席位請求依座位 index 排列，帶同一張桌子與維度，以及對應 index 的標準座位。 */
    @Test
    fun `seat requests carry the standard seat of each index`() {
        val tableId = Uuid.parse("00000000-0000-0000-0000-000000000007")
        val seats = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.FOUR_PLAYER,
            controllerX = 5,
            controllerY = 70,
            controllerZ = -3,
            tableFacing = MahjongTableFacing.EAST,
        )

        val requests = MahjongSeatingTableLayout.seatRequests(
            layout = MahjongTableSeatLayout.FOUR_PLAYER,
            tableId = tableId,
            dimensionId = "minecraft:the_nether",
            controllerX = 5,
            controllerY = 70,
            controllerZ = -3,
            tableFacing = MahjongTableFacing.EAST,
        )

        assertEquals(seats.indices.toList(), requests.map { it.seatIndex })
        assertEquals(seats, requests.map { it.seat })
        assertTrue(requests.all { it.tableId == tableId && it.dimensionId == "minecraft:the_nether" })
    }

    /** 驗證兩位玩家只使用相對的南北兩側，且所有桌子朝向都維持相同拓撲。 */
    @Test
    fun `two player layout uses opposite sides for every table facing`() {
        MahjongTableFacing.entries.forEach { tableFacing ->
            val placements = MahjongSeatingTableLayout.seatPlacements(
                layout = MahjongTableSeatLayout.TWO_PLAYER,
                controllerX = 0,
                controllerY = 64,
                controllerZ = 0,
                tableFacing = tableFacing,
            )

            assertEquals(2, placements.size)
            assertEquals(4.0, distanceBetween(placements[0], placements[1]), absoluteTolerance = 0.0001)
        }
    }

    /** 驗證三位玩家維持南、東、北三側，且所有桌子朝向都產生三個座位。 */
    @Test
    fun `three player layout leaves one side empty for every table facing`() {
        MahjongTableFacing.entries.forEach { tableFacing ->
            val placements = MahjongSeatingTableLayout.seatPlacements(
                layout = MahjongTableSeatLayout.THREE_PLAYER,
                controllerX = 0,
                controllerY = 64,
                controllerZ = 0,
                tableFacing = tableFacing,
            )

            assertEquals(3, placements.size)
            val distances = listOf(
                distanceBetween(placements[0], placements[1]),
                distanceBetween(placements[1], placements[2]),
                distanceBetween(placements[0], placements[2]),
            )
            assertEquals(sqrt(8.0), requireNotNull(distances.minOrNull()), absoluteTolerance = 0.0001)
            assertEquals(4.0, requireNotNull(distances.maxOrNull()), absoluteTolerance = 0.0001)
        }
    }

    /** 計算兩個標準座位腳底位置的水平距離。 */
    private fun distanceBetween(first: SeatGeometry, second: SeatGeometry): Double {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return sqrt(dx * dx + dz * dz)
    }
}
