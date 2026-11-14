package com.doublemoon1119.mahjongcraft.platform.minecraft.ruler

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 牌尺的世界尺寸與佔用範圍。
 *
 * 牌尺是放在方塊上的長條，長邊與放置者的視線垂直，可以平放或豎放；尺寸依實體牌尺與牌的比例換算，長度約等於一面牌牆。
 */
object MahjongRulerDimensions {
    /** 牌尺長度相當於幾張牌並排的寬度。 */
    private const val LENGTH_IN_TILES: Int = 18

    /** 牌尺長度。 */
    const val LENGTH: Double = MahjongTileDimensions.TILE_WIDTH * LENGTH_IN_TILES

    /** 牌尺截面較寬的一邊；豎放時是高度，平放時是寬度。 */
    const val BROAD_SIDE: Double = 0.085

    /** 牌尺截面較窄的一邊；豎放時是寬度，平放時是高度。 */
    const val NARROW_SIDE: Double = 0.04

    /**
     * 牌尺的高度。
     *
     * @param posture 牌尺的擺法。
     * @return 牌尺頂面到底面的距離。
     */
    fun height(posture: MahjongRulerPosture): Double = when (posture) {
        MahjongRulerPosture.FLAT -> NARROW_SIDE
        MahjongRulerPosture.UPRIGHT -> BROAD_SIDE
    }

    /**
     * 牌尺水平方向與長邊垂直的寬度。
     *
     * @param posture 牌尺的擺法。
     * @return 牌尺兩側面之間的距離。
     */
    fun width(posture: MahjongRulerPosture): Double = when (posture) {
        MahjongRulerPosture.FLAT -> BROAD_SIDE
        MahjongRulerPosture.UPRIGHT -> NARROW_SIDE
    }

    /**
     * 牌尺依朝向轉動後，水平方向佔用的範圍。
     *
     * @param yawDegrees 牌尺的朝向角度，與 Minecraft 實體的 yaw 相同。
     * @param posture 牌尺的擺法。
     * @return 包住整支牌尺的水平範圍。
     */
    fun footprint(
        yawDegrees: Double,
        posture: MahjongRulerPosture,
    ): MahjongRulerFootprint {
        val radians = yawDegrees * PI / HALF_TURN_DEGREES
        val alongX = abs(cos(radians))
        val alongZ = abs(sin(radians))
        val width = width(posture)
        return MahjongRulerFootprint(
            halfX = (alongX * LENGTH + alongZ * width) / 2.0,
            halfZ = (alongZ * LENGTH + alongX * width) / 2.0,
        )
    }

    /** 半圈的角度。 */
    private const val HALF_TURN_DEGREES: Double = 180.0
}
