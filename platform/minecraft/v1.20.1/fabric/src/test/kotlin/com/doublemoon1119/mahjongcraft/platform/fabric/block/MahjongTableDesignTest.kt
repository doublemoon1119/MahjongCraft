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

    /** 沒有靜態模型的上層中央不應提供選取框，避免桌面上方出現空的整格線框。 */
    @Test
    fun `top center has no outline because it renders nothing`() {
        MahjongTableDesign.entries.forEach { design ->
            assertTrue(design.outlineShape(MahjongTablePart.TOP_CENTER, Direction.NORTH).isEmpty)
        }
    }

    /** 其餘 parts 的選取框應貼合實際渲染的幾何，也就是與碰撞一致。 */
    @Test
    fun `outline matches rendered geometry for parts with models`() {
        MahjongTableDesign.entries.forEach { design ->
            MahjongTablePart.entries.filter { part -> part != MahjongTablePart.TOP_CENTER }.forEach { part ->
                Direction.Type.HORIZONTAL.forEach { facing ->
                    assertEquals(
                        design.collisionShape(part, facing).boundingBoxes,
                        design.outlineShape(part, facing).boundingBoxes,
                    )
                }
            }
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
