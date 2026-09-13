package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 回傳由 [startDegrees] 旋轉至 [endDegrees] 的最短有號角差，結果固定落在 `[-180, 180)`。
 */
fun shortestYawDeltaDegrees(startDegrees: Float, endDegrees: Float): Float {
    require(startDegrees.isFinite() && endDegrees.isFinite()) { "Yaw angles must be finite" }
    return ((endDegrees - startDegrees + HALF_TURN_DEGREES).mod(FULL_TURN_DEGREES)) - HALF_TURN_DEGREES
}

/** 一整圈的角度。 */
private const val FULL_TURN_DEGREES = 360.0f

/** 半圈的角度。 */
private const val HALF_TURN_DEGREES = 180.0f
