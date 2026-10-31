package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.table.ExampleFlatTableDesign
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.table.ExampleFlatTablePart
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 3×3 單層範例桌子的形狀不進入擺牌層，且轉角依位置鏡像。 */
class ExampleFlatTableDesignTest {
    /** 每一格的頂端都在方塊頂端，上方那一層保持空著。 */
    @Test
    fun `every part stays inside its own block`() {
        ExampleFlatTableDesign.entries.forEach { design ->
            ExampleFlatTablePart.entries.forEach { part ->
                val box = design.shape(part).boundingBox
                assertEquals(1.0, box.maxY, "Expected $design $part to end at the top of its block")
                assertTrue(box.minX >= 0.0 && box.maxX <= 1.0 && box.minZ >= 0.0 && box.maxZ <= 1.0, "Expected $design $part to stay in its block")
            }
        }
    }

    /** 矮桌的桌腳在每個轉角的外側，靠近中央的那一側桌面下方是空的。 */
    @Test
    fun `low table legs sit at the outer corner`() {
        val northWest = ExampleFlatTableDesign.LOW.shape(ExampleFlatTablePart.NORTH_WEST)
        val southEast = ExampleFlatTableDesign.LOW.shape(ExampleFlatTablePart.SOUTH_EAST)

        assertTrue(northWest.overlaps(underTabletop(1, 1)), "Expected the north-west leg at the outer corner")
        assertFalse(northWest.overlaps(underTabletop(12, 12)), "Expected nothing under the inner side of the north-west corner")
        assertTrue(southEast.overlaps(underTabletop(12, 12)), "Expected the south-east leg at the outer corner")
        assertFalse(southEast.overlaps(underTabletop(1, 1)), "Expected nothing under the inner side of the south-east corner")
    }

    /** 圓桌轉角削掉外側，只保留靠近中央的部分。 */
    @Test
    fun `round table corners are cut on the outer side`() {
        val northWest = ExampleFlatTableDesign.ROUND.shape(ExampleFlatTablePart.NORTH_WEST).boundingBox
        val southEast = ExampleFlatTableDesign.ROUND.shape(ExampleFlatTablePart.SOUTH_EAST).boundingBox

        assertEquals(3.0 / 16.0, northWest.minX)
        assertEquals(1.0, northWest.maxX)
        assertEquals(0.0, southEast.minX)
        assertEquals(13.0 / 16.0, southEast.maxX)
    }

    /**
     * 建立桌面下方一小塊探測範圍。
     *
     * @param x 探測範圍最小 x，方塊內 0～16 座標。
     * @param z 探測範圍最小 z，方塊內 0～16 座標。
     * @return 3×3 大小、高度在桌面以下的探測範圍。
     */
    private fun underTabletop(x: Int, z: Int): VoxelShape = VoxelShapes.cuboid(x / 16.0, 0.0, z / 16.0, (x + 3) / 16.0, 12.0 / 16.0, (z + 3) / 16.0)

    /**
     * 判斷形狀是否與探測範圍重疊。
     *
     * @param probe 探測範圍。
     * @return 有任何重疊時為 true。
     */
    private fun VoxelShape.overlaps(probe: VoxelShape): Boolean = VoxelShapes.matchesAnywhere(this, probe, BooleanBiFunction.AND)
}
