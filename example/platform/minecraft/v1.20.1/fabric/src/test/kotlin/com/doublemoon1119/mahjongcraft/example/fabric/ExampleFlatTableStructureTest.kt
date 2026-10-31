package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.table.ExampleFlatTablePart
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.table.ExampleFlatTableStructure
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 3×3 單層範例桌子的格子位置換算。 */
class ExampleFlatTableStructureTest {
    /** 9 格正好是中央所在層、以中央為中心的 3×3。 */
    @Test
    fun `placements cover the three by three layer around the center`() {
        val placements = ExampleFlatTableStructure.placements(CENTER)

        val expected = (9..11).flatMap { x -> (-6..-4).map { z -> BlockPos(x, 64, z) } }.toSet()
        assertEquals(expected, placements.values.toSet())
        assertEquals(CENTER, placements.getValue(ExampleFlatTablePart.CENTER))
        assertEquals(BlockPos(10, 64, -6), placements.getValue(ExampleFlatTablePart.NORTH))
        assertEquals(BlockPos(11, 64, -4), placements.getValue(ExampleFlatTablePart.SOUTH_EAST))
    }

    /** 任何一格都能反推回中央。 */
    @Test
    fun `every part maps back to the center`() {
        ExampleFlatTableStructure.placements(CENTER).forEach { (part, pos) ->
            assertEquals(CENTER, ExampleFlatTableStructure.centerPos(pos, part), "Expected $part to map back to the center")
        }
    }

    /** 測試用的中央位置。 */
    private companion object {
        /** 中央控制方塊的位置。 */
        val CENTER = BlockPos(10, 64, -5)
    }
}
