package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/** Reset to Defaults 與三個等寬動作按鈕共用的固定單行 footer 幾何。 */
internal data class SettingsFooterLayout(
    /** Reset 按鈕左界。 */
    val resetX: Int,
    /** Reset 按鈕寬度。 */
    val resetWidth: Int,
    /** Undo、Apply、Done 共用寬度。 */
    val actionWidth: Int,
    /** 按鈕間距。 */
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

    internal companion object {
        /** 在指定邊界內建立不換行的 footer。 */
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
