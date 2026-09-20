package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 標出「這張是鳴取來的牌」的倒三角形指標。
 *
 * 由上而下逐列縮減寬度的色塊疊成，階梯狀外觀是刻意的：決策卡片受螢幕像素限制只能這樣畫，世界中的鳴牌提示
 * 沿用同一份描述，兩邊看起來才是同一個標記。
 *
 * 只有吃會用到：碰與槓的牌面彼此完全相同，標記沒有辨識意義。
 */
object ClaimedTileMarker {
    /** 由上而下每列的寬度，以決策卡片的像素為單位；皆為奇數以確保左右對稱。 */
    val ROW_WIDTHS: IntArray = intArrayOf(9, 7, 5, 3, 1)

    /** 指標顏色，用鮮明的紅色與牌面色調完全區隔，不受任何背景深淺影響辨識度。 */
    const val COLOR: Int = 0xFFFF5555.toInt()

    /** 指標與牌面之間的間距，單位同 [ROW_WIDTHS]。 */
    const val GAP: Int = 3

    /** 指標總高度，等於列數。 */
    val height: Int
        get() = ROW_WIDTHS.size

    /** 指標最寬那一列的寬度。 */
    val width: Int
        get() = ROW_WIDTHS.max()
}
