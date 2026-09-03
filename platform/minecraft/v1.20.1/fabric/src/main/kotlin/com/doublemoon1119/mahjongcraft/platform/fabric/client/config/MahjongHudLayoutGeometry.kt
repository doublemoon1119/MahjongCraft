package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.math.roundToInt

/** HUD 矩形在目前 GUI scaled 畫面中的位置與尺寸。 */
data class MahjongHudBounds(
    /** 左邊界。 */
    val left: Int,
    /** 上邊界。 */
    val top: Int,
    /** 寬度。 */
    val width: Int,
    /** 高度。 */
    val height: Int,
) {
    /** 右邊界。 */
    val right: Int
        get() = left + width

    /** 下邊界。 */
    val bottom: Int
        get() = top + height

    /** 判斷畫面座標是否落在矩形內。 */
    fun contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom
}

/** 將合法移動範圍的比例轉換為不超出畫面的左上角座標。 */
fun hudCoordinate(ratio: Double, screenSize: Int, elementSize: Int): Int {
    val travel = (screenSize - elementSize).coerceAtLeast(0)
    return (travel * ratio.coerceIn(0.0, 1.0)).roundToInt()
}

/** 將左上角座標轉回合法移動範圍的比例。 */
fun hudRatio(coordinate: Int, screenSize: Int, elementSize: Int): Double {
    val travel = (screenSize - elementSize).coerceAtLeast(0)
    return if (travel == 0) 0.0 else coordinate.coerceIn(0, travel).toDouble() / travel
}
