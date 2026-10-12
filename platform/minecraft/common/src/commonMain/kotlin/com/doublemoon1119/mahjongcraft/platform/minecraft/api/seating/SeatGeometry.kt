package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

/**
 * 入座後乘坐者腳底的位置與面向，不是家具或隱形載具的中心。
 *
 * @property x 水平 X 座標。
 * @property y 腳底高度。
 * @property z 水平 Z 座標。
 * @property yaw 朝向桌心的 Minecraft 水平角度。
 */
data class SeatGeometry(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite() && yaw.isFinite()) { "Seat geometry must be finite" }
    }
}
