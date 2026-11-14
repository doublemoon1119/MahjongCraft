package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 設定頁底部按鈕列：重設為預設、復原、套用、完成四顆按鈕等寬排成一列，橫跨整個內容寬度。
 *
 * 寬度無法整除時，多出的像素由左至右各分給一顆按鈕，讓最後一顆的右界剛好對齊內容右界。
 *
 * @property left 按鈕列左界。
 * @property width 按鈕列寬度。
 * @property gap 相鄰按鈕的間距。
 */
internal data class SettingsFooterLayout(
    val left: Int,
    val width: Int,
    val gap: Int = BUTTON_GAP,
) {
    init {
        require(width > gap * (BUTTON_COUNT - 1)) { "Settings footer is too narrow for its buttons" }
    }

    /** 扣除間距後可分給按鈕的總寬度。 */
    private val buttonSpace: Int = width - gap * (BUTTON_COUNT - 1)

    /**
     * 取得按鈕寬度。
     *
     * @param index 按鈕順序，0 為重設為預設、1 為復原、2 為套用、3 為完成。
     * @return 按鈕寬度。
     */
    fun buttonWidth(index: Int): Int {
        require(index in 0 until BUTTON_COUNT) { "Settings footer has no button at index $index" }
        return buttonSpace / BUTTON_COUNT + if (index < buttonSpace % BUTTON_COUNT) 1 else 0
    }

    /**
     * 取得按鈕左界。
     *
     * @param index 按鈕順序，0 為重設為預設、1 為復原、2 為套用、3 為完成。
     * @return 按鈕左界。
     */
    fun buttonX(index: Int): Int = left + (0 until index).sumOf { buttonWidth(it) + gap }

    internal companion object {
        /** 按鈕數量。 */
        const val BUTTON_COUNT: Int = 4

        /** 相鄰按鈕的預設間距。 */
        const val BUTTON_GAP: Int = 6

        /** 重設為預設按鈕的順序。 */
        const val RESET: Int = 0

        /** 復原按鈕的順序。 */
        const val UNDO: Int = 1

        /** 套用按鈕的順序。 */
        const val APPLY: Int = 2

        /** 完成按鈕的順序。 */
        const val DONE: Int = 3
    }
}
