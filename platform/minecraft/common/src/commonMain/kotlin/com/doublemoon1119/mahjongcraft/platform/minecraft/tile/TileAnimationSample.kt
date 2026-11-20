package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一張牌在動畫中某一刻的樣子。
 *
 * @property placement 牌底中心的位置與水平朝向。
 * @property poseDegrees 牌模型局部 X 軸上的旋轉角度，動畫中可以介於兩個姿態之間。
 * @property lean 往前進方向的傾斜。
 * @property invisible 這一刻是否隱形，例如摸牌抬起後移到摸牌位之前。
 */
data class TileAnimationSample(
    val placement: MahjongTileWallPlacement,
    val poseDegrees: Float,
    val lean: TileLean,
    val invisible: Boolean,
)
