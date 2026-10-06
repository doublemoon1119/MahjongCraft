package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證規則一覽的畫面幾何與範例牌組排列。 */
class RuleCatalogueLayoutTest {
    /** 最小畫面（320×240）下控制項排成兩列，控制項、內容區與底部按鈕都不重疊且留在畫面內，內容區比原本三列排法更高。 */
    @Test
    fun `smallest screen uses two rows and keeps every area apart`() {
        listOf(false, true).forEach { sourceToggle ->
            val layout = measure(width = 320, height = 240, sourceToggle = sourceToggle)

            assertFalse(layout.singleRow)
            assertEquals(layout.rule.y, layout.category.y)
            assertEquals(layout.search.y, layout.clear.y)
            assertTrue(layout.rule.bottom <= layout.search.y)
            assertAreasApart(layout)
            assertTrue(layout.contentBounds.height >= MINIMUM_VISIBLE_CONTENT)
        }
    }

    /** 寬畫面時規則、分類、搜尋與清除排成一列，由左到右不重疊且不超出畫面。 */
    @Test
    fun `wide screens put every control on one row`() {
        listOf(427, 960).forEach { width ->
            val layout = measure(width = width, height = 240)
            val controls = listOf(layout.rule, layout.category, layout.search, layout.clear)

            assertTrue(layout.singleRow)
            assertEquals(1, controls.map { it.y }.distinct().size)
            controls.zipWithNext().forEach { (left, right) -> assertEquals(left.right + RuleCatalogueScreenLayout.GAP, right.x) }
            assertEquals(RuleCatalogueScreenLayout.MARGIN, layout.rule.x)
            assertEquals(layout.width - RuleCatalogueScreenLayout.MARGIN, layout.clear.right)
            assertEquals(layout.rule.width, layout.category.width)
            assertTrue(layout.rule.width <= RuleCatalogueScreenLayout.MAX_SELECTOR_WIDTH)
            assertAreasApart(layout)
        }
    }

    /** 兩列排法時，兩列都填滿可用寬度。 */
    @Test
    fun `two row controls fill the available width`() {
        val layout = measure(width = 320, height = 240)

        assertEquals(RuleCatalogueScreenLayout.MARGIN, layout.rule.x)
        assertEquals(layout.width - RuleCatalogueScreenLayout.MARGIN, layout.category.right)
        assertEquals(RuleCatalogueScreenLayout.MARGIN, layout.search.x)
        assertEquals(layout.width - RuleCatalogueScreenLayout.MARGIN, layout.clear.right)
    }

    /** 從房間或歷史開啟時，設定來源切換按鈕放在關閉按鈕左側，兩者一起置中；一般開啟時只有置中的關閉按鈕。 */
    @Test
    fun `footer places the source toggle beside the close button`() {
        val general = measure(width = 427, height = 240)
        val withToggle = measure(width = 427, height = 240, sourceToggle = true)
        val toggle = checkNotNull(withToggle.sourceToggle)

        assertEquals(null, general.sourceToggle)
        assertTrue(abs(general.close.x - (general.width - general.close.right)) <= 1)
        assertEquals(withToggle.close.y, toggle.y)
        assertEquals(toggle.right + RuleCatalogueScreenLayout.GAP, withToggle.close.x)
        assertTrue(abs(toggle.x - (withToggle.width - withToggle.close.right)) <= 1)
        assertTrue(toggle.x >= RuleCatalogueScreenLayout.MARGIN)
    }

    /** 清除按鈕依文字寬度變寬，但至少與按鈕同高。 */
    @Test
    fun `clear button follows its text width`() {
        assertEquals(RuleCatalogueScreenLayout.ROW_HEIGHT, measure(width = 320, height = 240, clearWidth = 4).clear.width)
        assertEquals(44, measure(width = 320, height = 240, clearWidth = 44).clear.width)
    }

    /** 畫面太矮時底部按鈕仍留在畫面內，只縮小內容區。 */
    @Test
    fun `very short screens keep the footer on screen`() {
        val layout = measure(width = 320, height = 120, sourceToggle = true)

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
            headingWidth = ::headingWidth,
        )

        assertEquals(listOf(RuleCatalogueTileGroupRole.HAND, RuleCatalogueTileGroupRole.WINNING_TILE), result.headings.map { it.role })
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
        val result = RuleCatalogueTileLayout.measure(groups = groups, width = width, headingWidth = ::headingWidth)

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
        val tiles = (1..9).map { Tile.Numeric(Tile.Suit.Character, it) }
        val width = 100
        val result = RuleCatalogueTileLayout.measure(
            groups = listOf(
                RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.HAND, tiles),
                RuleCatalogueTileGroup(RuleCatalogueTileGroupRole.WINNING_TILE, listOf(tiles.last())),
            ),
            width = width,
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
        val controlsBottom = listOf(layout.rule, layout.category, layout.search, layout.clear).maxOf { it.bottom }
        assertTrue(controlsBottom <= layout.contentBounds.y)
        assertTrue(layout.contentBounds.bottom <= layout.close.y)
        assertTrue(layout.close.bottom <= layout.height)
        assertTrue(layout.scrollbarBounds.x >= layout.contentBounds.right)
        assertTrue(layout.scrollbarBounds.right <= layout.width)
        listOf(layout.rule, layout.category, layout.search, layout.clear, layout.close).forEach { assertTrue(it.right <= layout.width) }
    }

    /**
     * 以測試用尺寸建立畫面幾何。
     *
     * @param width 畫面寬度。
     * @param height 畫面高度。
     * @param clearWidth 清除按鈕寬度。
     * @param sourceToggle 是否顯示設定來源切換按鈕。
     * @return 畫面幾何。
     */
    private fun measure(
        width: Int,
        height: Int,
        clearWidth: Int = 36,
        sourceToggle: Boolean = false,
    ): RuleCatalogueScreenLayout = RuleCatalogueScreenLayout.measure(
        width = width,
        height = height,
        clearWidth = clearWidth,
        sourceToggle = sourceToggle,
    )

    /**
     * 建立指定張數的測試牌組。
     *
     * @param role 牌組角色。
     * @param count 張數。
     * @return 測試牌組。
     */
    private fun group(role: RuleCatalogueTileGroupRole, count: Int): RuleCatalogueTileGroup = RuleCatalogueTileGroup(
        role,
        List(count) { Tile.Numeric(Tile.Suit.Character, it % 9 + 1) },
    )

    /**
     * 測試用標題寬度，短於任何一張牌。
     *
     * @param role 牌組角色。
     * @return 標題寬度。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun headingWidth(role: RuleCatalogueTileGroupRole): Int = 12

    /** 測試門檻。 */
    private companion object {
        /** 最小畫面上內容區至少要能顯示的高度。 */
        const val MINIMUM_VISIBLE_CONTENT = 130
    }
}
