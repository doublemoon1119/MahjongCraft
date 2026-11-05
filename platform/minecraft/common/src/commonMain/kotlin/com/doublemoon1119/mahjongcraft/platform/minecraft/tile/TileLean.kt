package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 牌繞著底部中心、以世界水平軸傾斜的角度；用來表現移動時往前進方向倒。
 *
 * @property axisX 旋轉軸的世界 X 分量，軸是水平單位向量。
 * @property axisZ 旋轉軸的世界 Z 分量。
 * @property degrees 傾斜角度；正值時牌的上端倒向前進方向。
 */
data class TileLean(
    val axisX: Double,
    val axisZ: Double,
    val degrees: Float,
) {
    /** [TileLean] 的固定值。 */
    companion object {
        /** 沒有傾斜。 */
        val NONE: TileLean = TileLean(axisX = 0.0, axisZ = 0.0, degrees = 0.0f)
    }
}
