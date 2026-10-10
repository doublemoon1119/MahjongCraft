package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

/**
 * 麻將桌單一座位的世界座標與朝向。
 *
 * @property x 置中後的世界 X 座標。
 * @property y 世界 Y 座標，與 controller 同高。
 * @property z 置中後的世界 Z 座標。
 * @property yaw 面向桌子中心（controller）的水平朝向角度。
 */
data class MahjongSeatPlacement(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)
