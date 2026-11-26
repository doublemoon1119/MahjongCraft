package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一張牌牆用牌的版本無關世界呈現位置。
 *
 * @property x 世界 X 座標，牌底面中心。
 * @property y 世界 Y 座標，牌底面中心。
 * @property z 世界 Z 座標，牌底面中心。
 * @property yaw 世界水平朝向角度。
 */
data class MahjongTileWallPlacement(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)
