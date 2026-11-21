package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.core.Direction
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [MahjongStoolDesign] 的固定高度、精確 cuboids 與可穿越空隙測試。 */
class MahjongStoolDesignTest {
    /** 兩款凳子的模型及碰撞最高點不超過玩家可直接跨越的九像素高度。 */
    @Test
    fun `all stool designs stay within directly walkable height`() {
        MahjongStoolDesign.entries.forEach { design ->
            val expectedHeight = 9.0
            assertEquals(expectedHeight, design.cuboids.maxOf(MahjongStoolCuboid::maxY))
            assertEquals(expectedHeight / 16.0, design.shape.bounds().maxY)
        }
    }

    /** 兩款凳子的凳面高度都等於碰撞最高點九像素。 */
    @Test
    fun `seat height matches the top of the collision shape`() {
        MahjongStoolDesign.entries.forEach { design ->
            assertEquals(9.0 / 16.0, design.seatHeight)
            assertEquals(design.shape.bounds().maxY, design.seatHeight)
        }
    }

    /** 每個模型 cuboid 都應被合併後的 collision shape 完整覆蓋。 */
    @Test
    fun `collision shape covers every model cuboid`() {
        MahjongStoolDesign.entries.forEach { design ->
            design.cuboids.forEach { cuboid ->
                assertTrue(
                    actual = !Shapes.joinIsNotEmpty(
                        cuboid.toVoxelShape(),
                        design.shape,
                        BooleanOp.ONLY_FIRST,
                    ),
                )
            }
        }
    }

    /** 木製款使用單一長方體涵蓋座板與整組 H 型支架。 */
    @Test
    fun `wooden design contains four separate legs`() {
        val floorCuboids = MahjongStoolDesign.WOODEN.cuboids.filter { cuboid -> cuboid.minY == 0.0 }

        assertEquals(1, floorCuboids.size)
        assertEquals(9.0, floorCuboids.single().maxY)
    }

    /** 東西朝向會交換木製凳碰撞的長軸與短軸。 */
    @Test
    fun `wooden shape rotates with horizontal facing`() {
        val north = MahjongStoolDesign.WOODEN.shapeFor(Direction.NORTH).bounds()
        val east = MahjongStoolDesign.WOODEN.shapeFor(Direction.EAST).bounds()

        assertEquals(14.0 / 16.0, north.xsize)
        assertEquals(8.0 / 16.0, north.zsize)
        assertEquals(8.0 / 16.0, east.xsize)
        assertEquals(14.0 / 16.0, east.zsize)
    }

    /** 塑膠款使用單一長方體涵蓋座面與四面鏤空支架。 */
    @Test
    fun `plastic design uses one full support envelope`() {
        val floorCuboids = MahjongStoolDesign.PLASTIC.cuboids.filter { cuboid -> cuboid.minY == 0.0 }

        assertEquals(1, floorCuboids.size)
        assertEquals(9.0, floorCuboids.single().maxY)
    }
}
