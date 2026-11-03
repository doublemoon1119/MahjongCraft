package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 空支撐的唯讀占用與入座前清理，不依賴原生世界。 */
class SeatSupportCleanupTest {
    /** 查詢可辨識尚未清除的空支撐，且不修改任何支撐。 */
    @Test
    fun `an empty leftover support does not occupy the seat or change during inspection`() {
        val support = Support("empty")
        assertFalse(SeatSupportCleanup.isOccupied(listOf(support)) { it.passengers > 0 })
        assertFalse(support.removed)
    }

    /** 任一支撐有乘客都保留占用，包括與空支撐混在一起的情況。 */
    @Test
    fun `a passenger keeps the seat occupied even with other empty supports`() {
        val supports = listOf(Support("empty"), Support("occupied", passengers = 1))
        assertTrue(SeatSupportCleanup.isOccupied(supports) { it.passengers > 0 })
        assertTrue(supports.none { it.removed })
    }

    /** 入座前可移除全部空支撐，不必等它們的下一次更新。 */
    @Test
    fun `empty leftover supports are cleared before the seat is claimed`() {
        val supports = listOf(Support("first"), Support("second"))
        val removed = mutableListOf<String>()
        assertTrue(
            SeatSupportCleanup.clearEmpty(
                supports = supports,
                hasPassengers = { it.passengers > 0 },
                discard = {
                    it.removed = true
                    removed += it.id
                },
            ),
        )
        assertEquals(listOf("first", "second"), removed)
        assertTrue(supports.all { it.removed })
    }

    /** 看見乘客即拒絕入座，不刪除乘客或同批支撐。 */
    @Test
    fun `occupied support prevents all cleanup and remains available to its original passenger`() {
        val supports = listOf(Support("empty"), Support("occupied", passengers = 1))
        assertFalse(
            SeatSupportCleanup.clearEmpty(
                supports = supports,
                hasPassengers = { it.passengers > 0 },
                discard = { it.removed = true },
            ),
        )
        assertTrue(supports.none { it.removed })
        assertEquals(1, supports.last().passengers)
    }

    /** 清理觸發的回呼若讓下一個支撐有乘客，立即保留該支撐並停止。 */
    @Test
    fun `a support occupied during cleanup is rechecked and never discarded`() {
        val first = Support("first")
        val second = Support("second")
        assertFalse(
            SeatSupportCleanup.clearEmpty(
                supports = listOf(first, second),
                hasPassengers = { it.passengers > 0 },
                discard = {
                    it.removed = true
                    second.passengers = 1
                },
            ),
        )
        assertTrue(first.removed)
        assertFalse(second.removed)
        assertEquals(1, second.passengers)
    }

    /** 沒有殘留支撐時，唯讀查詢與入座準備都維持可用。 */
    @Test
    fun `a seat without supports needs no cleanup`() {
        assertFalse(SeatSupportCleanup.isOccupied(emptyList<Support>()) { it.passengers > 0 })
        var removals = 0
        assertTrue(
            SeatSupportCleanup.clearEmpty(
                supports = emptyList<Support>(),
                hasPassengers = { it.passengers > 0 },
                discard = { removals++ },
            ),
        )
        assertEquals(0, removals)
    }

    /**
     * 可觀察是否誤刪乘客的最小支撐。
     *
     * @property id 支撐識別。
     * @property passengers 目前乘客數。
     * @property removed 是否已被清理。
     */
    private class Support(
        val id: String,
        var passengers: Int = 0,
        var removed: Boolean = false,
    )
}
