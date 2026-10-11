package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/**
 * 以模型像素表示的麻將凳長方體，供模型資源與 Fabric 碰撞測試對齊。
 *
 * @property minX X 軸最小座標。
 * @property minY Y 軸最小座標。
 * @property minZ Z 軸最小座標。
 * @property maxX X 軸最大座標。
 * @property maxY Y 軸最大座標。
 * @property maxZ Z 軸最大座標。
 */
data class MahjongStoolCuboid(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
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

    /** 模型像素與方塊單位的換算。 */
    internal companion object {
        /** Minecraft 方塊模型每軸的像素數。 */
        const val MODEL_SIZE: Double = 16.0
    }
}
