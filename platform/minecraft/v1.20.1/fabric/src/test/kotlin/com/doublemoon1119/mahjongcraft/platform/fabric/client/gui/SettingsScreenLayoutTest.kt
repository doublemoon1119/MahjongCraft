package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證設定類畫面寬版與窄版的版面計算。 */
class SettingsScreenLayoutTest {
    /** 視窗寬 480 時與設計稿相同：左右邊距 12、內容寬 456。 */
    @Test
    fun `regular layout uses twelve pixel margins`() {
        val layout = SettingsScreenLayout(windowWidth = 480, windowHeight = 270, hasPageSwitch = true, hasRuleRow = true)

        assertFalse(layout.isCompact)
        assertEquals(12, layout.contentLeft)
        assertEquals(456, layout.contentWidth)
        assertEquals(468, layout.contentRight)
    }

    /** 視窗更寬時內容寬度不超過 456 並水平置中。 */
    @Test
    fun `content width is capped and centred on wide windows`() {
        val layout = SettingsScreenLayout(windowWidth = 960, windowHeight = 540, hasPageSwitch = false, hasRuleRow = false)

        assertEquals(456, layout.contentWidth)
        assertEquals(252, layout.contentLeft)
        assertEquals(708, layout.contentRight)
        assertEquals(960, layout.contentLeft + layout.contentRight)
    }

    /** 寬版控制項寬 150 並靠右，標籤與控制項之間保留 8，每列相距 24。 */
    @Test
    fun `regular rows place the label left and a fixed width control right`() {
        val layout = SettingsScreenLayout(windowWidth = 480, windowHeight = 270, hasPageSwitch = true, hasRuleRow = true)

        assertEquals(150, layout.controlWidth)
        assertEquals(318, layout.controlLeft)
        assertEquals(298, layout.labelWidth)
        assertEquals(24, layout.fieldRowTop(1) - layout.fieldRowTop(0))
        assertEquals(layout.fieldRowTop(0), layout.fieldControlY(layout.fieldRowTop(0)))
        assertEquals(layout.fieldRowTop(0) + 6, layout.fieldLabelY(layout.fieldRowTop(0)))
    }

    /** 數值欄位的減號、輸入框、加號合計與其他控制項同寬。 */
    @Test
    fun `integer controls fill the control width`() {
        val layout = SettingsScreenLayout(windowWidth = 480, windowHeight = 270, hasPageSwitch = true, hasRuleRow = true)

        assertEquals(layout.controlLeft, layout.integerMinusX)
        assertEquals(layout.integerMinusX + 20 + 4, layout.integerTextX)
        assertEquals(102, layout.integerTextWidth)
        assertEquals(layout.integerTextX + layout.integerTextWidth + 4, layout.integerPlusX)
        assertEquals(layout.controlLeft + layout.controlWidth, layout.integerPlusX + 20)
    }

    /** 寬度小於 400 時改用窄版：邊距 18，標籤在上、控制項在下並佔滿內容寬度。 */
    @Test
    fun `compact layout stacks the label above a full width control`() {
        val layout = SettingsScreenLayout(windowWidth = 320, windowHeight = 360, hasPageSwitch = true, hasRuleRow = true)

        assertTrue(layout.isCompact)
        assertEquals(18, layout.contentLeft)
        assertEquals(284, layout.contentWidth)
        assertEquals(layout.contentWidth, layout.controlWidth)
        assertEquals(layout.contentLeft, layout.controlLeft)
        assertEquals(layout.contentWidth, layout.labelWidth)
        val rowTop = layout.fieldRowTop(0)
        assertEquals(rowTop, layout.fieldLabelY(rowTop))
        assertEquals(rowTop + 12, layout.fieldControlY(rowTop))
        assertEquals(39, layout.fieldRowTop(1) - rowTop)
        assertFalse(SettingsScreenLayout(windowWidth = 400, windowHeight = 360, hasPageSwitch = true, hasRuleRow = true).isCompact)
    }

    /** 區塊由上到下依序為頁面切換、規則列、頁籤、欄位、狀態文字與底部按鈕，彼此不重疊。 */
    @Test
    fun `sections stack from top to bottom without overlapping`() {
        listOf(480 to 270, 320 to 240, 960 to 540).forEach { (width, height) ->
            val layout = SettingsScreenLayout(windowWidth = width, windowHeight = height, hasPageSwitch = true, hasRuleRow = true)
            val row = assertNotNull(layout.ruleRow)

            assertTrue(row.top >= SettingsScreenLayout.PAGE_SWITCH_Y + 20, "Rule row overlaps the page switch at $width x $height")
            assertTrue(row.dividerY >= row.selectorY + 20)
            assertTrue(layout.tabsTop > row.dividerY)
            assertTrue(layout.fieldsTop > layout.tabsTop + SettingsTabStripLayout.SELECTED_HEIGHT)
            assertTrue(layout.fieldsBottom <= layout.statusY)
            assertTrue(layout.statusY + 9 <= layout.footerY)
            assertEquals(height - 28, layout.footerY)
            val lastRowTop = layout.fieldRowTop(layout.visibleFieldCount - 1)
            assertTrue(layout.fieldControlY(lastRowTop) + 20 <= layout.fieldsBottom, "Last visible row overflows at $width x $height")
        }
    }

    /** 沒有頁面切換與規則列的畫面，頁籤緊接在標題下方，欄位區因此較高。 */
    @Test
    fun `screens without page switch or rule row start the tabs under the title`() {
        val client = SettingsScreenLayout(windowWidth = 480, windowHeight = 270, hasPageSwitch = false, hasRuleRow = false)
        val room = SettingsScreenLayout(windowWidth = 480, windowHeight = 270, hasPageSwitch = true, hasRuleRow = true)

        assertNull(client.ruleRow)
        assertEquals(26, client.tabsTop)
        assertTrue(client.visibleFieldCount > room.visibleFieldCount)
    }

    /** 寬版規則列排成一列：規則選擇器靠左並接著規則一覽按鈕，玩法選擇器靠右對齊內容右界。 */
    @Test
    fun `regular rule row keeps the rule left and the variant right`() {
        val layout = SettingsScreenLayout(
            windowWidth = 480,
            windowHeight = 270,
            hasPageSwitch = true,
            hasRuleRow = true,
            ruleLabelWidth = 22,
            variantLabelWidth = 37,
        )
        val row = assertNotNull(layout.ruleRow)

        assertEquals(150, row.selectorWidth)
        assertEquals(layout.contentLeft, row.ruleLabelX)
        assertEquals(layout.contentLeft + 22 + 6, row.ruleSelectorX)
        assertEquals(row.ruleSelectorX + 150 + 4, row.catalogueX)
        assertEquals(20, row.catalogueSize)
        assertEquals(layout.contentRight - 150, row.variantSelectorX)
        assertEquals(row.variantSelectorX - 6 - 37, row.variantLabelX)
        assertEquals(row.top, row.selectorY)
        assertTrue(row.catalogueX + row.catalogueSize <= row.variantLabelX)
    }

    /** 寬版放不下兩個 150 寬的選擇器時，兩者一起縮窄，規則與玩法兩組仍不重疊。 */
    @Test
    fun `regular rule row narrows both selectors when space runs out`() {
        val layout = SettingsScreenLayout(
            windowWidth = 400,
            windowHeight = 270,
            hasPageSwitch = true,
            hasRuleRow = true,
            ruleLabelWidth = 22,
            variantLabelWidth = 37,
        )
        val row = assertNotNull(layout.ruleRow)

        assertTrue(row.selectorWidth < 150)
        assertTrue(row.catalogueX + row.catalogueSize + 12 <= row.variantLabelX)
        assertEquals(layout.contentRight, row.variantSelectorX + row.selectorWidth)
    }

    /** 窄版規則列分成兩欄：標籤在上、選擇器在下佔滿欄寬，規則一覽按鈕縮小放在規則標籤列右端。 */
    @Test
    fun `compact rule row uses two columns`() {
        val layout = SettingsScreenLayout(
            windowWidth = 320,
            windowHeight = 360,
            hasPageSwitch = true,
            hasRuleRow = true,
            ruleLabelWidth = 22,
            variantLabelWidth = 37,
        )
        val row = assertNotNull(layout.ruleRow)

        assertEquals(140, row.selectorWidth)
        assertEquals(layout.contentLeft, row.ruleSelectorX)
        assertEquals(layout.contentRight - 140, row.variantSelectorX)
        assertEquals(row.variantSelectorX, row.variantLabelX)
        assertEquals(row.top + 17, row.selectorY)
        assertEquals(14, row.catalogueSize)
        assertEquals(row.ruleSelectorX + 140, row.catalogueX + row.catalogueSize)
        assertEquals(row.top, row.catalogueY)
        assertTrue(row.ruleSelectorX + row.selectorWidth < row.variantSelectorX)
    }

    /** 欄位捲軸畫在內容右側的邊距內，命中範圍比繪製範圍稍寬，且只涵蓋欄位區的高度。 */
    @Test
    fun `scrollbar sits in the margin right of the content`() {
        val layout = SettingsScreenLayout(windowWidth = 480, windowHeight = 270, hasPageSwitch = true, hasRuleRow = true)
        val scrollbar = layout.scrollbar(fieldCount = 20, scrollIndex = 3)

        assertEquals(layout.contentRight + 4, layout.scrollbarLeft)
        assertTrue(layout.scrollbarLeft + SettingsScrollbar.WIDTH <= 480)
        assertEquals(layout.fieldsTop, scrollbar.trackTop)
        assertEquals(layout.fieldsBottom, scrollbar.trackBottom)
        assertEquals(20 - layout.visibleFieldCount, scrollbar.maximumScroll)
        assertTrue(layout.isOverScrollbar(layout.scrollbarLeft - 2.0, layout.fieldsTop + 1.0))
        assertTrue(layout.isOverScrollbar(layout.scrollbarLeft + 4.0, layout.fieldsBottom - 1.0))
        assertFalse(layout.isOverScrollbar(layout.contentRight - 1.0, layout.fieldsTop + 1.0))
        assertFalse(layout.isOverScrollbar(layout.scrollbarLeft + 1.0, layout.fieldsBottom + 1.0))
    }
}
