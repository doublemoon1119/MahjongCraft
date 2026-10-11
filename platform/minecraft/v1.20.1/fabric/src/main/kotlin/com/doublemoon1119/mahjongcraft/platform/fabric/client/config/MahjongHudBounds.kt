package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

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
    val right: Int get() = left + width

    /** 下邊界。 */
    val bottom: Int get() = top + height

    /** 判斷畫面座標是否落在矩形內。 */
    fun contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom
}
