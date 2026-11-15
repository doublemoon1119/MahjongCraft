package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.gui.DrawContext

/**
 * 一列一列的版面中，隔列加上的淡白色底色範圍；用來在標籤與右端的值或控制項相距很遠時，讓眼睛對得上同一列。
 *
 * @property left 左界。
 * @property top 上界。
 * @property right 右界（不含）。
 * @property bottom 下界（不含）。
 */
internal data class RowStripe(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /**
     * 只在指定列為奇數列時繪製底色；列號以完整清單計算，捲動時同一列的顏色不變。
     *
     * @param context 畫面繪製情境。
     * @param rowIndex 這一列在完整清單中的順序，從 0 開始。
     */
    fun renderIfStriped(context: DrawContext, rowIndex: Int) {
        if (isStriped(rowIndex)) context.fill(left, top, right, bottom, COLOR)
    }

    internal companion object {
        /** 底色的 ARGB 顏色：約 4% 不透明的白色。 */
        const val COLOR: Int = 0x0AFFFFFF

        /**
         * 判斷指定列是否加底色。
         *
         * @param rowIndex 這一列在完整清單中的順序，從 0 開始。
         * @return 第一列不加、第二列加，依此交替。
         */
        fun isStriped(rowIndex: Int): Boolean = rowIndex % 2 == 1
    }
}
