package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ButtonRowLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar

/**
 * 房間成員頁的版面：成員卡片 grid、底部按鈕列上方的狀態列與底部按鈕列。
 *
 * 內容寬度與設定頁相同，最多 [FormScreenLayout.MAX_CONTENT_WIDTH] 並置中；卡片在內容寬度內置中排列，列數超出可見
 * 範圍時以內容右側邊距內的捲軸捲動。只有一列卡片時，卡片依可用高度放大並上下置中；兩列以上以固定列高為上限；短視窗優先縮小人物範圍。
 * 卡片內的名稱與下方各行從卡片底端往上算，人物畫在名稱上方。等待室與進行中對局共用同一套換欄規則、放大規則與
 * 捲動機制。進行中對局的卡片內另有一段資訊清單，放不下時捲軸畫在該卡片內，每張卡片各自捲動。
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
    /** 內容寬度與底部按鈕列沿用設定頁版面。 */
    private val frame = FormScreenLayout(windowWidth = windowWidth, windowHeight = windowHeight, hasPageSwitch = true, hasRuleRow = false)

    /** 內容左界。 */
    val contentLeft: Int = frame.contentLeft

    /** 內容右界。 */
    val contentRight: Int = frame.contentRight

    /** 排版時至少視為一位玩家，避免空清單算出零欄。 */
    private val effectiveCount: Int = playerCount.coerceAtLeast(1)

    /** 目前欄數；依內容寬度決定。 */
    val columns: Int = minOf(effectiveCount, (frame.contentWidth / CARD_MIN_WIDTH).coerceAtLeast(1))

    /** 單張卡片寬度。 */
    val cardWidth: Int = minOf(CARD_MAX_WIDTH, frame.contentWidth / columns)

    /** 卡片 grid 的左界，整組在內容寬度內置中。 */
    private val gridLeft: Int = windowWidth / 2 - columns * cardWidth / 2

    /** 目前列數。 */
    val rows: Int = (effectiveCount + columns - 1) / columns

    /** 底部按鈕列上界。 */
    val footerY: Int = frame.footerY

    /** 底部按鈕列上方狀態文字的 Y 座標，例如對局進行中的唯讀說明。 */
    val statusY: Int = frame.statusY

    /** 卡片 grid 可用的下界，在狀態列上方。 */
    val gridBottom: Int = frame.fieldsBottom

    /** 卡片區可用的高度，從 [CARD_TOP] 到 [gridBottom]。 */
    private val availableHeight: Int = (gridBottom - CARD_TOP).coerceAtLeast(0)

    /** 卡片高度優先讓出頁尾空間。卡片至少要能容納名稱及下方的資訊與操作。 */
    val cardHeight: Int = when {
        availableHeight < MINIMUM_CARD_HEIGHT -> 0
        rows == 1 -> minOf(availableHeight, (CARD_HEIGHT * MAX_CARD_SCALE).toInt())
        else -> minOf(availableHeight, CARD_HEIGHT)
    }

    /** 人物依卡片高度縮放，文字和控制項使用原尺寸。 */
    val cardScale: Float = cardHeight.toFloat() / CARD_HEIGHT

    /** 含間距的實際列高。 */
    private val rowHeight: Int = cardHeight + (ROW_HEIGHT - CARD_HEIGHT)

    /** 目前視窗高度能完整顯示幾列卡片。 */
    val visibleRows: Int = if (cardHeight == 0) 0 else minOf(rows, 1 + (availableHeight - cardHeight) / rowHeight)

    /** 將視窗大小變動前的捲動位置收斂到目前可用的範圍。 */
    private val firstVisibleRow: Int = scrollIndex.coerceIn(0, if (visibleRows == 0) 0 else rows - visibleRows)

    /** 卡片 grid 捲軸的左界，在內容右側的邊距內。 */
    val scrollbarLeft: Int = frame.scrollbarLeft

    /** 卡片 grid 捲軸的垂直幾何。 */
    val scrollbar: ScrollbarLayout = ScrollbarLayout(
        trackTop = CARD_TOP,
        trackBottom = gridBottom,
        itemCount = if (visibleRows == 0) 0 else rows,
        visibleItemCount = visibleRows,
        scrollIndex = firstVisibleRow,
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT,
    )

    /** 目前螢幕上實際看得到的列範圍；捲動超出視窗的列不參與繪製與命中判定。 */
    val visibleGridRows: IntRange = firstVisibleRow until firstVisibleRow + visibleRows

    /** 第一個可見列的頂端；只有一列時整列在可用高度內上下置中。 */
    private val firstRowTop: Int = if (rows == 1) CARD_TOP + ((availableHeight - cardHeight) / 2).coerceAtLeast(0) else CARD_TOP

    /** 卡片內資訊清單一次能顯示幾行；卡片放大時行數跟著增加。 */
    val visibleInfoRows: Int = (((CARD_HEIGHT - INFO_OFFSET) * cardScale.coerceAtLeast(1f) - INFO_BOTTOM_PADDING) / INFO_ROW_HEIGHT).toInt().coerceAtLeast(1)

    /** 資訊清單區從頂端到卡片底端的高度。 */
    private val infoAreaHeight: Int = visibleInfoRows * INFO_ROW_HEIGHT + INFO_BOTTOM_PADDING

    /** 底部四格按鈕列；離開畫面的按鈕在最右一格。 */
    val footer: ButtonRowLayout = frame.footer(buttonCount = FOOTER_SLOTS)

    /**
     * 計算指定成員卡片的左界。
     *
     * @param index 成員在完整清單中的順序。
     * @return 卡片左界。
     */
    fun cardLeft(index: Int): Int = gridLeft + (index % columns) * cardWidth

    /**
     * 計算指定列的卡片頂端。
     *
     * @param row 從零開始的完整清單列索引。
     * @return 扣除捲動偏移後的頂端 Y 座標。
     */
    fun cardTop(row: Int): Int = firstRowTop + (row - firstVisibleRow) * rowHeight

    /**
     * 計算指定列的卡片底端。
     *
     * @param row 從零開始的完整清單列索引。
     * @return 卡片底端的 Y 座標。
     */
    fun cardBottom(row: Int): Int = cardTop(row) + cardHeight

    /**
     * 計算進行中對局卡片資訊清單的起點，從卡片底端往上算，資訊行數增加時往上延伸。
     *
     * @param row 從零開始的完整清單列索引。
     * @return 資訊清單頂端的 Y 座標。
     */
    fun infoTop(row: Int): Int = cardBottom(row) - infoAreaHeight

    /**
     * 計算等待室卡片狀態列的起點，例如遊戲建立者、準備狀態或 AI 策略選擇器；與卡片底端保持固定距離。
     *
     * @param row 從零開始的完整清單列索引。
     * @return 狀態列頂端的 Y 座標。
     */
    fun statusTop(row: Int): Int = cardBottom(row) - (CARD_HEIGHT - INFO_OFFSET)

    /**
     * 計算成員名稱的位置，在名稱下方第一行（[statusTop] 或 [infoTop]）之上。
     *
     * @param labelTop 名稱下方第一行的頂端。
     * @return 名稱頂端的 Y 座標。
     */
    fun nameTop(labelTop: Int): Int = labelTop - NAME_GAP

    /**
     * 計算人物腳底基線，在成員名稱之上。
     *
     * @param labelTop 名稱下方第一行的頂端。
     * @return 人物腳底的 Y 座標。
     */
    fun figureFeetY(labelTop: Int): Int = nameTop(labelTop) - FIGURE_GAP

    /**
     * 計算資訊清單的最大合法捲動位置。
     *
     * @param totalInfoRows 完整資訊清單的行數。
     * @return 最大首行索引；內容未超出可見範圍時為零。
     */
    fun maximumInfoScroll(totalInfoRows: Int): Int = (totalInfoRows - visibleInfoRows).coerceAtLeast(0)

    /**
     * 計算成員卡片內資訊捲軸的左界，在卡片右緣內側。
     *
     * @param index 成員在完整清單中的順序。
     * @return 捲軸左界。
     */
    fun infoScrollbarLeft(index: Int): Int = cardLeft(index) + cardWidth - CARD_RIGHT_INSET - INFO_SCROLLBAR_INSET - VerticalScrollbar.WIDTH

    /**
     * 建立指定成員卡片內資訊捲軸的垂直幾何。
     *
     * @param index 成員在完整清單中的順序。
     * @param totalInfoRows 這位成員資訊清單的完整行數。
     * @param infoScrollIndex 這位成員資訊清單第一個可見行的索引。
     * @return 該卡片資訊區的捲軸幾何。
     */
    fun infoScrollbar(index: Int, totalInfoRows: Int, infoScrollIndex: Int): ScrollbarLayout {
        val row = index / columns
        return ScrollbarLayout(
            trackTop = infoTop(row),
            trackBottom = cardBottom(row) - INFO_SCROLLBAR_INSET,
            itemCount = totalInfoRows,
            visibleItemCount = visibleInfoRows,
            scrollIndex = infoScrollIndex,
            minimumThumbHeight = INFO_MINIMUM_THUMB_HEIGHT,
        )
    }

    /** 游標是否位於可捲動的成員區，包含卡片之間的留白。 */
    fun isOverGrid(mouseX: Double, mouseY: Double): Boolean = visibleRows > 0 &&
        mouseX >= contentLeft &&
        mouseX < contentRight &&
        mouseY >= CARD_TOP &&
        mouseY < gridBottom

    /**
     * 找出游標所在的可見成員卡片。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 成員在完整清單中的順序；不在任何可見卡片上時為 null。
     */
    fun cardIndexAt(mouseX: Double, mouseY: Double): Int? {
        if (!isOverGrid(mouseX, mouseY)) return null
        if (mouseX < gridLeft || mouseX >= gridLeft + columns * cardWidth) return null
        val column = ((mouseX - gridLeft) / cardWidth).toInt()
        val row = visibleGridRows.firstOrNull { mouseY >= cardTop(it) && mouseY < cardBottom(it) } ?: return null
        return (row * columns + column).takeIf { it in 0 until playerCount }
    }

    /**
     * 判斷游標是否在指定卡片的資訊區內，用於以滾輪捲動該卡片的資訊。
     *
     * @param index 成員在完整清單中的順序。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標在這張卡片的資訊區內時為 true。
     */
    fun isOverInfo(index: Int, mouseX: Double, mouseY: Double): Boolean {
        val row = index / columns
        return cardIndexAt(mouseX, mouseY) == index && mouseY >= infoTop(row)
    }

    /**
     * 判斷游標是否落在指定卡片的資訊捲軸上；命中範圍比繪製範圍稍寬。
     *
     * @param index 成員在完整清單中的順序。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標在這張卡片的資訊捲軸上時為 true。
     */
    fun isOverInfoScrollbar(index: Int, mouseX: Double, mouseY: Double): Boolean {
        val left = infoScrollbarLeft(index)
        return isOverInfo(index, mouseX, mouseY) &&
            mouseX >= left - INFO_SCROLLBAR_HIT_PADDING &&
            mouseX < left + VerticalScrollbar.WIDTH + INFO_SCROLLBAR_HIT_PADDING
    }

    /**
     * 判斷游標是否落在成員卡片捲軸的命中範圍內；內容未超出可見範圍時回傳 false。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 需要捲動且游標位於命中範圍內時為 true。
     */
    fun isOverGridScrollbar(mouseX: Double, mouseY: Double): Boolean = scrollbar.maximumScroll > 0 &&
        mouseX >= scrollbarLeft - FormScreenLayout.SCROLLBAR_HIT_PADDING &&
        mouseX < scrollbarLeft + VerticalScrollbar.WIDTH + FormScreenLayout.SCROLLBAR_HIT_PADDING &&
        mouseY >= CARD_TOP &&
        mouseY < gridBottom

    internal companion object {
        /** 卡片 grid 的起始 Y。 */
        const val CARD_TOP: Int = 58

        /** 未放大時的單張卡片高度。 */
        const val CARD_HEIGHT: Int = 146

        /** 只有一列卡片時的最大放大倍數。 */
        const val MAX_CARD_SCALE: Float = 1.5f

        /** 未放大時，名稱下方第一行相對卡片頂端的偏移。 */
        const val INFO_OFFSET: Int = 111

        /** 資訊清單最後一行下方保留的高度。 */
        const val INFO_BOTTOM_PADDING: Int = 11

        /** 成員名稱頂端與下方第一行頂端的距離。 */
        const val NAME_GAP: Int = 15

        /** 名稱與資訊區所需的最小高度。 */
        private const val MINIMUM_CARD_HEIGHT: Int = CARD_HEIGHT - INFO_OFFSET + NAME_GAP

        /** 人物腳底與成員名稱頂端的距離。 */
        const val FIGURE_GAP: Int = 12

        /** 單張卡片的最大寬度。 */
        const val CARD_MAX_WIDTH: Int = 126

        /** 單張卡片的最小寬度，決定可容納的欄數。 */
        const val CARD_MIN_WIDTH: Int = 70

        /** 含間距的單列高度。 */
        const val ROW_HEIGHT: Int = 156

        /** 卡片底色右緣與卡片範圍右界的距離。 */
        const val CARD_RIGHT_INSET: Int = 4

        /** 卡片內資訊捲軸與卡片底色邊緣的距離。 */
        const val INFO_SCROLLBAR_INSET: Int = 2

        /** 卡片內資訊捲軸左右各加寬的命中範圍。 */
        const val INFO_SCROLLBAR_HIT_PADDING: Int = 2

        /** 卡片內資訊捲軸滑塊的最小高度。 */
        const val INFO_MINIMUM_THUMB_HEIGHT: Int = 6

        /** 卡片內資訊清單的單行高度。 */
        const val INFO_ROW_HEIGHT: Int = 12

        /** 底部按鈕列的格數。 */
        const val FOOTER_SLOTS: Int = 4
    }
}
