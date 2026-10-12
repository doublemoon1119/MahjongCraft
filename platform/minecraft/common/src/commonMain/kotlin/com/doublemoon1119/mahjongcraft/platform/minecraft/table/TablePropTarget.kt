package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.math.abs

/**
 * 一個規則桌面物件在世界中的身分與落點。
 *
 * @property kind 物件種類識別碼。
 * @property variant 種類內的變體。
 * @property x 世界 X 座標。
 * @property y 世界 Y 座標。
 * @property z 世界 Z 座標。
 * @property yaw 水平朝向（度）。
 */
data class TablePropTarget(
    val kind: String,
    val variant: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
) {
    /**
     * 判斷另一目標是否代表同一個桌面物件。
     *
     * @param other 要比較的目標。
     * @return 種類、變體、座標與朝向都相符時為 `true`。
     */
    fun matches(other: TablePropTarget): Boolean = kind == other.kind &&
        variant == other.variant &&
        abs(x - other.x) <= POSITION_EPSILON &&
        abs(y - other.y) <= POSITION_EPSILON &&
        abs(z - other.z) <= POSITION_EPSILON &&
        yawDistance(yaw, other.yaw) <= YAW_EPSILON

    private companion object {
        /** 世界座標比對容差；足以吸收 entity 座標的浮點往返誤差，又不會混淆相鄰落點。 */
        const val POSITION_EPSILON: Double = 1.0e-4

        /** 水平朝向比對容差（度）。 */
        const val YAW_EPSILON: Float = 1.0e-3f

        /** 一圈角度。 */
        const val FULL_ROTATION_DEGREES: Float = 360.0f

        /**
         * 計算兩個水平角度在環狀角度空間中的最短距離，讓 `180` 與 `-180` 視為同一朝向。
         *
         * @param first 第一個角度。
         * @param second 第二個角度。
         * @return 兩角度間不大於半圈的距離。
         */
        fun yawDistance(first: Float, second: Float): Float {
            val difference = abs((first - second) % FULL_ROTATION_DEGREES)
            return minOf(difference, FULL_ROTATION_DEGREES - difference)
        }
    }
}
