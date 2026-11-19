package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ButtonRowLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.PixelScrollViewport
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.TabStripLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole

/**
 * 目錄畫面使用的矩形邊界。
 *
 * @property x 左界。
 * @property y 上界。
 * @property width 寬度。
 * @property height 高度。
 */
internal data class CatalogueRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    /** 右界（不含）。 */
    val right: Int get() = x + width

    /** 下界（不含）。 */
    val bottom: Int get() = y + height

    /**
     * 判斷像素座標是否位於矩形內。
     *
     * @param mouseX 欲檢查的水平座標。
     * @param mouseY 欲檢查的垂直座標。
     * @return 座標位於矩形內時為 true。
     */
    fun contains(mouseX: Double, mouseY: Double): Boolean = mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom

    /**
     * 判斷矩形是否與垂直範圍相交。
     *
     * @param top 範圍上界。
     * @param bottom 範圍下界（不含）。
     * @return 矩形與範圍相交時為 true。
     */
    fun intersects(top: Int, bottom: Int): Boolean = y < bottom && this.bottom > top
}

/**
 * 規則一覽畫面的固定區域與可捲動內容區域。
 *
 * 內容寬度與設定類畫面相同，最多 [FormScreenLayout.MAX_CONTENT_WIDTH] 並水平置中。標題下方是規則選擇器、搜尋與
 * 清除，寬度不足時規則選擇器獨佔一列；其下是分類頁籤；底部是等寬按鈕列，返回或關閉固定在最右一格。從房間或歷史
 * 開啟時，底部左側另有「說明依據」標籤與設定來源選擇器。
 *
 * @property width 畫面寬度。
 * @property height 畫面高度。
 * @property singleRow 規則選擇器、搜尋與清除是否排成一列。
 * @property contentLeft 內容左界。
 * @property contentWidth 內容寬度。
 * @property rule 規則選擇器範圍。
 * @property search 搜尋輸入框外框範圍。
 * @property clear 清除搜尋按鈕範圍。
 * @property tabsTop 分類頁籤列上界。
 * @property sourceLabelY 設定來源標籤文字的 Y 座標；隱藏標籤時為 null。
 * @property sourceSelector 設定來源選擇器範圍；不顯示時為 null。
 * @property close 關閉或返回按鈕範圍。
 * @property contentBounds 可捲動內容區域。
 * @property scrollbarBounds 內容捲軸區域。
 */
internal data class RuleCatalogueScreenLayout(
    val width: Int,
    val height: Int,
    val singleRow: Boolean,
    val contentLeft: Int,
    val contentWidth: Int,
    val rule: CatalogueRect,
    val search: CatalogueRect,
    val clear: CatalogueRect,
    val tabsTop: Int,
    val sourceLabelY: Int?,
    val sourceSelector: CatalogueRect?,
    val close: CatalogueRect,
    val contentBounds: CatalogueRect,
    val scrollbarBounds: CatalogueRect,
) : PixelScrollViewport {
    /** 卡片寬度，與內容區同寬。 */
    val cardWidth: Int get() = contentBounds.width

    /**
     * 依目前捲動位置建立分類頁籤列版面。
     *
     * @param tabCount 頁籤數量。
     * @param scroll 頁籤列向左捲動的像素數。
     * @return 頁籤列版面。
     */
    fun tabStrip(tabCount: Int, scroll: Int): TabStripLayout = TabStripLayout(
        left = contentLeft,
        width = contentWidth,
        top = tabsTop,
        tabCount = tabCount,
        scroll = scroll,
    )

    /**
     * 依內容總高度與目前像素偏移建立垂直捲軸。
     *
     * @param contentHeight 內容總高度，單位為像素。
     * @param scroll 目前內容偏移，單位為像素。
     * @return 以像素為單位的捲軸幾何。
     */
    override fun scrollbar(contentHeight: Int, scroll: Double): ScrollbarLayout {
        val viewportHeight = contentBounds.height.coerceAtLeast(1)
        return ScrollbarLayout(
            trackTop = scrollbarBounds.y,
            trackBottom = scrollbarBounds.bottom,
            itemCount = contentHeight.coerceAtLeast(viewportHeight),
            visibleItemCount = viewportHeight,
            scrollIndex = clampScroll(scroll = scroll, contentHeight = contentHeight).toInt(),
            minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT.coerceAtMost(viewportHeight),
        )
    }

    /**
     * 將內容偏移限制在可捲動範圍內。
     *
     * @param scroll 目前內容偏移，單位為像素。
     * @param contentHeight 內容總高度，單位為像素。
     * @return 限制後的內容偏移。
     */
    override fun clampScroll(scroll: Double, contentHeight: Int): Double = scroll.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /**
     * 內容可捲動的最大像素偏移。
     *
     * @param contentHeight 內容總高度，單位為像素。
     * @return 內容未超出可見範圍時為 0。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - contentBounds.height).coerceAtLeast(0)

    /** 畫面幾何的建立入口與共用尺寸。 */
    internal companion object {
        /** 元件之間的間距。 */
        const val GAP: Int = 4

        /** 按鈕與輸入框高度。 */
        const val ROW_HEIGHT: Int = 20

        /** 規則選擇器與設定來源選擇器的寬度上限。 */
        const val SELECTOR_WIDTH: Int = FormScreenLayout.CONTROL_WIDTH

        /** 搜尋框與規則選擇器同列時至少保留的寬度。 */
        const val MIN_SEARCH_WIDTH: Int = 100

        /** 頁籤列與上方控制項之間的間距。 */
        const val TABS_GAP: Int = 6

        /** 頁籤底線與內容區之間的間距。 */
        const val CONTENT_GAP: Int = 5

        /** 設定來源標籤與選擇器之間的間距。 */
        const val LABEL_GAP: Int = 6

        /** 內容區與捲軸保留範圍之間的間距。 */
        const val SCROLLBAR_GAP: Int = 1

        /** 捲軸保留的寬度；捲軸畫在其中央，命中範圍比繪製範圍寬。 */
        const val SCROLLBAR_AREA_WIDTH: Int = 9

        /** 底部按鈕列的按鈕數；返回或關閉固定在最後一格。 */
        const val FOOTER_SLOTS: Int = 4

        /**
         * 建立畫面幾何；底部按鈕一律留在畫面內，空間不足時先縮小內容區。
         *
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @param clearWidth 清除搜尋按鈕依文字算出的寬度。
         * @param sourceLabelWidth 設定來源標籤的文字寬度；不顯示設定來源時為 null。
         * @return 計算後的畫面幾何。
         */
        fun measure(
            width: Int,
            height: Int,
            clearWidth: Int,
            sourceLabelWidth: Int?,
        ): RuleCatalogueScreenLayout {
            val safeWidth = width.coerceAtLeast(1)
            val safeHeight = height.coerceAtLeast(1)
            val frame = FormScreenLayout(windowWidth = safeWidth, windowHeight = safeHeight, hasPageSwitch = false, hasRuleRow = false)
            val left = frame.contentLeft
            val available = frame.contentWidth
            val controlsTop = FormScreenLayout.TITLE_BOTTOM
            val clear = clearWidth.coerceIn(ROW_HEIGHT, (available / 4).coerceAtLeast(ROW_HEIGHT))
            val ruleWidth = SELECTOR_WIDTH.coerceAtMost(available)
            val singleRow = available >= ruleWidth + GAP + MIN_SEARCH_WIDTH + GAP + clear
            val rule = CatalogueRect(x = left, y = controlsTop, width = if (singleRow) ruleWidth else available, height = ROW_HEIGHT)
            val searchTop = if (singleRow) controlsTop else rule.bottom + GAP
            val searchLeft = if (singleRow) rule.right + GAP else left
            val clearRect = CatalogueRect(x = left + available - clear, y = searchTop, width = clear, height = ROW_HEIGHT)
            val search = CatalogueRect(x = searchLeft, y = searchTop, width = (clearRect.x - GAP - searchLeft).coerceAtLeast(1), height = ROW_HEIGHT)
            val tabsTop = search.bottom + TABS_GAP
            val contentTop = tabsTop + TabStripLayout.SELECTED_HEIGHT + 1 + CONTENT_GAP
            val footerTop = (safeHeight - FormScreenLayout.FOOTER_BOTTOM_MARGIN - ROW_HEIGHT).coerceAtLeast(0)
            val footer = ButtonRowLayout(left = left, width = available.coerceAtLeast(ButtonRowLayout.minimumWidth(FOOTER_SLOTS)), buttonCount = FOOTER_SLOTS)
            val closeIndex = FOOTER_SLOTS - 1
            val close = CatalogueRect(x = footer.buttonX(closeIndex), y = footerTop, width = footer.buttonWidth(closeIndex), height = ROW_HEIGHT)
            val sourceSpace = close.x - GAP - left
            val sourceWidth = SELECTOR_WIDTH.coerceAtMost(available)
            val sourceAboveFooter = sourceLabelWidth != null && sourceSpace < sourceWidth && footerTop >= ROW_HEIGHT + GAP
            val sourceTop = if (sourceAboveFooter) footerTop - ROW_HEIGHT - GAP else footerTop
            val showSourceLabel = sourceLabelWidth != null && sourceLabelWidth + LABEL_GAP + sourceWidth <= sourceSpace
            val sourceSelector = sourceLabelWidth?.let { labelWidth ->
                val selectorLeft = if (showSourceLabel) left + labelWidth + LABEL_GAP else left
                val selectorSpace = if (sourceAboveFooter) available else close.x - GAP - selectorLeft
                CatalogueRect(x = selectorLeft, y = sourceTop, width = sourceWidth.coerceAtMost(selectorSpace.coerceAtLeast(1)), height = ROW_HEIGHT)
            }
            val contentBottom = (sourceTop - GAP).coerceAtLeast(contentTop)
            val contentBounds = CatalogueRect(x = left, y = contentTop, width = available, height = contentBottom - contentTop)
            return RuleCatalogueScreenLayout(
                width = safeWidth,
                height = safeHeight,
                singleRow = singleRow,
                contentLeft = left,
                contentWidth = available,
                rule = rule,
                search = search,
                clear = clearRect,
                tabsTop = tabsTop,
                sourceLabelY = (footerTop + (ROW_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2).takeIf { showSourceLabel },
                sourceSelector = sourceSelector,
                close = close,
                contentBounds = contentBounds,
                scrollbarBounds = CatalogueRect(
                    x = contentBounds.right + SCROLLBAR_GAP,
                    y = contentTop,
                    width = SCROLLBAR_AREA_WIDTH,
                    height = contentBounds.height,
                ),
            )
        }
    }
}

/**
 * 單張範例牌的位置，座標相對於牌例區左上角。
 *
 * @property tile 牌面。
 * @property x 左界。
 * @property y 上界。
 * @property width 寬度。
 * @property height 高度。
 */
internal data class RuleCatalogueTilePlacement(
    val tile: TileFaceView,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * 一段同角色牌組的標題位置，座標相對於牌例區左上角。
 *
 * @property role 牌組角色。
 * @property x 左界。
 * @property y 上界。
 * @property maxWidth 標題可用寬度。
 */
internal data class RuleCatalogueTileHeading(
    val role: RuleCatalogueTileGroupRole,
    val x: Int,
    val y: Int,
    val maxWidth: Int,
)

/**
 * 牌例區的測量結果，繪製與命中測試共用。
 *
 * @property height 牌例區所需高度。
 * @property placements 依牌組與牌面原順序排列的牌面位置。
 * @property headings 依出現順序排列的牌組標題位置。
 */
internal data class RuleCatalogueTileLayoutResult(
    val height: Int,
    val placements: List<RuleCatalogueTilePlacement>,
    val headings: List<RuleCatalogueTileHeading>,
)

/**
 * 將範例牌組依序由左往右排列：放不下的牌組整組移到下一列，只有單組本身超過可用寬度時才在組內換列。
 *
 * 相鄰且角色相同的牌組共用一個標題，組與組之間仍保留間距；角色不同的牌組各有標題。
 */
internal object RuleCatalogueTileLayout {
    /** 牌面寬度。 */
    const val TILE_WIDTH: Int = 18

    /** 牌面高度。 */
    const val TILE_HEIGHT: Int = 24

    /** 同組相鄰牌面的間距。 */
    const val TILE_GAP: Int = 1

    /** 同角色相鄰牌組的間距。 */
    const val GROUP_GAP: Int = 5

    /** 不同角色牌組的間距。 */
    const val SECTION_GAP: Int = 10

    /** 牌組標題所佔高度。 */
    const val HEADING_HEIGHT: Int = 11

    /** 列與列之間的間距。 */
    const val ROW_GAP: Int = 4

    /**
     * 測量牌組並建立牌面與標題位置。
     *
     * @param groups 依顯示順序排列的牌組。
     * @param width 牌例區可用寬度。
     * @param headingWidth 取得某角色標題文字的實際寬度。
     * @return 牌面位置、標題位置與所需高度。
     */
    fun measure(
        groups: List<RuleCatalogueTileGroup>,
        width: Int,
        headingWidth: (RuleCatalogueTileGroupRole) -> Int,
    ): RuleCatalogueTileLayoutResult {
        val builder = RowBuilder(maxWidth = width.coerceAtLeast(TILE_WIDTH))
        groups.forEachIndexed { index, group ->
            val startsSection = index == 0 || groups[index - 1].role != group.role
            val tilesWidth = group.tiles.size * TILE_WIDTH + (group.tiles.size - 1) * TILE_GAP
            val heading = if (startsSection) headingWidth(group.role).coerceAtLeast(0) else 0
            builder.place(
                group = group,
                reservedWidth = maxOf(tilesWidth, heading),
                leadingGap = if (startsSection) SECTION_GAP else GROUP_GAP,
                startsSection = startsSection,
            )
        }
        return builder.finish()
    }

    /**
     * 逐列累積牌面；一列結束時才決定該列是否需要保留標題高度。
     *
     * @property maxWidth 牌例區可用寬度。
     */
    private class RowBuilder(private val maxWidth: Int) {
        /** 已完成各列的牌面。 */
        private val placements = mutableListOf<RuleCatalogueTilePlacement>()

        /** 已完成各列的標題。 */
        private val headings = mutableListOf<RuleCatalogueTileHeading>()

        /** 目前列的牌面水平位置。 */
        private val rowTiles = mutableListOf<Pair<TileFaceView, Int>>()

        /** 目前列的標題水平位置與可用寬度。 */
        private val rowHeadings = mutableListOf<Triple<RuleCatalogueTileGroupRole, Int, Int>>()

        /** 目前列的上界。 */
        private var rowTop = 0

        /** 目前列已使用的寬度。 */
        private var x = 0

        /**
         * 放入一個牌組。
         *
         * @param group 牌組。
         * @param reservedWidth 牌組連同標題需要的寬度。
         * @param leadingGap 與同列前一組之間的間距。
         * @param startsSection 是否為一段同角色牌組的第一組。
         */
        fun place(
            group: RuleCatalogueTileGroup,
            reservedWidth: Int,
            leadingGap: Int,
            startsSection: Boolean,
        ) {
            if (x > 0 && x + leadingGap + reservedWidth > maxWidth) closeRow()
            val left = if (x == 0) 0 else x + leadingGap
            if (startsSection) rowHeadings += Triple(group.role, left, (maxWidth - left).coerceAtLeast(0))
            if (left + reservedWidth <= maxWidth) {
                group.tiles.forEachIndexed { index, tile -> rowTiles += tile to left + index * (TILE_WIDTH + TILE_GAP) }
                x = left + reservedWidth
                return
            }
            var tileX = left
            group.tiles.forEach { tile ->
                if (tileX > 0 && tileX + TILE_WIDTH > maxWidth) {
                    closeRow()
                    tileX = 0
                }
                rowTiles += tile to tileX
                tileX += TILE_WIDTH + TILE_GAP
            }
            x = tileX - TILE_GAP
        }

        /** 封存目前列並開始下一列。 */
        fun closeRow() {
            if (rowTiles.isEmpty() && rowHeadings.isEmpty()) return
            val headingHeight = if (rowHeadings.isEmpty()) 0 else HEADING_HEIGHT
            rowHeadings.forEach { (role, left, width) -> headings += RuleCatalogueTileHeading(role, left, rowTop, width) }
            rowTiles.forEach { (tile, left) ->
                placements += RuleCatalogueTilePlacement(
                    tile = tile,
                    x = left,
                    y = rowTop + headingHeight,
                    width = TILE_WIDTH,
                    height = TILE_HEIGHT,
                )
            }
            rowTop += headingHeight + TILE_HEIGHT + ROW_GAP
            rowTiles.clear()
            rowHeadings.clear()
            x = 0
        }

        /**
         * 封存最後一列並回傳結果。
         *
         * @return 完整的牌例區測量結果。
         */
        fun finish(): RuleCatalogueTileLayoutResult {
            closeRow()
            return RuleCatalogueTileLayoutResult(
                height = (rowTop - ROW_GAP).coerceAtLeast(0),
                placements = placements.toList(),
                headings = headings.toList(),
            )
        }
    }
}
