package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/** 麻將凳固定款式及其碰撞與選取幾何。 */
enum class MahjongStoolDesign(
    /** 以模型像素表示的碰撞與選取 cuboids。 */
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

/** 以模型像素表示的麻將凳長方體，供模型資源與 Fabric 碰撞測試對齊。 */
data class MahjongStoolCuboid(
    /** X 軸最小座標。 */
    val minX: Double,
    /** Y 軸最小座標。 */
    val minY: Double,
    /** Z 軸最小座標。 */
    val minZ: Double,
    /** X 軸最大座標。 */
    val maxX: Double,
    /** Y 軸最大座標。 */
    val maxY: Double,
    /** Z 軸最大座標。 */
    val maxZ: Double,
) {
    /** 將模型像素座標轉成 Minecraft block 單位的 [VoxelShape]。 */
    fun toVoxelShape(): VoxelShape = VoxelShapes.cuboid(
        minX / MODEL_SIZE,
        minY / MODEL_SIZE,
        minZ / MODEL_SIZE,
        maxX / MODEL_SIZE,
        maxY / MODEL_SIZE,
        maxZ / MODEL_SIZE,
    )

    /** 以方塊中心為軸將此方盒水平旋轉九十度。 */
    fun rotateQuarterTurn(): MahjongStoolCuboid = MahjongStoolCuboid(
        minX = MODEL_SIZE - maxZ,
        minY = minY,
        minZ = minX,
        maxX = MODEL_SIZE - minZ,
        maxY = maxY,
        maxZ = maxX,
    )

    private companion object {
        /** Minecraft 方塊模型每軸的像素數。 */
        const val MODEL_SIZE: Double = 16.0
    }
}
