package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabStripLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證規則一覽的畫面幾何與範例牌組排列。 */
class RuleCatalogueLayoutTest {
    /** 最小畫面（320×240）下規則選擇器獨佔一列，搜尋與清除在下一列。各區域不重疊且留在畫面內。 */
    @Test
    fun `smallest screen gives the rule selector its own row`() {
        listOf<Int?>(null, 60).forEach { sourceLabelWidth ->
            val layout = measure(width = 320, height = 240, sourceLabelWidth = sourceLabelWidth)

            assertFalse(layout.singleRow)
            assertEquals(layout.contentLeft, layout.rule.x)
            assertEquals(layout.contentWidth, layout.rule.width)
            assertEquals(layout.search.y, layout.clear.y)
            assertTrue(layout.rule.bottom <= layout.search.y)
            assertAreasApart(layout)
            assertTrue(layout.contentBounds.height >= MINIMUM_VISIBLE_CONTENT)
        }
    }

    /** 寬畫面時規則選擇器（寬 150）、搜尋與清除排成一列，由左到右不重疊並填滿內容寬度。 */
    @Test
    fun `wide screens put the rule selector search and clear on one row`() {
        listOf(427, 960).forEach { width ->
            val layout = measure(width = width, height = 240)
            val controls = listOf(layout.rule, layout.search, layout.clear)

            assertTrue(layout.singleRow)
            assertEquals(1, controls.map { it.y }.distinct().size)
            controls.zipWithNext().forEach { (left, right) -> assertEquals(left.right + RuleCatalogueScreenLayout.GAP, right.x) }
            assertEquals(150, layout.rule.width)
            assertEquals(layout.contentLeft, layout.rule.x)
            assertEquals(layout.contentLeft + layout.contentWidth, layout.clear.right)
            assertAreasApart(layout)
        }
    }

    /** 內容寬度最多 456 並水平置中，和設定類畫面一致。 */
    @Test
    fun `content width is capped and centred`() {
        val layout = measure(width = 960, height = 540)

        assertEquals(456, layout.contentWidth)
        assertEquals((960 - 456) / 2, layout.contentLeft)
        assertEquals(layout.contentWidth, layout.contentBounds.width)
        assertEquals(layout.contentWidth, layout.tabStrip(tabCount = 12, scroll = 0).width)
    }

    /** 分類頁籤在控制項下方，內容區從頁籤底線下方開始。 */
    @Test
    fun `category tabs sit between the controls and the content`() {
        val layout = measure(width = 480, height = 270)
        val strip = layout.tabStrip(tabCount = 12, scroll = 0)

        assertTrue(strip.top >= layout.search.bottom)
        assertTrue(strip.lineY < layout.contentBounds.y)
        assertTrue(strip.maximumScroll > 0)
    }

    /** 返回或關閉固定在底部最右一格。設定來源選擇器在標籤右側，不與返回按鈕重疊。 */
    @Test
    fun `footer keeps close in the last slot and the source selector after its label`() {
        val general = measure(width = 480, height = 270)
        val withSource = measure(width = 480, height = 270, sourceLabelWidth = 60)
        val selector = checkNotNull(withSource.sourceSelector)

        assertEquals(null, general.sourceSelector)
        assertEquals(general.close, withSource.close)
        assertEquals(general.contentLeft + general.contentWidth, general.close.right)
        assertEquals(withSource.contentLeft + 60 + RuleCatalogueScreenLayout.LABEL_GAP, selector.x)
        assertEquals(150, selector.width)
        assertEquals(withSource.close.y, selector.y)
        assertTrue(selector.right + RuleCatalogueScreenLayout.GAP <= withSource.close.x)
    }

    /** 長標籤優先隱藏，窄版將來源選擇器放到返回按鈕上方。 */
    @Test
    fun `test source selector stays clear of close as the viewport narrows`() {
        val inline = measure(width = 320, height = 240, sourceLabelWidth = 180)
        val inlineSelector = checkNotNull(inline.sourceSelector)
        assertEquals(null, inline.sourceLabelY)
        assertEquals(inline.close.y, inlineSelector.y)
        assertTrue(inlineSelector.right + RuleCatalogueScreenLayout.GAP <= inline.close.x)

        val stacked = measure(width = 160, height = 240, sourceLabelWidth = 180)
        val stackedSelector = checkNotNull(stacked.sourceSelector)
        assertEquals(null, stacked.sourceLabelY)
        assertTrue(stackedSelector.bottom + RuleCatalogueScreenLayout.GAP <= stacked.close.y)
        assertTrue(stackedSelector.right <= stacked.width)
        assertTrue(stacked.contentBounds.bottom <= stackedSelector.y)
    }

    /** 清除按鈕依文字寬度變寬，但至少與按鈕同高。 */
    @Test
    fun `clear button follows its text width`() {
        assertEquals(RuleCatalogueScreenLayout.ROW_HEIGHT, measure(width = 320, height = 240, clearWidth = 4).clear.width)
        assertEquals(44, measure(width = 320, height = 240, clearWidth = 44).clear.width)
    }

    /** 畫面建立前的 1×1 佔位尺寸也能算出版面，不會因按鈕列太窄而拋出例外。 */
    @Test
    fun `placeholder size before the first init still measures`() {
        listOf<Int?>(null, 60).forEach { sourceLabelWidth ->
            val layout = measure(width = 1, height = 1, clearWidth = 0, sourceLabelWidth = sourceLabelWidth)

            assertTrue(layout.contentBounds.height >= 0)
        }
    }

    /** 畫面太矮時底部按鈕仍留在畫面內，只縮小內容區。 */
    @Test
    fun `very short screens keep the footer on screen`() {
        val layout = measure(width = 320, height = 120, sourceLabelWidth = 60)

        assertTrue(layout.close.bottom <= layout.height)
        assertTrue(layout.contentBounds.height >= 0)
    }

    /** 像素偏移限制在內容範圍內，捲軸反映可見高度並保留抓取位置。 */
    @Test
    fun `scrollbar clamps pixel offsets and preserves grab geometry`() {
        val layout = measure(width = 320, height = 240)
        val maximum = (400 - layout.contentBounds.height).toDouble()

        assertEquals(0.0, layout.clampScroll(scroll = -20.0, contentHeight = 20))
        assertEquals(0.0, layout.clampScroll(scroll = 20.0, contentHeight = layout.contentBounds.height))
        assertEquals(maximum, layout.clampScroll(scroll = 999.0, contentHeight = 400))
        val scrollbar = layout.scrollbar(contentHeight = 400, scroll = maximum)
        val grab = scrollbar.grabOffset(scrollbar.thumbTop + 2.0)
        assertEquals(2.0, grab)
        assertEquals(scrollbar.maximumScroll, scrollbar.scrollIndexFor(scrollbar.trackBottom.toDouble(), grab))
    }

    /** 放得下的牌組在同一列由左往右排列，相鄰同角色牌組共用一個標題。 */
    @Test
    fun `groups flow left to right and adjacent same roles share a heading`() {
        val result = RuleCatalogueTileLayout.measure(
            groups = listOf(
                group(RuleCatalogueTileGroupRole.HAND, 3),
                group(RuleCatalogueTileGroupRole.HAND, 3),
                group(RuleCatalogueTileGroupRole.WINNING_TILE, 1),
            ),
            width = 400,
            headingKey = ::groupHeadingKey,
            headingWidth = ::headingWidth,
        )

        assertEquals(listOf(roleKey(RuleCatalogueTileGroupRole.HAND), roleKey(RuleCatalogueTileGroupRole.WINNING_TILE)), result.headings.map { it.key })
        assertEquals(1, result.placements.map { it.y }.distinct().size)
        assertEquals(RuleCatalogueTileLayout.HEADING_HEIGHT + RuleCatalogueTileLayout.TILE_HEIGHT, result.height)
        val handGap = result.placements[3].x - (result.placements[2].x + RuleCatalogueTileLayout.TILE_WIDTH)
        val sectionGap = result.placements[6].x - (result.placements[5].x + RuleCatalogueTileLayout.TILE_WIDTH)
        assertEquals(RuleCatalogueTileLayout.GROUP_GAP, handGap)
        assertEquals(RuleCatalogueTileLayout.SECTION_GAP, sectionGap)
        assertEquals(result.placements[6].x, result.headings[1].x)
    }

    /** 放不下的牌組整組移到下一列，不會把一組拆成兩半。 */
    @Test
    fun `groups that do not fit move to the next row whole`() {
        val groups = listOf(
            group(RuleCatalogueTileGroupRole.OPEN_KAN, 4),
            group(RuleCatalogueTileGroupRole.CLOSED_KAN, 4),
            group(RuleCatalogueTileGroupRole.OPEN_KAN, 4),
            group(RuleCatalogueTileGroupRole.CLOSED_KAN, 4),
            group(RuleCatalogueTileGroupRole.HAND, 2),
        )
        val width = 180
        val result = RuleCatalogueTileLayout.measure(groups = groups, width = width, headingKey = ::groupHeadingKey, headingWidth = ::headingWidth)

        assertEquals(18, result.placements.size)
        assertEquals(groups.size, result.headings.size)
        var index = 0
        groups.forEach { group ->
            val rows = result.placements.subList(index, index + group.tiles.size).map { it.y }.distinct()
            assertEquals(1, rows.size, "a group must stay on one row")
            index += group.tiles.size
        }
        result.placements.forEach { assertTrue(it.x >= 0 && it.x + it.width <= width) }
        assertTrue(result.placements.map { it.y }.distinct().size < groups.size, "groups that fit must share rows")
    }

    /** 單組比可用寬度還寬時才在組內換列，牌面順序不變且不超出寬度。 */
    @Test
    fun `oversized groups wrap inside the group and keep tile order`() {
        val tiles = (1..9).map { TileFaceView(Tile.Numeric(Tile.Suit.Character, it)) }
        val width = 100
        val result = RuleCatalogueTileLayout.measure(
            groups = listOf(
                RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.HAND, tiles),
                RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(tiles.last())),
            ),
            width = width,
            headingKey = ::groupHeadingKey,
            headingWidth = ::headingWidth,
        )

        assertEquals(tiles + tiles.last(), result.placements.map { it.tile })
        assertTrue(result.placements.all { it.x >= 0 && it.x + it.width <= width })
        assertTrue(result.placements.zipWithNext().all { (first, second) -> first.y < second.y || first.x < second.x })
        assertEquals(2, result.headings.size)
        assertTrue(result.placements.take(tiles.size).map { it.y }.distinct().size > 1)
    }

    /** 標題比牌組寬時保留標題寬度，下一段不會疊到上一段的標題。 */
    @Test
    fun `wide headings reserve their width`() {
        val result = RuleCatalogueTileLayout.measure(
            groups = listOf(group(RuleCatalogueTileGroupRole.WINNING_TILE, 1), group(RuleCatalogueTileGroupRole.HAND, 2)),
            width = 400,
            headingKey = ::groupHeadingKey,
            headingWidth = { 60 },
        )

        assertTrue(result.headings[1].x >= result.headings[0].x + 60)
        assertEquals(result.headings[1].x, result.placements[1].x)
    }

    /**
     * 確認控制項、內容區、捲軸與底部按鈕互不重疊且都在畫面內。
     *
     * @param layout 受測畫面幾何。
     */
    private fun assertAreasApart(layout: RuleCatalogueScreenLayout) {
        val controlsBottom = listOf(layout.rule, layout.search, layout.clear).maxOf { it.bottom }
        assertTrue(controlsBottom <= layout.tabsTop)
        assertTrue(layout.tabsTop + TabStripLayout.SELECTED_HEIGHT < layout.contentBounds.y)
        assertTrue(layout.contentBounds.bottom <= layout.close.y)
        assertTrue(layout.close.bottom <= layout.height)
        assertTrue(layout.scrollbarBounds.x >= layout.contentBounds.right)
        assertTrue(layout.scrollbarBounds.right <= layout.width)
        listOfNotNull(layout.rule, layout.search, layout.clear, layout.close, layout.sourceSelector).forEach { assertTrue(it.right <= layout.width) }
    }

    /**
     * 以測試用尺寸建立畫面幾何。
     *
     * @param width 畫面寬度。
     * @param height 畫面高度。
     * @param clearWidth 清除按鈕寬度。
     * @param sourceLabelWidth 設定來源標籤寬度。不顯示設定來源時為 null。
     * @return 畫面幾何。
     */
    private fun measure(
        width: Int,
        height: Int,
        clearWidth: Int = 36,
        sourceLabelWidth: Int? = null,
    ): RuleCatalogueScreenLayout = RuleCatalogueScreenLayout.measure(
        width = width,
        height = height,
        clearWidth = clearWidth,
        sourceLabelWidth = sourceLabelWidth,
    )

    /**
     * 建立指定張數的測試牌組。
     *
     * @param role 牌組角色。
     * @param count 張數。
     * @return 測試牌組。
     */
    private fun group(role: RuleCatalogueTileGroupRole, count: Int): RuleCatalogueTileGroup = RuleCatalogueTileGroup(
        role = role,
        tiles = List(count) { TileFaceView(Tile.Numeric(Tile.Suit.Character, it % 9 + 1)) },
    )

    /** 相鄰牌組標籤相同時共用一個標題，自訂標籤不同時各有標題，即使角色相同。 */
    @Test
    fun `custom labels decide which groups share a heading`() {
        val tiles = listOf(TileFaceView(Tile.Numeric(Tile.Suit.Character, 3)))
        val result = RuleCatalogueTileLayout.measure(
            groups = listOf(
                RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, tiles, labelTranslationKey = "test.called"),
                RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, tiles, labelTranslationKey = "test.forbidden"),
                RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.ILLUSTRATION, tiles, labelTranslationKey = "test.forbidden"),
            ),
            width = 400,
            headingKey = ::groupHeadingKey,
            headingWidth = ::headingWidth,
        )

        assertEquals(listOf("test.called", "test.forbidden"), result.headings.map { it.key })
    }

    /**
     * 測試用標題寬度，短於任何一張牌。
     *
     * @param key 標題翻譯鍵。
     * @return 標題寬度。
     */
    private fun headingWidth(key: String): Int = 12

    /** 測試門檻。 */
    private companion object {
        /** 最小畫面上內容區至少要能顯示的高度。 */
        const val MINIMUM_VISIBLE_CONTENT = 100
    }
}
