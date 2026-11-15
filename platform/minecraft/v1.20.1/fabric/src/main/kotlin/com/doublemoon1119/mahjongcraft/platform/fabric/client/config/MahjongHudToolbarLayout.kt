package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ButtonRowLayout

/**
 * HUD 位置編輯器的工具列與底部按鈕列版面，與原版 widget 完全分離。
 *
 * 工具列有四個控制項：HUD、預覽情境、其他 HUD 的顯示方式與隱藏控制項，每個控制項上方留一行標籤。寬度足夠時四個排成
 * 一列；不夠時改成兩欄兩列，底部四顆按鈕也同樣改成兩欄兩列，離開畫面的按鈕都在右下角。編輯器橫跨整個視窗，不限制內容寬度。
 *
 * @property screenWidth 目前 GUI scaled 畫面寬度。
 * @property screenHeight 目前 GUI scaled 畫面高度。
 */
internal data class MahjongHudToolbarLayout(
    val screenWidth: Int,
    val screenHeight: Int,
) {
    /** 工具列與底部按鈕列可用的寬度。 */
    private val contentWidth: Int = (screenWidth - MARGIN * 2).coerceAtLeast(ButtonRowLayout.minimumWidth(CONTROL_COUNT))

    /** 每一列放幾個控制項：寬度足夠時四個，否則兩個。 */
    val columns: Int = if ((contentWidth - ButtonRowLayout.BUTTON_GAP * (CONTROL_COUNT - 1)) / CONTROL_COUNT >= MIN_CONTROL_WIDTH) CONTROL_COUNT else 2

    /** 工具列與底部按鈕列的列數。 */
    val rows: Int = CONTROL_COUNT / columns

    /** 每一列的欄位分配。 */
    private val grid = ButtonRowLayout(left = MARGIN, width = contentWidth, buttonCount = columns)

    /** 工具列下界。 */
    val toolbarBottom: Int = TOP + rows * (CAPTION_HEIGHT + BUTTON_HEIGHT) + (rows - 1) * ROW_GAP

    /** 底部按鈕列第一列的上界。 */
    val footerTop: Int = (screenHeight - FOOTER_BOTTOM_MARGIN - rows * BUTTON_HEIGHT - (rows - 1) * ROW_GAP).coerceAtLeast(toolbarBottom)

    /**
     * 取得工具列控制項的範圍。
     *
     * @param index 控制項順序：[HUD]、[SCENARIO]、[VISIBILITY]、[HIDE_CONTROLS]。
     * @return 控制項本身的範圍，不含上方的標籤。
     */
    fun control(index: Int): Area {
        require(index in 0 until CONTROL_COUNT) { "HUD toolbar has no control at index $index" }
        val row = index / columns
        val column = index % columns
        return Area(
            x = grid.buttonX(column),
            y = TOP + row * (CAPTION_HEIGHT + BUTTON_HEIGHT + ROW_GAP) + CAPTION_HEIGHT,
            width = grid.buttonWidth(column),
            height = BUTTON_HEIGHT,
        )
    }

    /**
     * 取得控制項上方標籤文字的 Y 座標。
     *
     * @param index 控制項順序。
     * @return 標籤文字的 Y 座標。
     */
    fun captionY(index: Int): Int = control(index).y - CAPTION_HEIGHT + 1

    /**
     * 取得底部按鈕的範圍；兩列時前兩顆在上、後兩顆在下。
     *
     * @param index 按鈕順序，與設定頁底部按鈕相同。
     * @return 按鈕的範圍。
     */
    fun footerButton(index: Int): Area {
        require(index in 0 until CONTROL_COUNT) { "HUD editor footer has no button at index $index" }
        val row = index / columns
        val column = index % columns
        return Area(
            x = grid.buttonX(column),
            y = footerTop + row * (BUTTON_HEIGHT + ROW_GAP),
            width = grid.buttonWidth(column),
            height = BUTTON_HEIGHT,
        )
    }

    /**
     * 矩形範圍。
     *
     * @property x 左界。
     * @property y 上界。
     * @property width 寬度。
     * @property height 高度。
     */
    data class Area(val x: Int, val y: Int, val width: Int, val height: Int) {
        /** 右界。 */
        val right: Int get() = x + width

        /** 下界。 */
        val bottom: Int get() = y + height

        /**
         * 判斷座標是否落在範圍內。
         *
         * @param pointX 水平座標。
         * @param pointY 垂直座標。
         * @return 落在範圍內時為 true。
         */
        fun contains(pointX: Double, pointY: Double): Boolean = pointX >= x && pointX < right && pointY >= y && pointY < bottom
    }

    /** 版位常數與控制項順序。 */
    internal companion object {
        /** 左右邊距。 */
        const val MARGIN = 12

        /** 工具列上界，位於標題下方。 */
        const val TOP = 22

        /** 控制項上方標籤所佔的高度。 */
        const val CAPTION_HEIGHT = 11

        /** 按鈕高度。 */
        const val BUTTON_HEIGHT = 20

        /** 兩列之間的垂直間距。 */
        const val ROW_GAP = 4

        /** 四個控制項排成一列時，每個控制項至少要有的寬度。 */
        const val MIN_CONTROL_WIDTH = 90

        /** 底部按鈕列與視窗底部的距離。 */
        const val FOOTER_BOTTOM_MARGIN = 8

        /** 工具列控制項與底部按鈕的數量。 */
        const val CONTROL_COUNT = 4

        /** HUD 選擇器。 */
        const val HUD = 0

        /** 預覽情境按鈕。 */
        const val SCENARIO = 1

        /** 其他 HUD 顯示方式按鈕。 */
        const val VISIBILITY = 2

        /** 隱藏控制項按鈕。 */
        const val HIDE_CONTROLS = 3
    }
}
