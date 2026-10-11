package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

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
        listOf(
            MahjongStoolCuboid(1.0, 0.0, 4.0, 15.0, 9.0, 12.0),
        ),
    ),

    /** 亮紅塑膠倒角座面、外擴腳墊與四面鏤空支架。 */
    PLASTIC(
        listOf(
            MahjongStoolCuboid(1.75, 0.0, 1.75, 14.25, 9.0, 14.25),
        ),
    ),
    ;

    /** 完全貼合 [cuboids] 的碰撞與選取形狀。 */
    val shape: VoxelShape = cuboids.fold(VoxelShapes.empty()) { shape, cuboid ->
        VoxelShapes.union(shape, cuboid.toVoxelShape())
    }

    /** 凳面相對方塊底部的高度，以方塊為單位；坐下時乘客的屁股底面貼齊此高度。 */
    val seatHeight: Double = cuboids.maxOf(MahjongStoolCuboid::maxY) / MahjongStoolCuboid.MODEL_SIZE

    /** 將北向 [shape] 旋轉為指定水平朝向。 */
    fun shapeFor(facing: Direction): VoxelShape = when (facing) {
        Direction.EAST,
        Direction.WEST,
        -> cuboids.fold(VoxelShapes.empty()) { shape, cuboid ->
            VoxelShapes.union(shape, cuboid.rotateQuarterTurn().toVoxelShape())
        }
        else -> shape
    }
}
