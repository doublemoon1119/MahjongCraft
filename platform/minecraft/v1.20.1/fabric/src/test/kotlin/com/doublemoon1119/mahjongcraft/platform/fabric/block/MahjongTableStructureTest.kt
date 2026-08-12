package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

/** [MahjongTableStructure] 的 part 唯一性、旋轉與反查測試。 */
class MahjongTableStructureTest {
    /** 每個朝向都應產生 18 個不重複且位於 3×3×2 bounds 內的位置。 */
    @Test
    fun `placements cover unique three by three by two structure for every facing`() {
        Direction.Type.HORIZONTAL.forEach { facing ->
            val controller = BlockPos(12, 64, -7)
            val positions = MahjongTableStructure.placements(controller, facing).values

            assertEquals(MahjongTableStructure.PART_COUNT, positions.size)
            assertEquals(MahjongTableStructure.PART_COUNT, positions.toSet().size)
            positions.forEach { pos ->
                assertEquals(true, pos.x in controller.x - 1..controller.x + 1)
                assertEquals(true, pos.y in controller.y..controller.y + 1)
                assertEquals(true, pos.z in controller.z - 1..controller.z + 1)
            }
        }
    }

    /** 每個 part 在四種朝向下都應能由世界座標還原同一 controller。 */
    @Test
    fun `controller position round trips every part and facing`() {
        val controller = BlockPos(-20, 5, 31)

        Direction.Type.HORIZONTAL.forEach { facing ->
            MahjongTablePart.entries.forEach { part ->
                val partPos = MahjongTableStructure.position(controller, part, facing)

                assertEquals(controller, MahjongTableStructure.controllerPosition(partPos, part, facing))
            }
        }
    }

    /** 向東旋轉時，朝北的局部北側應落在 controller 東側。 */
    @Test
    fun `east facing rotates north edge to east side`() {
        val controller = BlockPos.ORIGIN

        assertEquals(
            BlockPos(1, 0, 0),
            MahjongTableStructure.position(controller, MahjongTablePart.BOTTOM_NORTH, Direction.EAST),
        )
    }
}
