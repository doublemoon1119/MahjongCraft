package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 將恢復預設值按鈕及三個等寬動作按鈕排成一列的頁尾位置。
 *
 * @property resetX 恢復預設值按鈕的左界。
 * @property resetWidth 恢復預設值按鈕的寬度。
 * @property actionWidth 復原、套用、套用並返回按鈕的共用寬度。
 * @property gap 相鄰按鈕的間距。
 */
internal data class SettingsFooterLayout(
    val resetX: Int,
    val resetWidth: Int,
    val actionWidth: Int,
    val gap: Int,
) {
    /** Undo 按鈕左界。 */
    val undoX: Int = resetX + resetWidth + gap

    /** Apply 按鈕左界。 */
    val applyX: Int = undoX + actionWidth + gap

    /** Done 按鈕左界。 */
    val doneX: Int = applyX + actionWidth + gap

    /** Footer 右界。 */
    val right: Int = doneX + actionWidth

    /** 依可用寬度分配頁尾按鈕的位置。 */
    internal companion object {
        /**
         * 在指定範圍內建立單行頁尾；整數除法剩餘的空間分配在左右兩端。
         *
         * @param left 可用範圍的左界。
         * @param availableWidth 可用寬度。
         * @param preferredResetWidth 恢復預設值按鈕的期望寬度，最多使用可用寬度的五分之二。
         * @param gap 相鄰按鈕的間距。
         * @return 按鈕寬度與水平位置。
         * @throws IllegalArgumentException 可用寬度不足以容納間距，或分配後的動作按鈕寬度不大於 0 時拋出。
         */
        fun create(left: Int, availableWidth: Int, preferredResetWidth: Int, gap: Int): SettingsFooterLayout {
            require(availableWidth > gap * 3)
            val resetWidth = minOf(preferredResetWidth, availableWidth * 2 / 5)
            val actionWidth = (availableWidth - resetWidth - gap * 3) / 3
            require(actionWidth > 0)
            val usedWidth = resetWidth + actionWidth * 3 + gap * 3
            return SettingsFooterLayout(left + (availableWidth - usedWidth) / 2, resetWidth, actionWidth, gap)
        }
    }
}
