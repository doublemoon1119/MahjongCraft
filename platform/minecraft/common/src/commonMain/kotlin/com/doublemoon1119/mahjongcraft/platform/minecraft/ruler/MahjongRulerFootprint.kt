package com.doublemoon1119.mahjongcraft.platform.minecraft.ruler

/**
 * 牌尺放在地上時，水平方向佔用的範圍，以牌尺中心為原點。
 *
 * @property halfX 沿世界 X 軸的半寬。
 * @property halfZ 沿世界 Z 軸的半寬。
 */
data class MahjongRulerFootprint(
    val halfX: Double,
    val halfZ: Double,
)
