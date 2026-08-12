package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [MahjongTableDesign] 的桌面高度、桌緣、桌腳及中央柱碰撞測試。 */
class MahjongTableDesignTest {
    /** 兩種桌型的底層碰撞最高點都應固定在一個 block。 */
    @Test
    fun `all bottom collision profiles keep fixed table surface height`() {
        MahjongTableDesign.entries.forEach { design ->
            MahjongTablePart.entries.filter { part -> part.localY == 0 }.forEach { part ->
                Direction.Type.HORIZONTAL.forEach { facing ->
                    assertEquals(1.0, design.collisionShape(part, facing).boundingBox.maxY)
                }
            }
        }
    }

    /** 上層中心沒有碰撞，外圍桌緣只高出桌面十六分之一格。 */
    @Test
    fun `top collision contains empty center and one pixel rim`() {
        MahjongTableDesign.entries.forEach { design ->
            assertTrue(design.collisionShape(MahjongTablePart.TOP_CENTER, Direction.NORTH).isEmpty)
            val edge = design.collisionShape(MahjongTablePart.TOP_NORTH, Direction.NORTH).boundingBox

            assertEquals(1.0 / 16.0, edge.maxY)
            assertEquals(1.0 / 16.0, edge.maxZ)
        }
    }

    /** 無碰撞的上層中央仍應具有完整方塊選取框。 */
    @Test
    fun `top center remains selectable without collision`() {
        MahjongTableDesign.entries.forEach { design ->
            val outline = design.outlineShape(MahjongTablePart.TOP_CENTER, Direction.NORTH)

            assertEquals(1.0, outline.boundingBox.maxX)
            assertEquals(1.0, outline.boundingBox.maxY)
            assertEquals(1.0, outline.boundingBox.maxZ)
        }
    }

    /** 四腳款只在底層角落具有延伸至地面的桌腳。 */
    @Test
    fun `four leg profile reaches floor only at corners`() {
        val corner = MahjongTableDesign.FOUR_LEG
            .collisionShape(MahjongTablePart.BOTTOM_NORTH_WEST, Direction.NORTH)
            .boundingBoxes
        val edge = MahjongTableDesign.FOUR_LEG
            .collisionShape(MahjongTablePart.BOTTOM_NORTH, Direction.NORTH)
            .boundingBoxes

        assertTrue(corner.any { box -> box.minY == 0.0 })
        assertTrue(edge.all { box -> box.minY == 14.0 / 16.0 })
    }

    /** 中央柱款只在 controller 具有延伸至地面的底座及柱體。 */
    @Test
    fun `pedestal profile reaches floor only at controller`() {
        val center = MahjongTableDesign.PEDESTAL
            .collisionShape(MahjongTablePart.BOTTOM_CENTER, Direction.NORTH)
            .boundingBoxes
        val edge = MahjongTableDesign.PEDESTAL
            .collisionShape(MahjongTablePart.BOTTOM_NORTH, Direction.NORTH)
            .boundingBoxes

        assertTrue(center.any { box -> box.minY == 0.0 })
        assertTrue(edge.all { box -> box.minY == 12.0 / 16.0 })
    }

    /** 桌緣應依 facing 從局部北側旋轉至世界東側。 */
    @Test
    fun `rim collision rotates with table facing`() {
        val eastFacingEdge = MahjongTableDesign.FOUR_LEG
            .collisionShape(MahjongTablePart.TOP_NORTH, Direction.EAST)
            .boundingBox

        assertEquals(15.0 / 16.0, eastFacingEdge.minX)
        assertEquals(1.0, eastFacingEdge.maxX)
    }
}
