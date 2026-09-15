package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec

/**
 * 房間設定頁的版面幾何。
 *
 * 視窗寬度低於 [COMPACT_MIN_WIDTH] 時改用窄視窗堆疊版面：規則切換鈕與分類格佔用內容區頂部空間、欄位列
 * 加高、label 與控制項改為上下堆疊。兩種版面的差異全部集中在這裡，呼叫端只讀算好的座標。
 *
 * 純資料，不依賴任何 Minecraft 型別；視窗尺寸、分類數、欄位數與捲動位置都由呼叫端傳入。
 */
internal data class RoomSettingsLayout(
    /** 目前視窗寬度。 */
    val windowWidth: Int,
    /** 目前視窗高度。 */
    val windowHeight: Int,
    /** 目前規則模組的設定分類數。 */
    val categoryCount: Int,
    /** 目前分類的完整欄位數。 */
    val fieldCount: Int,
    /** 目前第一個可見欄位的 index。 */
    val scrollIndex: Int,
) {
    /** 視窗寬度不足以容納側邊欄、label 與控制項並排時改用堆疊版面。 */
    val isCompact: Boolean = windowWidth < COMPACT_MIN_WIDTH

    /** 保留底部操作列後的內容區下界。 */
    val contentBottom: Int = windowHeight - BOTTOM_RESERVED_HEIGHT

    /** 底部狀態文字的 Y 座標。 */
    val statusY: Int = windowHeight - STATUS_BOTTOM_OFFSET

    /** Compact 版面下欄位列必須從分類格下方開始。 */
    val fieldsTop: Int = if (isCompact) {
        val categoryRows = (categoryCount + 1) / 2
        COMPACT_CATEGORY_TOP + categoryRows * COMPACT_CATEGORY_ROW_HEIGHT + COMPACT_FIELDS_GAP
    } else {
        FIELDS_TOP
    }

    /** Compact 版面下 label 與控制項上下堆疊，單一欄位列需要更高的垂直空間。 */
    val fieldRowHeight: Int = if (isCompact) COMPACT_FIELD_ROW_HEIGHT else REGULAR_FIELD_ROW_HEIGHT

    /** 目前視窗能完整容納的欄位數。 */
    val maximumVisibleFields: Int = ((contentBottom - fieldsTop) / fieldRowHeight).coerceAtLeast(1)

    /** 最大合法捲動位置。 */
    val maximumScroll: Int = (fieldCount - maximumVisibleFields).coerceAtLeast(0)

    /** 右側 scrollbar 的水平座標。 */
    val scrollbarColumn: RoomScrollbarColumn = RoomScrollbarColumn(windowWidth)

    /** 設定欄位 scrollbar 的垂直幾何。 */
    val scrollbar: ScrollbarLayout = ScrollbarLayout(
        trackTop = fieldsTop,
        trackBottom = contentBottom,
        itemCount = fieldCount,
        visibleItemCount = maximumVisibleFields,
        scrollIndex = scrollIndex,
        minimumThumbHeight = MINIMUM_THUMB_HEIGHT,
    )

    /**
     * 控制項在該欄位列中的實際 Y 座標。
     *
     * Compact 版面下 label 畫在 [rowTop]，控制項必須讓出 label 的高度才不會疊在一起；非 compact 版面則與
     * label 同高並排。
     */
    fun fieldControlY(rowTop: Int): Int = if (isCompact) rowTop + COMPACT_CONTROL_Y_OFFSET else rowTop

    /** 依 editor 實際控制元件的左界，供計算本地化標籤的可用寬度。 */
    fun fieldControlLeft(editor: GameConfigEditorSpec): Int = when (editor) {
        GameConfigEditorSpec.BooleanToggle -> windowWidth - BOOLEAN_CONTROL_LEFT_INSET
        is GameConfigEditorSpec.SingleChoice,
        is GameConfigEditorSpec.IntegerInput,
        -> windowWidth - WIDE_CONTROL_LEFT_INSET
    }

    /** 游標是否落在設定欄位 scrollbar 的可拖曳範圍內。 */
    fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = scrollbarColumn.containsHorizontally(mouseX) &&
        mouseY >= fieldsTop &&
        mouseY <= contentBottom

    internal companion object {
        /** 視窗寬度低於此值時，設定頁改為窄視窗堆疊版面。 */
        const val COMPACT_MIN_WIDTH: Int = 400

        /** 非 compact 版面的欄位列起點。 */
        const val FIELDS_TOP: Int = 54

        /** 保留給底部操作列的高度。 */
        const val BOTTOM_RESERVED_HEIGHT: Int = 56

        /** 底部狀態文字距離視窗底部的距離。 */
        const val STATUS_BOTTOM_OFFSET: Int = 47

        /** 非 compact 版面的欄位列高。 */
        const val REGULAR_FIELD_ROW_HEIGHT: Int = 28

        /** Compact 版面分類格的起點。 */
        const val COMPACT_CATEGORY_TOP: Int = 78

        /** Compact 版面每列分類格的高度。 */
        const val COMPACT_CATEGORY_ROW_HEIGHT: Int = 24

        /** Compact 版面分類格與欄位列之間的間距。 */
        const val COMPACT_FIELDS_GAP: Int = 8

        /** Compact 版面的欄位列高。 */
        const val COMPACT_FIELD_ROW_HEIGHT: Int = 42

        /** Compact 版面 label 與控制項之間的垂直間距。 */
        const val COMPACT_CONTROL_Y_OFFSET: Int = 14

        /** 布林開關控制項相對視窗右界的左界內縮。 */
        const val BOOLEAN_CONTROL_LEFT_INSET: Int = 152

        /** 單選與整數控制項相對視窗右界的左界內縮。 */
        const val WIDE_CONTROL_LEFT_INSET: Int = 212

        /** Scrollbar thumb 最小高度。 */
        const val MINIMUM_THUMB_HEIGHT: Int = 12
    }
}
