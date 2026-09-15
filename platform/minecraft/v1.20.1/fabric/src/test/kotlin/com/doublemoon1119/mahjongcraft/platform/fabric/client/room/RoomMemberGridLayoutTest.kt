package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

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

    /** 玩家數超過可用寬度時換列，欄數由可用寬度決定。 */
    @Test
    fun `wraps to more rows once the width is used up`() {
        val grid = grid(width = 640, playerCount = 20)

        val availableWidth = 640 - RoomMemberGridLayout.GRID_MARGIN * 2
        assertEquals(availableWidth / RoomMemberGridLayout.CARD_MIN_WIDTH, grid.columns)
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
        val availableWidth = 640 - RoomMemberGridLayout.GRID_MARGIN * 2
        assertEquals(availableWidth / crowded.columns, crowded.cardWidth)
    }

    /** 卡片座標依捲動位置位移，資訊區在卡片內固定偏移處。 */
    @Test
    fun `offsets card coordinates by the scroll position`() {
        val grid = grid(width = 640, playerCount = 20, scrollIndex = 2)

        assertEquals(RoomMemberGridLayout.CARD_TOP, grid.cardTop(2))
        assertEquals(RoomMemberGridLayout.CARD_TOP + RoomMemberGridLayout.ROW_HEIGHT, grid.cardTop(3))
        assertEquals(grid.cardTop(2) + RoomMemberGridLayout.CARD_HEIGHT, grid.cardBottom(2))
        assertEquals(grid.cardTop(2) + RoomMemberGridLayout.INFO_OFFSET, grid.infoTop(2))
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

        assertFalse(grid.isOverGridScrollbar((640 - 10).toDouble(), RoomMemberGridLayout.CARD_TOP.toDouble()))
    }

    /** 列數超出可見範圍時，命中判定涵蓋整段軌道。 */
    @Test
    fun `hit tests the grid scrollbar over the whole track`() {
        val grid = grid(width = 640, height = 300, playerCount = 40)
        val insideX = (640 - 10).toDouble()

        assertTrue(grid.rows > grid.visibleRows)
        assertTrue(grid.isOverGridScrollbar(insideX, RoomMemberGridLayout.CARD_TOP.toDouble()))
        assertTrue(grid.isOverGridScrollbar(insideX, grid.gridBottom.toDouble()))
        assertFalse(grid.isOverGridScrollbar(insideX, (RoomMemberGridLayout.CARD_TOP - 1).toDouble()))
        assertFalse(grid.isOverGridScrollbar(0.0, RoomMemberGridLayout.CARD_TOP.toDouble()))
    }

    /** 資訊清單未超出卡片高度時不可捲動，也沒有可拖曳的列。 */
    @Test
    fun `reports no info scroll when the rows fit inside the card`() {
        val grid = grid(width = 640, playerCount = 4)

        assertEquals(0, grid.maximumInfoScroll(grid.visibleInfoRows))
        assertNull(grid.infoScrollbarRowAt((640 - 10).toDouble(), grid.infoTop(0).toDouble(), grid.visibleInfoRows))
    }

    /** 資訊清單超出卡片高度時，命中判定回傳游標所在的那一列。 */
    @Test
    fun `resolves the info scrollbar row under the cursor`() {
        val grid = grid(width = 640, playerCount = 4)
        val totalInfoRows = grid.visibleInfoRows + 5
        val insideX = (640 - 10).toDouble()

        assertEquals(5, grid.maximumInfoScroll(totalInfoRows))
        assertEquals(0, grid.infoScrollbarRowAt(insideX, grid.infoTop(0).toDouble(), totalInfoRows))
        assertNull(grid.infoScrollbarRowAt(insideX, (grid.infoTop(0) - 1).toDouble(), totalInfoRows))
        assertNull(grid.infoScrollbarRowAt(0.0, grid.infoTop(0).toDouble(), totalInfoRows))
    }

    /** 建立測試用版面；未指定的輸入使用不影響該測試的預設值。 */
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
