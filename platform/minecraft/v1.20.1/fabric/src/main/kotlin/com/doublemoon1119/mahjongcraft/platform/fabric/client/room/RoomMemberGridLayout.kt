package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout

/**
 * 房間成員卡片 grid 的版面幾何。
 *
 * 等待室與進行中對局共用同一套換欄規則、固定列高與捲動機制，差別只在卡片內容，因此兩者都用這個類別
 * 計算座標。進行中對局的卡片內另有一段可捲動的資訊清單，它的幾何由 [infoScrollbar] 與相關成員提供。
 *
 * 純資料，不依賴任何 Minecraft 型別；視窗尺寸、玩家數與捲動位置都由呼叫端傳入。
 *
 * @property windowWidth 目前視窗寬度。
 * @property windowHeight 目前視窗高度。
 * @property playerCount 目前要排版的玩家數。
 * @property scrollIndex 目前第一個可見列的索引。
 */
internal data class RoomMemberGridLayout(
    val windowWidth: Int,
    val windowHeight: Int,
    val playerCount: Int,
    val scrollIndex: Int,
) {
    /** 排版時至少視為一位玩家，避免空清單算出零欄。 */
    private val effectiveCount: Int = playerCount.coerceAtLeast(1)

    /** 扣掉左右邊距後可用的寬度。 */
    private val availableWidth: Int = windowWidth - GRID_MARGIN * 2

    /** 目前欄數；不額外限制欄數，完全依可用寬度決定。 */
    val columns: Int = minOf(effectiveCount, (availableWidth / CARD_MIN_WIDTH).coerceAtLeast(1))

    /** 單張卡片寬度。 */
    val cardWidth: Int = minOf(CARD_MAX_WIDTH, availableWidth / columns)

    /** 目前列數。 */
    val rows: Int = (effectiveCount + columns - 1) / columns

    /** 保留底部操作列後 grid 可用的下界。 */
    val gridBottom: Int = windowHeight - GRID_BOTTOM_RESERVED_HEIGHT

    /** 目前視窗高度能完整顯示幾列卡片。 */
    val visibleRows: Int = ((gridBottom - CARD_TOP) / ROW_HEIGHT).coerceAtLeast(1)

    /** 右側 scrollbar 的水平座標。 */
    val scrollbarColumn: RoomScrollbarColumn = RoomScrollbarColumn(windowWidth)

    /** 卡片 grid scrollbar 的垂直幾何。 */
    val scrollbar: ScrollbarLayout = ScrollbarLayout(
        trackTop = CARD_TOP,
        trackBottom = gridBottom,
        itemCount = rows,
        visibleItemCount = visibleRows,
        scrollIndex = scrollIndex,
        minimumThumbHeight = MINIMUM_THUMB_HEIGHT,
    )

    /** 目前螢幕上實際看得到的列範圍；捲動超出視窗的列不參與繪製與命中判定。 */
    val visibleGridRows: IntRange = scrollIndex until minOf(scrollIndex + visibleRows, rows)

    /** 卡片內資訊清單一次能顯示幾行。 */
    val visibleInfoRows: Int = ((CARD_HEIGHT - INFO_OFFSET) / INFO_ROW_HEIGHT).coerceAtLeast(1)

    /**
     * 計算指定列的卡片頂端。
     *
     * @param row 從零開始的完整清單列索引。
     * @return 扣除捲動偏移後的頂端 Y 座標。
     */
    fun cardTop(row: Int): Int = CARD_TOP + (row - scrollIndex) * ROW_HEIGHT

    /**
     * 計算指定列的卡片底端。
     *
     * @param row 從零開始的完整清單列索引。
     * @return 卡片底端的 Y 座標。
     */
    fun cardBottom(row: Int): Int = cardTop(row) + CARD_HEIGHT

    /**
     * 計算指定列資訊清單的起點。
     *
     * @param row 從零開始的完整清單列索引。
     * @return 資訊清單頂端的 Y 座標。
     */
    fun infoTop(row: Int): Int = cardTop(row) + INFO_OFFSET

    /**
     * 計算資訊清單的最大合法捲動位置。
     *
     * @param totalInfoRows 完整資訊清單的行數。
     * @return 最大首行索引；內容未超出可見範圍時為零。
     */
    fun maximumInfoScroll(totalInfoRows: Int): Int = (totalInfoRows - visibleInfoRows).coerceAtLeast(0)

    /**
     * 建立指定列資訊清單捲軸的垂直幾何。
     *
     * @param row 從零開始的完整清單列索引。
     * @param totalInfoRows 完整資訊清單的行數。
     * @param infoScrollIndex 資訊清單第一個可見行的索引。
     * @return 該列資訊區的捲軸幾何。
     */
    fun infoScrollbar(row: Int, totalInfoRows: Int, infoScrollIndex: Int): ScrollbarLayout = ScrollbarLayout(
        trackTop = infoTop(row),
        trackBottom = cardBottom(row),
        itemCount = totalInfoRows,
        visibleItemCount = visibleInfoRows,
        scrollIndex = infoScrollIndex,
        minimumThumbHeight = MINIMUM_THUMB_HEIGHT,
    )

    /**
     * 判斷游標是否落在成員卡片捲軸的命中範圍內；內容未超出可見範圍時回傳 false。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 需要捲動且游標位於命中範圍內時為 true。
     */
    fun isOverGridScrollbar(mouseX: Double, mouseY: Double): Boolean = rows > visibleRows &&
        scrollbarColumn.containsHorizontally(mouseX) &&
        mouseY >= CARD_TOP &&
        mouseY <= gridBottom

    /**
     * 尋找游標所在的可見資訊捲軸列。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param totalInfoRows 每列資訊清單的完整行數。
     * @return 完整清單中的列索引；不需捲動或未命中任何可見列時為 null。
     */
    fun infoScrollbarRowAt(mouseX: Double, mouseY: Double, totalInfoRows: Int): Int? {
        if (maximumInfoScroll(totalInfoRows) <= 0) return null
        if (!scrollbarColumn.containsHorizontally(mouseX)) return null
        return visibleGridRows.firstOrNull { row -> mouseY >= infoTop(row) && mouseY <= cardBottom(row) }
    }

    internal companion object {
        /** 卡片 grid 的起始 Y。 */
        const val CARD_TOP: Int = 58

        /** 單張卡片高度。 */
        const val CARD_HEIGHT: Int = 146

        /** 卡片內資訊清單相對卡片頂端的偏移。 */
        const val INFO_OFFSET: Int = 111

        /** 卡片 grid 的左右邊距。 */
        const val GRID_MARGIN: Int = 8

        /** 單張卡片的最大寬度。 */
        const val CARD_MAX_WIDTH: Int = 126

        /** 單張卡片的最小寬度，決定可容納的欄數。 */
        const val CARD_MIN_WIDTH: Int = 70

        /** 含間距的單列高度。 */
        const val ROW_HEIGHT: Int = 156

        /** 保留給底部操作列的高度。 */
        const val GRID_BOTTOM_RESERVED_HEIGHT: Int = 40

        /** 卡片內資訊清單的單行高度。 */
        const val INFO_ROW_HEIGHT: Int = 12

        /** Scrollbar thumb 最小高度。 */
        const val MINIMUM_THUMB_HEIGHT: Int = 12
    }
}
