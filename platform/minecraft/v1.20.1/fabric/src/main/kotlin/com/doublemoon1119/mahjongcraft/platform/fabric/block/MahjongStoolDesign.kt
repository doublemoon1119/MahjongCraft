package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.core.Direction
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 麻將凳固定款式及其碰撞與選取幾何。
 *
 * @property cuboids 以模型像素表示的碰撞與選取 cuboids。
 */
enum class MahjongStoolDesign(
    val cuboids: List<MahjongStoolCuboid>,
) {
    /** 狹長木座板、四隻外張斜腳與兩側橫向支撐。 */
    WOODEN(
        cuboids = listOf(
            element = MahjongStoolCuboid(1.0, 0.0, 4.0, 15.0, 9.0, 12.0),
        ),
    ),

    /** 亮紅塑膠倒角座面、外擴腳墊與四面鏤空支架。 */
    PLASTIC(
        cuboids = listOf(
            element = MahjongStoolCuboid(1.75, 0.0, 1.75, 14.25, 9.0, 14.25),
        ),
    ),
    ;

    /** 完全貼合 [cubes] 的碰撞與選取形狀。 */
    val shape: VoxelShape = cuboids.fold(Shapes.empty()) { shape, cuboid ->
        Shapes.or(shape, cuboid.toVoxelShape())
    }

    /** 凳面相對方塊底部的高度，以方塊為單位；坐下時乘客的屁股底面貼齊此高度。 */
    val seatHeight: Double = cuboids.maxOf(MahjongStoolCuboid::maxY) / MahjongStoolCuboid.MODEL_SIZE

    /** 將北向 [shape] 旋轉為指定水平朝向。 */
    fun shapeFor(facing: Direction): VoxelShape = when (facing) {
        Direction.EAST,
        Direction.WEST,
        -> cuboids.fold(Shapes.empty()) { shape, cuboid ->
            Shapes.or(shape, cuboid.rotateQuarterTurn().toVoxelShape())
        }
        else -> shape
    }
}
