package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證設定頁在一般與窄視窗版面下的幾何。 */
class RoomSettingsLayoutTest {
    /** 寬度達到門檻時維持並排版面。 */
    @Test
    fun `keeps the regular layout at the compact threshold`() {
        val layout = layout(width = RoomSettingsLayout.COMPACT_MIN_WIDTH)

        assertFalse(layout.isCompact)
        assertEquals(RoomSettingsLayout.FIELDS_TOP, layout.fieldsTop)
        assertEquals(RoomSettingsLayout.REGULAR_FIELD_ROW_HEIGHT, layout.fieldRowHeight)
    }

    /** 寬度低於門檻一像素即切換為堆疊版面。 */
    @Test
    fun `switches to the compact layout below the threshold`() {
        val layout = layout(width = RoomSettingsLayout.COMPACT_MIN_WIDTH - 1, categoryCount = 3)

        assertTrue(layout.isCompact)
        assertEquals(RoomSettingsLayout.COMPACT_FIELD_ROW_HEIGHT, layout.fieldRowHeight)
        // 三個分類排成兩列。
        val expected = RoomSettingsLayout.COMPACT_CATEGORY_TOP +
            2 * RoomSettingsLayout.COMPACT_CATEGORY_ROW_HEIGHT +
            RoomSettingsLayout.COMPACT_FIELDS_GAP
        assertEquals(expected, layout.fieldsTop)
    }

    /** 分類格每兩個佔一列，奇數時最後一列只有一個。 */
    @Test
    fun `packs category cells two per row`() {
        val base = RoomSettingsLayout.COMPACT_CATEGORY_TOP + RoomSettingsLayout.COMPACT_FIELDS_GAP
        val rowHeight = RoomSettingsLayout.COMPACT_CATEGORY_ROW_HEIGHT
        val compactWidth = RoomSettingsLayout.COMPACT_MIN_WIDTH - 1

        assertEquals(base, layout(width = compactWidth, categoryCount = 0).fieldsTop)
        assertEquals(base + rowHeight, layout(width = compactWidth, categoryCount = 1).fieldsTop)
        assertEquals(base + rowHeight, layout(width = compactWidth, categoryCount = 2).fieldsTop)
        assertEquals(base + 2 * rowHeight, layout(width = compactWidth, categoryCount = 4).fieldsTop)
    }

    /** 控制項在並排版面與 label 同高，堆疊版面則讓出 label 的高度。 */
    @Test
    fun `offsets the control below the label only when compact`() {
        assertEquals(100, layout(width = 640).fieldControlY(100))
        assertEquals(
            100 + RoomSettingsLayout.COMPACT_CONTROL_Y_OFFSET,
            layout(width = RoomSettingsLayout.COMPACT_MIN_WIDTH - 1).fieldControlY(100),
        )
    }

    /** 布林開關的控制項左界比單選與整數輸入更靠右。 */
    @Test
    fun `places the boolean control further right than the wider editors`() {
        val layout = layout(width = 640)

        assertEquals(640 - RoomSettingsLayout.BOOLEAN_CONTROL_LEFT_INSET, layout.fieldControlLeft(GameConfigEditorSpec.BooleanToggle))
        assertEquals(
            640 - RoomSettingsLayout.WIDE_CONTROL_LEFT_INSET,
            layout.fieldControlLeft(GameConfigEditorSpec.IntegerInput(minimum = 0, maximum = 1)),
        )
    }

    /** 極矮視窗至少仍保留一列可見欄位。 */
    @Test
    fun `always keeps at least one visible field row`() {
        assertEquals(1, layout(width = 640, height = 1).maximumVisibleFields)
    }

    /** 欄位數未超出可見範圍時不可捲動。 */
    @Test
    fun `reports no scroll when every field fits`() {
        val layout = layout(width = 640, height = 480, fieldCount = 2)

        assertTrue(layout.maximumVisibleFields >= 2)
        assertEquals(0, layout.maximumScroll)
        assertEquals(0, layout.scrollbar.maximumScroll)
    }

    /** 欄位數超出可見範圍時捲動上限為兩者之差。 */
    @Test
    fun `reports the overflow as the maximum scroll`() {
        val layout = layout(width = 640, height = 480, fieldCount = 100)

        assertEquals(100 - layout.maximumVisibleFields, layout.maximumScroll)
    }

    /** Scrollbar 命中判定涵蓋整個內容區的垂直範圍。 */
    @Test
    fun `hit tests the scrollbar over the whole content area`() {
        val layout = layout(width = 640, height = 480)
        val insideX = (640 - 10).toDouble()

        assertTrue(layout.isOverScrollbar(insideX, layout.fieldsTop.toDouble()))
        assertTrue(layout.isOverScrollbar(insideX, layout.contentBottom.toDouble()))
        assertFalse(layout.isOverScrollbar(insideX, (layout.fieldsTop - 1).toDouble()))
        assertFalse(layout.isOverScrollbar(insideX, (layout.contentBottom + 1).toDouble()))
        assertFalse(layout.isOverScrollbar(0.0, layout.fieldsTop.toDouble()))
    }

    /** 建立測試用版面；未指定的輸入使用不影響該測試的預設值。 */
    private fun layout(
        width: Int,
        height: Int = 480,
        categoryCount: Int = 0,
        fieldCount: Int = 0,
        scrollIndex: Int = 0,
    ) = RoomSettingsLayout(
        windowWidth = width,
        windowHeight = height,
        categoryCount = categoryCount,
        fieldCount = fieldCount,
        scrollIndex = scrollIndex,
    )
}
