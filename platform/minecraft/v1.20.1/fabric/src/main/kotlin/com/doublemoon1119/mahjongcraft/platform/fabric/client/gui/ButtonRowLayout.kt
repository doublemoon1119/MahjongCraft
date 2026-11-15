package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 等寬排成一列的按鈕：橫跨指定寬度，相鄰按鈕之間保留固定間距。
 *
 * 寬度無法整除時，多出的像素由左至右各分給一顆按鈕，讓最後一顆的右界剛好對齊整列的右界。
 *
 * @property left 按鈕列左界。
 * @property width 按鈕列寬度。
 * @property buttonCount 按鈕數量。
 * @property gap 相鄰按鈕的間距。
 */
internal data class ButtonRowLayout(
    val left: Int,
    val width: Int,
    val buttonCount: Int,
    val gap: Int = BUTTON_GAP,
) {
    init {
        require(buttonCount > 0) { "Button row needs at least one button" }
        require(width > gap * (buttonCount - 1)) { "Button row is too narrow for its buttons" }
    }

    /** 扣除間距後可分給按鈕的總寬度。 */
    private val buttonSpace: Int = width - gap * (buttonCount - 1)

    /**
     * 取得按鈕寬度。
     *
     * @param index 按鈕由左至右的順序，從 0 開始。
     * @return 按鈕寬度。
     */
    fun buttonWidth(index: Int): Int {
        require(index in 0 until buttonCount) { "Button row has no button at index $index" }
        return buttonSpace / buttonCount + if (index < buttonSpace % buttonCount) 1 else 0
    }

    /**
     * 取得按鈕左界。
     *
     * @param index 按鈕由左至右的順序，從 0 開始。
     * @return 按鈕左界。
     */
    fun buttonX(index: Int): Int = left + (0 until index).sumOf { buttonWidth(it) + gap }

    internal companion object {
        /** 相鄰按鈕的預設間距。 */
        const val BUTTON_GAP: Int = 6
    }
}
