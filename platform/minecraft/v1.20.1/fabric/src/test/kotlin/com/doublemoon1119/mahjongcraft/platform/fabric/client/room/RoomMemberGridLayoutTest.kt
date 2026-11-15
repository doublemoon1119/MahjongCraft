package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證成員卡片 grid 的換欄、捲動與命中判定。 */
class RoomMemberGridLayoutTest {
    /** 玩家少於一列可容納的欄數時，欄數等於玩家數。 */
    @Test
    fun `uses one column per player when they all fit on one row`() {
        val grid = grid(width = 640, playerCount = 3)

        assertEquals(3, grid.columns)
        assertEquals(1, grid.rows)
    }

    /** 玩家數超過內容寬度時換列，欄數由內容寬度決定；內容寬度最多 456 並置中。 */
    @Test
    fun `wraps to more rows once the content width is used up`() {
        val grid = grid(width = 640, playerCount = 20)

        assertEquals(456, grid.contentRight - grid.contentLeft)
        assertEquals((640 - 456) / 2, grid.contentLeft)
        assertEquals(456 / RoomMemberGridLayout.CARD_MIN_WIDTH, grid.columns)
        assertEquals((20 + grid.columns - 1) / grid.columns, grid.rows)
    }

    /** 空清單仍算作一位玩家，避免算出零欄造成除以零。 */
    @Test
    fun `treats an empty roster as a single player`() {
        val grid = grid(width = 640, playerCount = 0)

        assertEquals(1, grid.columns)
        assertEquals(1, grid.rows)
    }

    /** 極窄視窗至少保留一欄。 */
    @Test
    fun `always keeps at least one column`() {
        assertEquals(1, grid(width = 1, playerCount = 8).columns)
    }

    /** 卡片寬度不超過上限，欄多時由可用寬度平分。 */
    @Test
    fun `caps the card width and shares the remaining width`() {
        assertEquals(RoomMemberGridLayout.CARD_MAX_WIDTH, grid(width = 640, playerCount = 1).cardWidth)

        val crowded = grid(width = 640, playerCount = 20)
        assertEquals(456 / crowded.columns, crowded.cardWidth)
    }

    /** 卡片整組在內容寬度內置中，同一列的卡片左右相接。 */
    @Test
    fun `centres the cards inside the content`() {
        val grid = grid(width = 640, playerCount = 4)

        assertEquals(grid.cardLeft(0) + grid.cardWidth, grid.cardLeft(1))
        assertTrue(abs(grid.cardLeft(0) - grid.contentLeft - (grid.contentRight - grid.cardLeft(3) - grid.cardWidth)) <= 1)
    }

    /** 底部四格橫跨內容寬度，狀態列在卡片區與底部按鈕列之間。 */
    @Test
    fun `footer spans the content with the status line above it`() {
        val grid = grid(width = 640, height = 300, playerCount = 4)

        assertEquals(grid.contentLeft, grid.footer.buttonX(0))
        assertEquals(grid.contentRight, grid.footer.buttonX(3) + grid.footer.buttonWidth(3))
        assertTrue(grid.gridBottom < grid.statusY)
        assertTrue(grid.statusY < grid.footerY)
        assertEquals(300 - 8 - 20, grid.footerY)
    }

    /** 兩列以上時卡片座標依捲動位置位移，資訊區在卡片內固定偏移處。 */
    @Test
    fun `offsets card coordinates by the scroll position`() {
        val grid = grid(width = 640, playerCount = 20, scrollIndex = 2)

        assertEquals(RoomMemberGridLayout.CARD_TOP, grid.cardTop(2))
        assertEquals(RoomMemberGridLayout.CARD_TOP + RoomMemberGridLayout.ROW_HEIGHT, grid.cardTop(3))
        assertEquals(grid.cardTop(2) + RoomMemberGridLayout.CARD_HEIGHT, grid.cardBottom(2))
        assertEquals(grid.cardTop(2) + RoomMemberGridLayout.INFO_OFFSET, grid.infoTop(2))
    }

    /** 只有一列且可用高度足夠時，卡片放大到 1.5 倍並在可用高度內上下置中。 */
    @Test
    fun `scales a single row up to the cap and centres it`() {
        val grid = grid(width = 960, height = 516, playerCount = 4)

        assertEquals(RoomMemberGridLayout.MAX_CARD_SCALE, grid.cardScale)
        assertEquals(219, grid.cardHeight)
        assertTrue(abs((grid.cardTop(0) - RoomMemberGridLayout.CARD_TOP) - (grid.gridBottom - grid.cardBottom(0))) <= 1)
    }

    /** 可用高度介於 1 倍與 1.5 倍之間時，卡片依比例填滿可用高度。 */
    @Test
    fun `scales a single row in proportion to a medium window`() {
        val grid = grid(width = 640, height = 300, playerCount = 4)

        assertTrue(grid.cardScale > 1f && grid.cardScale < RoomMemberGridLayout.MAX_CARD_SCALE)
        assertEquals(grid.gridBottom - RoomMemberGridLayout.CARD_TOP, grid.cardHeight)
        assertEquals(RoomMemberGridLayout.CARD_TOP, grid.cardTop(0))
    }

    /** 可用高度不足 1 倍時不放大，卡片從頂端開始排。 */
    @Test
    fun `does not scale a single row in a small window`() {
        val grid = grid(width = 640, height = 240, playerCount = 4)

        assertEquals(1f, grid.cardScale)
        assertEquals(RoomMemberGridLayout.CARD_HEIGHT, grid.cardHeight)
        assertEquals(RoomMemberGridLayout.CARD_TOP, grid.cardTop(0))
    }

    /** 兩列以上維持 1 倍高度，從頂端開始排並以捲軸捲動。 */
    @Test
    fun `does not scale two or more rows`() {
        val grid = grid(width = 640, height = 2000, playerCount = 20)

        assertEquals(1f, grid.cardScale)
        assertEquals(RoomMemberGridLayout.CARD_HEIGHT, grid.cardHeight)
        assertEquals(RoomMemberGridLayout.CARD_TOP, grid.cardTop(0))
    }

    /** 未放大時，名稱、狀態列、資訊區與人物腳底維持原本相對卡片頂端的位置。 */
    @Test
    fun `keeps the unscaled card positions`() {
        val grid = grid(width = 640, height = 240, playerCount = 4)
        val top = grid.cardTop(0)

        assertEquals(top + RoomMemberGridLayout.INFO_OFFSET, grid.statusTop(0))
        assertEquals(top + RoomMemberGridLayout.INFO_OFFSET, grid.infoTop(0))
        assertEquals(top + 96, grid.nameTop(grid.statusTop(0)))
        assertEquals(top + 84, grid.figureFeetY(grid.statusTop(0)))
        assertEquals(2, grid.visibleInfoRows)
    }

    /** 放大後，狀態列與底部的距離不變，資訊區行數增加並從底部往上延伸。 */
    @Test
    fun `measures the card positions from the bottom when scaled`() {
        val small = grid(width = 960, height = 240, playerCount = 4)
        val large = grid(width = 960, height = 516, playerCount = 4)

        assertEquals(small.cardBottom(0) - small.statusTop(0), large.cardBottom(0) - large.statusTop(0))
        assertEquals(3, large.visibleInfoRows)
        assertTrue(large.cardBottom(0) - large.infoTop(0) >= large.visibleInfoRows * RoomMemberGridLayout.INFO_ROW_HEIGHT)
        assertTrue(large.infoTop(0) < large.statusTop(0))
        assertTrue(large.figureFeetY(large.infoTop(0)) > large.cardTop(0))
    }

    /** 可見列範圍從捲動位置起算，且不超過總列數。 */
    @Test
    fun `clamps the visible rows to the row count`() {
        val tall = grid(width = 640, height = 2000, playerCount = 3)
        assertEquals(0 until 1, tall.visibleGridRows)

        val scrolled = grid(width = 640, height = 480, playerCount = 40, scrollIndex = 1)
        assertEquals(1, scrolled.visibleGridRows.first)
        assertTrue(scrolled.visibleGridRows.last < scrolled.rows)
    }

    /** 列數未超出可見範圍時 grid scrollbar 不可拖曳。 */
    @Test
    fun `does not hit test the grid scrollbar when everything fits`() {
        val grid = grid(width = 640, height = 2000, playerCount = 3)

        assertFalse(grid.isOverGridScrollbar(grid.scrollbarLeft + 1.0, RoomMemberGridLayout.CARD_TOP.toDouble()))
    }

    /** 列數超出可見範圍時，命中判定涵蓋整段軌道。 */
    @Test
    fun `hit tests the grid scrollbar over the whole track`() {
        val grid = grid(width = 640, height = 300, playerCount = 40)
        val insideX = grid.scrollbarLeft + 1.0

        assertTrue(grid.scrollbarLeft > grid.contentRight)
        assertTrue(grid.rows > grid.visibleRows)
        assertTrue(grid.isOverGridScrollbar(insideX, RoomMemberGridLayout.CARD_TOP.toDouble()))
        assertTrue(grid.isOverGridScrollbar(insideX, grid.gridBottom - 1.0))
        assertFalse(grid.isOverGridScrollbar(insideX, (RoomMemberGridLayout.CARD_TOP - 1).toDouble()))
        assertFalse(grid.isOverGridScrollbar(0.0, RoomMemberGridLayout.CARD_TOP.toDouble()))
    }

    /** 資訊清單未超出卡片高度時不可捲動。 */
    @Test
    fun `reports no info scroll when the rows fit inside the card`() {
        val grid = grid(width = 640, playerCount = 4)

        assertEquals(0, grid.maximumInfoScroll(grid.visibleInfoRows))
        assertEquals(5, grid.maximumInfoScroll(grid.visibleInfoRows + 5))
    }

    /** 資訊捲軸畫在各自卡片內的右緣，只有游標在那張卡片的資訊區時才算命中。 */
    @Test
    fun `info scrollbars sit inside each card`() {
        val grid = grid(width = 640, playerCount = 4)
        val infoY = grid.infoTop(0) + 2.0

        (0 until 4).forEach { index ->
            val left = grid.infoScrollbarLeft(index)
            assertTrue(left > grid.cardLeft(index))
            assertTrue(left < grid.cardLeft(index) + grid.cardWidth)
            assertTrue(grid.isOverInfoScrollbar(index, left + 1.0, infoY))
            assertTrue(grid.isOverInfo(index, grid.cardLeft(index) + 4.0, infoY))
        }
        assertFalse(grid.isOverInfoScrollbar(1, grid.infoScrollbarLeft(0) + 1.0, infoY))
        assertFalse(grid.isOverInfo(0, grid.cardLeft(0) + 4.0, grid.infoTop(0) - 1.0))
        assertEquals(2, grid.cardIndexAt(grid.cardLeft(2) + 1.0, infoY))
        assertNull(grid.cardIndexAt(grid.contentLeft - 1.0, infoY))
        assertEquals(grid.infoTop(0), grid.infoScrollbar(3, 10, 0).trackTop)
    }

    /**
     * 建立測試用版面；未指定的輸入使用不影響該測試的預設值。
     *
     * @param width 視窗寬度。
     * @param height 視窗高度。
     * @param playerCount 成員數量。
     * @param scrollIndex 捲動位置索引。
     * @return 與輸入尺寸及成員數相符的網格版面。
     */
    private fun grid(
        width: Int,
        height: Int = 480,
        playerCount: Int,
        scrollIndex: Int = 0,
    ) = RoomMemberGridLayout(
        windowWidth = width,
        windowHeight = height,
        playerCount = playerCount,
        scrollIndex = scrollIndex,
    )
}
