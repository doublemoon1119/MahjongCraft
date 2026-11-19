package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 選擇器展開清單的版面：清單與按鈕同寬，每個選項一列，底部可附一段說明。
 *
 * 清單通常在按鈕正下方；下方放不下完整清單、而上方空間比下方大時，改在按鈕正上方展開，例如畫面底部的選擇器。
 * 選擇的那一側仍放不下時只顯示放得下的幾列，其餘在清單內捲動。純資料，不依賴任何 Minecraft 型別。
 *
 * @property anchorLeft 選擇器按鈕的左界。
 * @property anchorTop 選擇器按鈕的上界。
 * @property anchorBottom 選擇器按鈕的下界。
 * @property width 選擇器按鈕的寬度。
 * @property optionCount 選項數量。
 * @property footnoteHeight 底部說明文字的高度；沒有說明時為 0。
 * @property windowHeight 視窗高度。
 * @property footnoteLineHeight 說明的單行高度，即文字繪製器的字高。
 * @property scrollIndex 第一個可見選項的索引；超出合法範圍的部分不採用。
 */
internal data class DropdownListLayout(
    val anchorLeft: Int,
    val anchorTop: Int,
    val anchorBottom: Int,
    val width: Int,
    val optionCount: Int,
    val footnoteHeight: Int,
    val windowHeight: Int,
    val footnoteLineHeight: Int,
    val scrollIndex: Int = 0,
) {
    /** 有說明區時的分隔線及上下內距。 */
    private val footnotePadding = 1 + FOOTNOTE_PADDING * 2

    /** 清單全部展開所需的高度。 */
    private val fullHeight = BORDER * 2 + optionCount * OPTION_HEIGHT +
        if (footnoteHeight > 0) footnotePadding + footnoteHeight else 0

    /** 按鈕下方可用的高度。 */
    private val spaceBelow = windowHeight - WINDOW_MARGIN - anchorBottom

    /** 按鈕上方可用的高度。 */
    private val spaceAbove = anchorTop - WINDOW_MARGIN

    /** 清單是否在按鈕上方展開。 */
    val opensUpward: Boolean = fullHeight > spaceBelow && spaceAbove > spaceBelow

    /** 兩側都無法放下一列時，容許清單覆蓋按鈕以保留操作空間。 */
    private val overlapsAnchor = maxOf(spaceAbove, spaceBelow) < BORDER * 2 + OPTION_HEIGHT

    /** 清單可用的總高度。 */
    private val availableHeight = when {
        overlapsAnchor -> windowHeight - WINDOW_MARGIN * 2
        opensUpward -> spaceAbove
        else -> spaceBelow
    }

    /** 可分配給選項與說明的高度，外框已扣除。 */
    private val contentHeight = (availableHeight - BORDER * 2).coerceAtLeast(0)

    /** 有空間時優先保留至少兩列選項。 */
    private val reservedOptions = minOf(2, optionCount, contentHeight / OPTION_HEIGHT)

    /** 保留選項與說明內距後，說明文字能使用的高度。 */
    private val footnoteSpace = (contentHeight - reservedOptions * OPTION_HEIGHT - footnotePadding).coerceAtLeast(0)

    /** 可完整繪製的說明行數，各選項共用同一份空間。 */
    val visibleFootnoteLines: Int = minOf(footnoteHeight, footnoteSpace) / footnoteLineHeight

    /** 底部說明區所佔的高度，含分隔線與上下內距。 */
    val footnoteBlockHeight: Int = if (visibleFootnoteLines > 0) footnotePadding + visibleFootnoteLines * footnoteLineHeight else 0

    /** 同時顯示的選項數。 */
    val visibleCount: Int = minOf(optionCount, (contentHeight - footnoteBlockHeight) / OPTION_HEIGHT)

    /** 最大合法捲動位置。 */
    val maximumScroll: Int = (optionCount - visibleCount).coerceAtLeast(0)

    /** 實際採用的第一個可見選項索引。 */
    val effectiveScroll: Int = scrollIndex.coerceIn(0, maximumScroll)

    /** 清單左界，含外框。 */
    val left: Int = anchorLeft

    /** 清單右界，含外框。 */
    val right: Int = anchorLeft + width

    /** 清單上界，含外框。 */
    val top: Int = run {
        val height = BORDER * 2 + visibleCount * OPTION_HEIGHT + footnoteBlockHeight
        val anchoredTop = if (opensUpward) anchorTop - height else anchorBottom
        if (overlapsAnchor) {
            anchoredTop.coerceIn(WINDOW_MARGIN, (windowHeight - WINDOW_MARGIN - height).coerceAtLeast(WINDOW_MARGIN))
        } else {
            anchoredTop
        }
    }

    /** 第一列選項的上界。 */
    val optionsTop: Int = top + BORDER

    /** 最後一列選項的下界。 */
    val optionsBottom: Int = optionsTop + visibleCount * OPTION_HEIGHT

    /** 底部說明文字的上界。 */
    val footnoteTop: Int = optionsBottom + 1 + FOOTNOTE_PADDING

    /** 清單下界，含外框。 */
    val bottom: Int = optionsBottom + footnoteBlockHeight + BORDER

    /** 清單內捲軸的垂直幾何。 */
    val scrollbar: ScrollbarLayout = ScrollbarLayout(
        trackTop = optionsTop,
        trackBottom = optionsBottom,
        itemCount = optionCount,
        visibleItemCount = visibleCount,
        scrollIndex = effectiveScroll,
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT.coerceAtMost(visibleCount * OPTION_HEIGHT),
    )

    /** 清單內捲軸的左界。 */
    val scrollbarLeft: Int = right - BORDER - VerticalScrollbar.WIDTH

    /**
     * 取得可見選項列的上界。
     *
     * @param visibleIndex 選項在可見範圍內的順序，從 0 開始。
     * @return 該列的上界。
     */
    fun optionTop(visibleIndex: Int): Int = optionsTop + visibleIndex * OPTION_HEIGHT

    /**
     * 找出游標所在的選項。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @return 選項在完整清單中的索引；游標不在任何選項上時為 null。
     */
    fun optionIndexAt(mouseX: Double, mouseY: Double): Int? {
        val optionRight = if (maximumScroll > 0) scrollbarLeft else right - BORDER
        if (mouseX < left + BORDER || mouseX >= optionRight || mouseY < optionsTop || mouseY >= optionsBottom) return null
        val index = effectiveScroll + ((mouseY - optionsTop) / OPTION_HEIGHT).toInt()
        return index.takeIf { it in 0 until optionCount }
    }

    /**
     * 判斷游標是否位於清單範圍內，含外框與底部說明。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @return 位於清單內時為 true。
     */
    fun contains(mouseX: Double, mouseY: Double): Boolean = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom

    internal companion object {
        /** 每個選項列的高度。 */
        const val OPTION_HEIGHT: Int = 20

        /** 外框寬度。 */
        const val BORDER: Int = 1

        /** 底部說明文字上下的內距。 */
        const val FOOTNOTE_PADDING: Int = 4

        /** 清單與視窗上下緣至少保留的距離。 */
        const val WINDOW_MARGIN: Int = 4
    }
}
