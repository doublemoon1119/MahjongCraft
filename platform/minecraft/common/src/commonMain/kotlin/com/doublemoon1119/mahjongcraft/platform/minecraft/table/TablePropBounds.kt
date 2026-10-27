package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 物件旋轉後的保守占用盒；Y 是物件底部，水平位置是中心。
 *
 * @property minX 左側邊界。
 * @property maxX 右側邊界。
 * @property minY 底部高度。
 * @property maxY 頂部高度。
 * @property minZ 前側邊界。
 * @property maxZ 後側邊界。
 */
internal data class TablePropBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val minZ: Double,
    val maxZ: Double,
) {
    /**
     * 檢查實體範圍是否重疊；上下或左右恰好相接不算重疊。
     *
     * @param other 要比較的占用盒。
     * @return 三個方向皆有實際重疊時為 true。
     */
    fun overlaps(other: TablePropBounds): Boolean = minX < other.maxX - EPSILON &&
        maxX > other.minX + EPSILON &&
        minY < other.maxY - EPSILON &&
        maxY > other.minY + EPSILON &&
        minZ < other.maxZ - EPSILON &&
        maxZ > other.minZ + EPSILON

    companion object {
        /** 用來吸收直角旋轉與上下堆疊浮點誤差的容差。 */
        private const val EPSILON = 1.0e-9

        /**
         * 由實際尺寸及朝向建立占用盒，不能只檢查物件中心點。
         *
         * @param target 物件落點。
         * @param dimensions 未旋轉前的實際尺寸。
         * @return 旋轉後的水平包圍盒與實際垂直範圍。
         */
        fun of(target: TablePropTarget, dimensions: TablePropDimensions): TablePropBounds {
            val radians = target.yaw.toDouble() * PI / 180.0
            val halfX = (abs(cos(radians)) * dimensions.width + abs(sin(radians)) * dimensions.depth) / 2
            val halfZ = (abs(sin(radians)) * dimensions.width + abs(cos(radians)) * dimensions.depth) / 2
            return TablePropBounds(target.x - halfX, target.x + halfX, target.y, target.y + dimensions.height, target.z - halfZ, target.z + halfZ)
        }
    }
}
