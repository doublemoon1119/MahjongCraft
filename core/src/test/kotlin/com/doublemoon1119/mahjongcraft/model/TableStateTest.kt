package com.doublemoon1119.mahjongcraft.model

import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [TableState] 的基礎通用邏輯進行測試。
 * 僅依賴核心模型層，確保測試的純粹性，不依賴特定規則。
 */
class TableStateTest {

    /**
     * 測試用的最簡捨牌紀錄實作。
     */
    private class BaseEntry(tile: IdentifiedTile) : DiscardPile.DiscardEntry(tile)

    /**
     * 測試用的最簡牌河實作。
     */
    private class BaseDiscardPile : DiscardPile<BaseEntry> {
        private val _entries = mutableListOf<BaseEntry>()
        override val entries: List<BaseEntry> get() = _entries
        override fun discard(entry: BaseEntry) { _entries.add(entry) }
        override fun takeLast() { _entries.lastOrNull()?.isTaken = true }
    }

    /**
     * 測試用的動態規則狀態實作，模擬不同規則下的動態數據。
     */
    private class MockDynamicState : DynamicRuleState

    /**
     * 輔助方法：建立測試用的玩家物件。
     */
    private fun createMockPlayer(name: String, seat: Wind): MahjongPlayer {
        return MahjongPlayer(
            id = UUID.randomUUID(),
            name = name,
            initialSeat = seat,
            discardPile = BaseDiscardPile()
        )
    }

    /**
     * 驗證下家獲取邏輯是否正確，並確保支援動態人數（3人或4人）。
     */
    @Test
    fun `test next player logic supports dynamic player count`() {
        val p1 = createMockPlayer("Player 1", Wind.EAST)
        val p2 = createMockPlayer("Player 2", Wind.SOUTH)
        val p3 = createMockPlayer("Player 3", Wind.WEST)

        val table = TableState(
            players = listOf(p1, p2, p3),
            tileWall = TileWall(mutableListOf())
        )

        assertEquals(3, table.playerCount, "Should support 3 players")
        assertEquals(p2, table.getNextPlayer(p1), "Next player of P1 should be P2")
        assertEquals(p1, table.getNextPlayer(p3), "Next player of P3 should wrap back to P1")
    }

    /**
     * 驗證 TableState 能正確持有動態規則狀態介面。
     */
    @Test
    fun `test dynamic rule state assignment`() {
        val dynamicState = MockDynamicState()
        val table = TableState(
            players = emptyList(),
            tileWall = TileWall(mutableListOf()),
            dynamicRuleState = dynamicState
        )

        assertEquals(dynamicState, table.dynamicRuleState, "TableState should store and return the dynamic state")
    }
}