package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayDiscardDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證牌譜回放的最新捨牌與相同牌的判斷。 */
class HistoryReplayHighlightsTest {
    /** 最後打出、仍在牌河的牌發光。被鳴走、局已結束或還沒有人打牌時沒有。 */
    @Test
    fun `latest discard stays only while it is in the river`() {
        val river = listOf(discard(3), discard(7))
        assertEquals(expected = 7, actual = HistoryReplayHighlights.latestDiscard(listOf(3, 7), river, roundOver = false))
        assertNull(HistoryReplayHighlights.latestDiscard(listOf(3, 7), listOf(discard(3), discard(7, taken = true)), roundOver = false))
        assertNull(HistoryReplayHighlights.latestDiscard(listOf(3, 7), river, roundOver = true))
        assertNull(HistoryReplayHighlights.latestDiscard(emptyList(), emptyList(), roundOver = false))
    }

    /** 相同牌依標準牌面比較，不含指著的牌與看不到牌面的牌。 */
    @Test
    fun `matching compares canonical faces`() {
        val faces = mapOf(0 to "five", 1 to "five", 2 to "six", 3 to "five")
        val matches = HistoryReplayHighlights.matching(target = 0, candidates = setOf(0, 1, 2, 3, 4)) { faces[it] }
        assertEquals(expected = setOf(1, 3), actual = matches)
        assertEquals(expected = emptySet(), actual = HistoryReplayHighlights.matching(target = 4, candidates = setOf(0, 1)) { faces[it] })
    }

    /**
     * 建立牌河中的一張牌。
     *
     * @param tile 局內牌索引。
     * @param taken 是否已被鳴走。
     * @return 牌河中的牌。
     */
    private fun discard(tile: Int, taken: Boolean = false): HistoryReplayDiscardDto = HistoryReplayDiscardDto(tile = tile, isTaken = taken, markers = emptySet())
}
