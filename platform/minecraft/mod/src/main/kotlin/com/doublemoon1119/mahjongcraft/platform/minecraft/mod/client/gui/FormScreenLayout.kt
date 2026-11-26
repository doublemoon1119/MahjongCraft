package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

/**
 * 由分類頁籤（可省略）、一列列欄位與底部按鈕列組成的畫面版面，例如設定頁：標題、頁面切換、選擇器列、分類頁籤、
 * 欄位區與底部按鈕列由上到下的位置。
 *
 * - 視窗寬度低於 [COMPACT_MIN_WIDTH] 時改用窄版：欄位的標籤在上、控制項在下並佔滿內容寬度，規則列改為兩欄並排。
 * - 內容寬度最多 [MAX_CONTENT_WIDTH] 並水平置中，所有區塊共用同一組左右邊界。
 * - 純資料，不依賴任何 Minecraft 型別。文字寬度由呼叫端量好傳入。
 *
 * @property windowWidth 目前視窗寬度。
 * @property windowHeight 目前視窗高度。
 * @property hasPageSwitch 標題下方是否有頁面切換按鈕。
 * @property hasRuleRow 分類頁籤上方是否有規則列。
 * @property ruleLabelWidth 規則列「規則」標籤的文字寬度。
 * @property variantLabelWidth 規則列「玩法」標籤的文字寬度。
 * @property hasTabs 欄位區上方是否有分類頁籤。沒有時欄位區緊接在上一個區塊下方。
 * @property headerHeight 分類頁籤上方保留給說明文字的高度，從 [headerTop] 開始。不需要時為 0。
 */
internal data class FormScreenLayout(
    val windowWidth: Int,
    val windowHeight: Int,
    val hasPageSwitch: Boolean,
    val hasRuleRow: Boolean,
    val ruleLabelWidth: Int = 0,
    val variantLabelWidth: Int = 0,
    val hasTabs: Boolean = true,
    val headerHeight: Int = 0,
) {
    /** 是否使用窄版。 */
    val isCompact: Boolean = windowWidth < COMPACT_MIN_WIDTH

    /** 內容寬度。 */
    val contentWidth: Int = (windowWidth - (if (isCompact) COMPACT_MARGIN else REGULAR_MARGIN) * 2)
        .coerceAtMost(MAX_CONTENT_WIDTH)
        .coerceAtLeast(1)

    /** 內容左界。 */
    val contentLeft: Int = (windowWidth - contentWidth) / 2

    /** 內容右界。 */
    val contentRight: Int = contentLeft + contentWidth

    /** 規則列。畫面沒有規則列時為 null。 */
    val ruleRow: SelectorRowLayout? = if (hasRuleRow) {
        SelectorRowLayout(
            top = if (hasPageSwitch) PAGE_SWITCH_Y + BUTTON_HEIGHT + SECTION_GAP else TITLE_BOTTOM,
            contentLeft = contentLeft,
            contentWidth = contentWidth,
            compact = isCompact,
            ruleLabelWidth = ruleLabelWidth,
            variantLabelWidth = variantLabelWidth,
        )
    } else {
        null
    }

    /** 說明文字區上界。說明文字區之後接著分類頁籤列。 */
    val headerTop: Int = when {
        ruleRow != null -> ruleRow.dividerY + 1 + RULE_DIVIDER_GAP
        hasPageSwitch -> PAGE_SWITCH_Y + BUTTON_HEIGHT + SECTION_GAP
        else -> TITLE_BOTTOM
    }

    /** 分類頁籤列上界。 */
    val tabsTop: Int = headerTop + headerHeight

    /** 欄位區上界。 */
    val fieldsTop: Int = if (hasTabs) tabsTop + TabStripLayout.SELECTED_HEIGHT + 1 + FIELDS_TOP_GAP else tabsTop

    /** 底部按鈕列上界。 */
    val footerY: Int = windowHeight - FOOTER_BOTTOM_MARGIN - BUTTON_HEIGHT

    /** 底部按鈕列上方狀態文字的 Y 座標。 */
    val statusY: Int = footerY - STATUS_HEIGHT

    /** 欄位區下界。 */
    val fieldsBottom: Int = statusY - STATUS_GAP

    /** 相鄰兩個欄位上界之間的距離。 */
    val fieldRowHeight: Int = if (isCompact) COMPACT_FIELD_ROW_HEIGHT else REGULAR_FIELD_ROW_HEIGHT

    /** 目前視窗能完整容納的欄位數。 */
    val visibleFieldCount: Int = completeRowCount(
        rowHeight = fieldRowHeight,
        controlHeight = BUTTON_HEIGHT + if (isCompact) COMPACT_LABEL_HEIGHT else 0,
    )

    /**
     * 計算欄位區內能放下的完整列數。
     *
     * @param rowHeight 相鄰列的間距。
     * @param controlHeight 單列實際需要的高度。
     * @return 完整可見列數。不夠一列時為 0。
     */
    fun completeRowCount(rowHeight: Int, controlHeight: Int): Int {
        val remaining = fieldsBottom - fieldsTop - controlHeight
        return if (remaining < 0) 0 else 1 + remaining / rowHeight
    }

    /** 控制項寬度。 */
    val controlWidth: Int = if (isCompact) contentWidth else CONTROL_WIDTH.coerceAtMost(contentWidth)

    /** 控制項左界。 */
    val controlLeft: Int = contentRight - controlWidth

    /** 標籤可用的寬度。 */
    val labelWidth: Int = if (isCompact) contentWidth else (controlLeft - LABEL_GAP - contentLeft).coerceAtLeast(1)

    /** 數值欄位減號按鈕的左界。 */
    val integerMinusX: Int = controlLeft

    /** 數值欄位輸入框的左界。 */
    val integerTextX: Int = controlLeft + INTEGER_BUTTON_WIDTH + INTEGER_GAP

    /** 數值欄位輸入框的寬度。 */
    val integerTextWidth: Int = (controlWidth - (INTEGER_BUTTON_WIDTH + INTEGER_GAP) * 2).coerceAtLeast(1)

    /** 數值欄位加號按鈕的左界。 */
    val integerPlusX: Int = controlLeft + controlWidth - INTEGER_BUTTON_WIDTH

    /** 欄位捲軸的左界。捲軸畫在內容右側的邊距內，不佔用控制項的寬度。 */
    val scrollbarLeft: Int = contentRight + SCROLLBAR_GAP

    /**
     * 建立橫跨內容寬度的底部按鈕列。
     *
     * @param buttonCount 按鈕數量。
     * @return 等寬排成一列的按鈕位置。內容寬度連最小按鈕都放不下時，按鈕列會超出內容寬度。
     */
    fun footer(buttonCount: Int): ButtonRowLayout = ButtonRowLayout(
        left = contentLeft,
        width = contentWidth.coerceAtLeast(ButtonRowLayout.minimumWidth(buttonCount)),
        buttonCount = buttonCount,
    )

    /**
     * 取得第幾個可見欄位的上界。
     *
     * @param visibleIndex 欄位在可見範圍內的順序，從 0 開始。
     * @return 欄位列的上界。
     */
    fun fieldRowTop(visibleIndex: Int): Int = fieldsTop + visibleIndex * fieldRowHeight

    /**
     * 取得欄位列的隔列底色範圍：上下各延伸到相鄰列的中間，相鄰兩列的底色剛好銜接。左右比內容寬一點，不碰到捲軸。
     *
     * @param visibleIndex 欄位在可見範圍內的順序，從 0 開始。
     * @return 底色的左、上、右、下界。
     */
    fun fieldStripe(visibleIndex: Int): RowStripe {
        val top = fieldRowTop(visibleIndex) - (fieldRowHeight - BUTTON_HEIGHT - if (isCompact) COMPACT_LABEL_HEIGHT else 0) / 2
        return RowStripe(left = contentLeft - STRIPE_INSET, top = top, right = contentRight + STRIPE_INSET, bottom = top + fieldRowHeight)
    }

    /**
     * 取得欄位標籤文字的 Y 座標。
     *
     * @param rowTop 欄位列的上界。
     * @return 寬版與控制項垂直置中、窄版位於列頂端的文字 Y 座標。
     */
    fun fieldLabelY(rowTop: Int): Int = if (isCompact) rowTop else rowTop + (BUTTON_HEIGHT - VISIBLE_TEXT_HEIGHT) / 2

    /**
     * 取得欄位控制項的上界。
     *
     * @param rowTop 欄位列的上界。
     * @return 寬版與列同高、窄版位於標籤下方的控制項上界。
     */
    fun fieldControlY(rowTop: Int): Int = if (isCompact) rowTop + COMPACT_LABEL_HEIGHT else rowTop

    /**
     * 建立欄位捲軸的垂直幾何。
     *
     * @param fieldCount 目前分類的欄位總數。
     * @param scrollIndex 第一個可見欄位的索引。
     * @return 捲軸的軌道與滑塊位置。
     */
    fun scrollbar(fieldCount: Int, scrollIndex: Int): ScrollbarLayout = ScrollbarLayout(
        trackTop = fieldsTop,
        trackBottom = fieldsBottom,
        itemCount = if (visibleFieldCount > 0) fieldCount else 0,
        visibleItemCount = visibleFieldCount,
        scrollIndex = scrollIndex,
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT,
    )

    /**
     * 判斷游標是否落在欄位捲軸的命中範圍內。命中範圍比繪製範圍稍寬，不判斷內容是否需要捲動。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @return 位於命中範圍內時為 true。
     */
    fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = mouseX >= scrollbarLeft - SCROLLBAR_HIT_PADDING &&
        mouseX < scrollbarLeft + VerticalScrollbar.WIDTH + SCROLLBAR_HIT_PADDING &&
        mouseY >= fieldsTop &&
        mouseY < fieldsBottom

    internal companion object {
        /** 視窗寬度低於此值時改用窄版。 */
        const val COMPACT_MIN_WIDTH: Int = 400

        /** 內容寬度上限。 */
        const val MAX_CONTENT_WIDTH: Int = 456

        /** 寬版內容的左右邊距。 */
        const val REGULAR_MARGIN: Int = 12

        /** 窄版內容的左右邊距。 */
        const val COMPACT_MARGIN: Int = 18

        /** 標題文字的 Y 座標。 */
        const val TITLE_Y: Int = 8

        /** 標題的文字顏色。 */
        const val TITLE_COLOR: Int = 0xFFD54F

        /** 沒有頁面切換按鈕時，標題下方第一個區塊的上界。 */
        const val TITLE_BOTTOM: Int = 26

        /** 頁面切換按鈕的上界。 */
        const val PAGE_SWITCH_Y: Int = 22

        /** 頁面切換按鈕的寬度。 */
        const val PAGE_SWITCH_WIDTH: Int = 100

        /** 兩顆頁面切換按鈕的間距。 */
        const val PAGE_SWITCH_GAP: Int = 4

        /** 按鈕與控制項的高度。 */
        const val BUTTON_HEIGHT: Int = 20

        /** 頁面切換、規則列與頁籤之間的垂直間距。 */
        const val SECTION_GAP: Int = 6

        /** 規則列分隔線與頁籤之間的垂直間距。 */
        const val RULE_DIVIDER_GAP: Int = 4

        /** 頁籤底線與第一個欄位之間的垂直間距。 */
        const val FIELDS_TOP_GAP: Int = 5

        /** 控制項寬度。 */
        const val CONTROL_WIDTH: Int = 150

        /** 寬版標籤與控制項之間至少保留的水平間距。 */
        const val LABEL_GAP: Int = 8

        /** 寬版相鄰兩個欄位上界之間的距離。 */
        const val REGULAR_FIELD_ROW_HEIGHT: Int = 24

        /** 窄版相鄰兩個欄位上界之間的距離。 */
        const val COMPACT_FIELD_ROW_HEIGHT: Int = 39

        /** 窄版標籤所佔的高度，含標籤與控制項之間的間距。 */
        const val COMPACT_LABEL_HEIGHT: Int = 12

        /** 垂直置中文字使用的可見字高。 */
        const val VISIBLE_TEXT_HEIGHT: Int = 8

        /** 數值欄位加減按鈕的寬度。 */
        const val INTEGER_BUTTON_WIDTH: Int = 20

        /** 數值欄位加減按鈕與輸入框的間距。 */
        const val INTEGER_GAP: Int = 4

        /** 底部按鈕列與視窗底部的距離。 */
        const val FOOTER_BOTTOM_MARGIN: Int = 8

        /** 狀態文字所佔的高度。 */
        const val STATUS_HEIGHT: Int = 12

        /** 欄位區與狀態文字之間的垂直間距。 */
        const val STATUS_GAP: Int = 3

        /** 內容右界與欄位捲軸之間的水平間距。 */
        const val SCROLLBAR_GAP: Int = 4

        /** 隔列底色左右超出內容範圍的寬度。必須小於 [SCROLLBAR_GAP]。 */
        const val STRIPE_INSET: Int = 3

        /** 欄位捲軸左右各加寬的命中範圍。 */
        const val SCROLLBAR_HIT_PADDING: Int = 3
    }
}

/**
 * 規則列的版面：規則選擇器、規則一覽按鈕與玩法選擇器。
 *
 * 寬版排成一列，規則靠左、玩法靠右。窄版分成兩欄，標籤在上、選擇器在下並佔滿欄寬。
 *
 * @property top 規則列上界。
 * @property contentLeft 內容左界。
 * @property contentWidth 內容寬度。
 * @property compact 是否使用窄版。
 * @property ruleLabelWidth 「規則」標籤的文字寬度。
 * @property variantLabelWidth 「玩法」標籤的文字寬度。
 */
internal data class SelectorRowLayout(
    val top: Int,
    val contentLeft: Int,
    val contentWidth: Int,
    val compact: Boolean,
    val ruleLabelWidth: Int,
    val variantLabelWidth: Int,
) {
    /** 窄版每一欄的寬度。 */
    private val columnWidth: Int = (contentWidth - COLUMN_GAP) / 2

    /** 兩個選擇器共用的寬度。寬版放不下時兩者一起縮窄。 */
    val selectorWidth: Int = if (compact) {
        columnWidth
    } else {
        val fixed = ruleLabelWidth + variantLabelWidth + LABEL_GAP * 2 + CATALOGUE_GAP + CATALOGUE_SIZE + MINIMUM_MIDDLE_GAP
        ((contentWidth - fixed) / 2).coerceIn(1, FormScreenLayout.CONTROL_WIDTH)
    }

    /** 兩個標籤文字的 Y 座標。 */
    val labelY: Int = if (compact) {
        top + (COMPACT_LABEL_ROW_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2
    } else {
        top + (FormScreenLayout.BUTTON_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2
    }

    /** 兩個選擇器的上界。 */
    val selectorY: Int = if (compact) top + COMPACT_LABEL_ROW_HEIGHT + COMPACT_LABEL_GAP else top

    /** 「規則」標籤的左界。 */
    val ruleLabelX: Int = contentLeft

    /** 規則選擇器的左界。 */
    val ruleSelectorX: Int = if (compact) contentLeft else contentLeft + ruleLabelWidth + LABEL_GAP

    /** 規則一覽按鈕的邊長。 */
    val catalogueSize: Int = if (compact) COMPACT_CATALOGUE_SIZE else CATALOGUE_SIZE

    /** 規則一覽按鈕的左界。 */
    val catalogueX: Int = if (compact) contentLeft + columnWidth - COMPACT_CATALOGUE_SIZE else ruleSelectorX + selectorWidth + CATALOGUE_GAP

    /** 規則一覽按鈕的上界。 */
    val catalogueY: Int = top

    /** 玩法選擇器的左界。 */
    val variantSelectorX: Int = contentLeft + contentWidth - selectorWidth

    /** 「玩法」標籤的左界。 */
    val variantLabelX: Int = if (compact) variantSelectorX else variantSelectorX - LABEL_GAP - variantLabelWidth

    /** 規則列下方分隔線的 Y 座標。 */
    val dividerY: Int = selectorY + FormScreenLayout.BUTTON_HEIGHT + DIVIDER_GAP

    internal companion object {
        /** 標籤與選擇器之間的水平間距。 */
        const val LABEL_GAP: Int = 6

        /** 規則選擇器與規則一覽按鈕之間的水平間距。 */
        const val CATALOGUE_GAP: Int = 4

        /** 寬版規則一覽按鈕的邊長。 */
        const val CATALOGUE_SIZE: Int = 20

        /** 窄版規則一覽按鈕的邊長。 */
        const val COMPACT_CATALOGUE_SIZE: Int = 14

        /** 寬版規則與玩法兩組之間至少保留的水平間距。 */
        const val MINIMUM_MIDDLE_GAP: Int = 12

        /** 窄版兩欄之間的水平間距。 */
        const val COLUMN_GAP: Int = 4

        /** 窄版標籤列的高度。 */
        const val COMPACT_LABEL_ROW_HEIGHT: Int = 14

        /** 窄版標籤列與選擇器之間的垂直間距。 */
        const val COMPACT_LABEL_GAP: Int = 3

        /** 選擇器與下方分隔線之間的垂直間距。 */
        const val DIVIDER_GAP: Int = 4
    }
}
