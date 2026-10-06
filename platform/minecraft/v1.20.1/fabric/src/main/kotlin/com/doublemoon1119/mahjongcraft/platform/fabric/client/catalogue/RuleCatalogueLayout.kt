package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
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
 * 寬畫面時規則、分類、搜尋與清除排成一列；寬度不足時規則與分類並排一列、搜尋與清除另一列。
 * 底部固定關閉或返回按鈕；從房間或歷史開啟時，設定來源切換按鈕放在它旁邊。
 *
 * @property width 畫面寬度。
 * @property height 畫面高度。
 * @property titleY 標題的上界。
 * @property singleRow 控制項是否排成一列。
 * @property rule 規則切換按鈕範圍。
 * @property category 分類切換按鈕範圍。
 * @property search 搜尋輸入框外框範圍。
 * @property clear 清除搜尋按鈕範圍。
 * @property sourceToggle 設定來源切換按鈕範圍；不顯示時為 null。
 * @property close 關閉或返回按鈕範圍。
 * @property contentBounds 可捲動內容區域。
 * @property scrollbarBounds 內容捲軸區域。
 */
internal data class RuleCatalogueScreenLayout(
    val width: Int,
    val height: Int,
    val titleY: Int,
    val singleRow: Boolean,
    val rule: CatalogueRect,
    val category: CatalogueRect,
    val search: CatalogueRect,
    val clear: CatalogueRect,
    val sourceToggle: CatalogueRect?,
    val close: CatalogueRect,
    val contentBounds: CatalogueRect,
    val scrollbarBounds: CatalogueRect,
) {
    /** 卡片寬度，與內容區同寬。 */
    val cardWidth: Int get() = contentBounds.width

    /**
     * 依內容總高度與目前像素偏移建立垂直捲軸。
     *
     * @param contentHeight 內容總高度，單位為像素。
     * @param scroll 目前內容偏移，單位為像素。
     * @return 以像素為單位的捲軸幾何。
     */
    fun scrollbar(contentHeight: Int, scroll: Double): ScrollbarLayout {
        val viewportHeight = contentBounds.height.coerceAtLeast(1)
        return ScrollbarLayout(
            trackTop = scrollbarBounds.y,
            trackBottom = scrollbarBounds.bottom,
            itemCount = contentHeight.coerceAtLeast(viewportHeight),
            visibleItemCount = viewportHeight,
            scrollIndex = clampScroll(scroll = scroll, contentHeight = contentHeight).toInt(),
            minimumThumbHeight = MINIMUM_THUMB_HEIGHT.coerceAtMost(viewportHeight),
        )
    }

    /**
     * 將內容偏移限制在可捲動範圍內。
     *
     * @param scroll 目前內容偏移，單位為像素。
     * @param contentHeight 內容總高度，單位為像素。
     * @return 限制後的內容偏移。
     */
    fun clampScroll(scroll: Double, contentHeight: Int): Double = scroll.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /**
     * 內容可捲動的最大像素偏移。
     *
     * @param contentHeight 內容總高度，單位為像素。
     * @return 內容未超出可見範圍時為 0。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - contentBounds.height).coerceAtLeast(0)

    /** 畫面幾何的建立入口與共用尺寸。 */
    internal companion object {
        /** 外側留白。 */
        const val MARGIN: Int = 8

        /** 元件之間的間距。 */
        const val GAP: Int = 4

        /** 按鈕與輸入框高度。 */
        const val ROW_HEIGHT: Int = 20

        /** 一行文字所佔高度。 */
        const val TEXT_HEIGHT: Int = 10

        /** 控制項排成一列所需的最小可用寬度。 */
        const val SINGLE_ROW_MIN_WIDTH: Int = 400

        /** 排成一列時規則與分類按鈕各自佔剩餘寬度的比例（十分之幾）。 */
        const val SELECTOR_SHARE_TENTHS: Int = 3

        /** 排成一列時規則與分類按鈕的最大寬度。 */
        const val MAX_SELECTOR_WIDTH: Int = 160

        /** 捲軸寬度。 */
        const val SCROLLBAR_WIDTH: Int = 6

        /** 捲軸滑塊最小高度。 */
        const val MINIMUM_THUMB_HEIGHT: Int = 10

        /** 單獨顯示時關閉按鈕的寬度。 */
        const val CLOSE_WIDTH: Int = 120

        /** 與設定來源切換按鈕並排時關閉按鈕的寬度。 */
        const val PAIRED_CLOSE_WIDTH: Int = 100

        /** 設定來源切換按鈕的最大寬度。 */
        const val SOURCE_TOGGLE_WIDTH: Int = 180

        /**
         * 建立畫面幾何；底部按鈕一律留在畫面內，空間不足時先縮小內容區。
         *
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @param clearWidth 清除搜尋按鈕依文字算出的寬度。
         * @param sourceToggle 是否顯示設定來源切換按鈕。
         * @return 計算後的畫面幾何。
         */
        fun measure(
            width: Int,
            height: Int,
            clearWidth: Int,
            sourceToggle: Boolean,
        ): RuleCatalogueScreenLayout {
            val safeWidth = width.coerceAtLeast(1)
            val safeHeight = height.coerceAtLeast(1)
            val available = (safeWidth - MARGIN * 2).coerceAtLeast(1)
            val titleY = MARGIN
            val controlsTop = titleY + TEXT_HEIGHT + GAP + 2
            val singleRow = available >= SINGLE_ROW_MIN_WIDTH
            val clear = clearWidth.coerceIn(ROW_HEIGHT, (available / 4).coerceAtLeast(ROW_HEIGHT))
            val controls = if (singleRow) {
                val rest = available - clear - GAP * 3
                val selector = (rest * SELECTOR_SHARE_TENTHS / 10).coerceAtMost(MAX_SELECTOR_WIDTH)
                val rule = CatalogueRect(x = MARGIN, y = controlsTop, width = selector, height = ROW_HEIGHT)
                val category = CatalogueRect(x = rule.right + GAP, y = controlsTop, width = selector, height = ROW_HEIGHT)
                val search = CatalogueRect(x = category.right + GAP, y = controlsTop, width = rest - selector * 2, height = ROW_HEIGHT)
                listOf(rule, category, search, CatalogueRect(x = search.right + GAP, y = controlsTop, width = clear, height = ROW_HEIGHT))
            } else {
                val half = (available - GAP) / 2
                val searchTop = controlsTop + ROW_HEIGHT + GAP
                val search = CatalogueRect(x = MARGIN, y = searchTop, width = (available - GAP - clear).coerceAtLeast(1), height = ROW_HEIGHT)
                listOf(
                    CatalogueRect(x = MARGIN, y = controlsTop, width = half, height = ROW_HEIGHT),
                    CatalogueRect(x = MARGIN + half + GAP, y = controlsTop, width = available - half - GAP, height = ROW_HEIGHT),
                    search,
                    CatalogueRect(x = search.right + GAP, y = searchTop, width = clear, height = ROW_HEIGHT),
                )
            }
            val footerTop = (safeHeight - MARGIN - ROW_HEIGHT).coerceAtLeast(0)
            val (toggle, close) = footer(
                width = safeWidth,
                available = available,
                top = footerTop,
                sourceToggle = sourceToggle,
            )
            val contentTop = controls.maxOf { it.bottom } + GAP
            val contentBottom = (footerTop - GAP).coerceAtLeast(contentTop)
            val contentBounds = CatalogueRect(
                x = MARGIN,
                y = contentTop,
                width = (available - SCROLLBAR_WIDTH - GAP).coerceAtLeast(1),
                height = contentBottom - contentTop,
            )
            return RuleCatalogueScreenLayout(
                width = safeWidth,
                height = safeHeight,
                titleY = titleY,
                singleRow = singleRow,
                rule = controls[0],
                category = controls[1],
                search = controls[2],
                clear = controls[3],
                sourceToggle = toggle,
                close = close,
                contentBounds = contentBounds,
                scrollbarBounds = CatalogueRect(
                    x = safeWidth - MARGIN - SCROLLBAR_WIDTH,
                    y = contentTop,
                    width = SCROLLBAR_WIDTH,
                    height = contentBounds.height,
                ),
            )
        }

        /**
         * 置中排列底部按鈕。
         *
         * @param width 畫面寬度。
         * @param available 扣除外側留白的可用寬度。
         * @param top 按鈕上界。
         * @param sourceToggle 是否在關閉按鈕左側放設定來源切換按鈕。
         * @return 設定來源切換按鈕（不顯示時為 null）與關閉按鈕的範圍。
         */
        private fun footer(
            width: Int,
            available: Int,
            top: Int,
            sourceToggle: Boolean,
        ): Pair<CatalogueRect?, CatalogueRect> {
            if (!sourceToggle) {
                val closeWidth = CLOSE_WIDTH.coerceAtMost(available)
                return null to CatalogueRect(x = (width - closeWidth) / 2, y = top, width = closeWidth, height = ROW_HEIGHT)
            }
            val closeWidth = PAIRED_CLOSE_WIDTH.coerceAtMost((available - GAP) / 3)
            val toggleWidth = SOURCE_TOGGLE_WIDTH.coerceAtMost(available - GAP - closeWidth)
            val left = (width - toggleWidth - GAP - closeWidth) / 2
            val toggle = CatalogueRect(x = left, y = top, width = toggleWidth, height = ROW_HEIGHT)
            return toggle to CatalogueRect(x = toggle.right + GAP, y = top, width = closeWidth, height = ROW_HEIGHT)
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
    val tile: Tile,
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
        private val rowTiles = mutableListOf<Pair<Tile, Int>>()

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
