package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

/**
 * HUD 矩形在 GUI 縮放後畫面中的位置與尺寸，單位為像素。
 *
 * @property left 左邊界。
 * @property top 上邊界。
 * @property width 寬度。
 * @property height 高度。
 */
data class MahjongHudBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    /** 右邊界。 */
    val right: Int get() = left + width

    /** 下邊界。 */
    val bottom: Int get() = top + height

    /**
     * 判斷畫面座標是否落在矩形內，右界與下界不包含在內。
     *
     * @param x 水平座標。
     * @param y 垂直座標。
     * @return 座標在矩形內時為 true。
     */
    fun contains(x: Double, y: Double): Boolean = x >= left && x < right && y >= top && y < bottom
}
