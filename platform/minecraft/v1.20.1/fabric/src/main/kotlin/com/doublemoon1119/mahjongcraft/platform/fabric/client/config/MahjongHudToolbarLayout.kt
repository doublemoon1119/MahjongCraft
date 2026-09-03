package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.math.roundToInt

/**
 * 工具列 scrollbar thumb 的水平邊界。
 *
 * @property left 左邊界。
 * @property right 右邊界。
 */
internal data class MahjongHudToolbarThumb(
    val left: Int,
    val right: Int,
) {
    /** 寬度。 */
    val width: Int
        get() = right - left
}

/**
 * HUD 位置編輯器頂部水平工具列的全部幾何計算與點擊區域判定，與原版 widget 完全分離。
 *
 * 工具列右側固定保留「其他 HUD 預覽」與「隱藏控制項」兩顆不參與水平捲動的按鈕，因此可捲動 viewport
 * 的右邊界比畫面寬度窄；內容超出 viewport 時才出現 scrollbar，未超出時整組按鈕在 viewport 內置中。
 *
 * @property screenWidth 目前 GUI scaled 畫面寬度。
 * @property contentWidth 工具列全部按鈕與間距所需的內容寬度。
 */
internal data class MahjongHudToolbarLayout(
    val screenWidth: Int,
    val contentWidth: Int,
) {
    /** 可捲動 viewport 的右邊界，保留固定按鈕所需空間。 */
    val viewportRight: Int
        get() = (screenWidth - MARGIN * 2 - HIDE_CONTROLS_WIDTH - OTHER_PREVIEW_WIDTH - GAP)
            .coerceAtLeast(MARGIN + 1)

    /** 可捲動 viewport 的可見寬度。 */
    val viewportWidth: Int
        get() = (viewportRight - MARGIN).coerceAtLeast(1)

    /** 內容是否超出可見寬度，決定是否需要 scrollbar。 */
    val hasOverflow: Boolean
        get() = contentWidth > viewportWidth

    /** 最大水平捲動量；沒有溢出時為零。 */
    val maximumScroll: Double
        get() = (contentWidth - viewportWidth).coerceAtLeast(0).toDouble()

    /** 依捲動量計算工具列內容的實際起始 X；未溢出時整組按鈕在 viewport 內置中。 */
    fun contentOffset(scroll: Double): Int = if (hasOverflow) {
        MARGIN - scroll.toInt()
    } else {
        MARGIN + (viewportWidth - contentWidth) / 2
    }

    /** 游標是否位於工具列按鈕列。 */
    fun isInsideButtons(mouseX: Double, mouseY: Double): Boolean = mouseX >= MARGIN && mouseX < viewportRight && mouseY >= TOP && mouseY < BOTTOM

    /** 游標是否位於包含 scrollbar 在內的整個工具列區域，決定滾輪是否轉為水平捲動。 */
    fun isInsideArea(mouseX: Double, mouseY: Double): Boolean = mouseX >= MARGIN &&
        mouseX < viewportRight &&
        mouseY >= TOP &&
        mouseY < SCROLLBAR_TOP + SCROLLBAR_HEIGHT

    /** 游標是否位於 scrollbar 軌道。 */
    fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = mouseX >= MARGIN &&
        mouseX < viewportRight &&
        mouseY >= SCROLLBAR_TOP &&
        mouseY < SCROLLBAR_TOP + SCROLLBAR_HEIGHT

    /** 依可見比例與目前捲動量計算 scrollbar thumb 邊界。 */
    fun thumb(scroll: Double): MahjongHudToolbarThumb {
        val thumbWidth = if (contentWidth <= 0) {
            viewportWidth
        } else {
            (viewportWidth.toDouble() * viewportWidth / contentWidth)
                .roundToInt()
                .coerceIn(MIN_THUMB_WIDTH, viewportWidth)
        }
        val travel = viewportWidth - thumbWidth
        val left = MARGIN + if (maximumScroll == 0.0) 0 else (scroll / maximumScroll * travel).roundToInt()
        return MahjongHudToolbarThumb(left = left, right = left + thumbWidth)
    }

    /** 將 thumb 左界轉換回捲動量，供直接點擊或拖曳 scrollbar 使用。 */
    fun scrollFromThumb(thumbLeft: Double): Double {
        val travel = (viewportWidth - thumb(0.0).width).coerceAtLeast(1)
        val relative = (thumbLeft - MARGIN).coerceIn(0.0, travel.toDouble())
        return relative / travel * maximumScroll
    }

    /** 依 thumb 拖曳位移計算新的捲動量，並限制在合法範圍內。 */
    fun scrollFromDrag(
        startScroll: Double,
        pointerDelta: Double,
    ): Double {
        val travel = (viewportWidth - thumb(startScroll).width).coerceAtLeast(1)
        return (startScroll + pointerDelta / travel * maximumScroll).coerceIn(0.0, maximumScroll)
    }

    /** 依滾輪量計算新的捲動量，並限制在合法範圍內。 */
    fun scrollFromWheel(
        currentScroll: Double,
        amount: Double,
    ): Double = (currentScroll - amount * SCROLL_STEP).coerceIn(0.0, maximumScroll)

    /** 依錨點按鈕位置計算下拉選單的左界，確保 popup 不會超出畫面。 */
    fun dropdownLeft(anchorX: Int): Int = anchorX.coerceIn(MARGIN, (screenWidth - MARGIN - DROPDOWN_POPUP_WIDTH).coerceAtLeast(MARGIN))

    /**
     * 找出下拉選單中被點擊的項目索引。
     *
     * @return 命中的項目索引；點擊落在 popup 範圍外或超出項目數量時為 `null`。
     */
    fun dropdownOptionIndexAt(
        mouseX: Double,
        mouseY: Double,
        anchorX: Int,
        optionCount: Int,
    ): Int? {
        val left = dropdownLeft(anchorX)
        if (mouseX < left || mouseX >= left + DROPDOWN_POPUP_WIDTH || mouseY < POPUP_TOP) return null
        val index = ((mouseY - POPUP_TOP) / DROPDOWN_OPTION_HEIGHT).toInt()
        return index.takeIf { it in 0 until optionCount }
    }

    /** 工具列與下拉選單的版位常數。 */
    internal companion object {
        /** 工具列左右邊距。 */
        internal const val MARGIN = 12

        /** 工具列按鈕上界。 */
        internal const val TOP = 26

        /** 工具列按鈕高度。 */
        internal const val BUTTON_HEIGHT = 20

        /** 工具列按鈕下界。 */
        internal const val BOTTOM = TOP + BUTTON_HEIGHT

        /** scrollbar 上界。 */
        internal const val SCROLLBAR_TOP = BOTTOM + 3

        /** scrollbar 高度。 */
        internal const val SCROLLBAR_HEIGHT = 4

        /** 工具列按鈕間距。 */
        internal const val GAP = 4

        /** 每個下拉選單按鈕寬度。 */
        internal const val DROPDOWN_WIDTH = 132

        /** 固定隱藏控制項按鈕寬度。 */
        internal const val HIDE_CONTROLS_WIDTH = 104

        /** 固定其他 HUD 預覽按鈕寬度。 */
        internal const val OTHER_PREVIEW_WIDTH = 156

        /** 下拉 popup 寬度。 */
        internal const val DROPDOWN_POPUP_WIDTH = 156

        /** 下拉 popup 每列高度。 */
        internal const val DROPDOWN_OPTION_HEIGHT = 20

        /** 下拉 popup 上界。 */
        internal const val POPUP_TOP = SCROLLBAR_TOP + SCROLLBAR_HEIGHT + 4

        /** scrollbar thumb 最小寬度。 */
        internal const val MIN_THUMB_WIDTH = 18

        /** 滾輪每格移動距離。 */
        internal const val SCROLL_STEP = 48.0
    }
}
