package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [MahjongDiceRollTimeline] 的連續時間計算測試。 */
class MahjongDiceRollTimelineTest {
    /** 確認多輪骰子動畫會依序開始且彼此不重疊。 */
    @Test
    fun `segments start after initial delay and never overlap`() {
        val segments = MahjongDiceRollTimeline.segments(
            diceCounts = listOf(2, 3, 2),
            initialDelayTicks = 7,
        )

        assertEquals(7, segments[0].startOffsetTicks)
        assertEquals(
            7 + MahjongDiceTableLayout.totalAnimationTicks(2),
            segments[0].endOffsetTicks,
        )
        assertEquals(segments[0].endOffsetTicks, segments[1].startOffsetTicks)
        assertEquals(segments[1].endOffsetTicks, segments[2].startOffsetTicks)
        assertEquals(
            segments[2].endOffsetTicks,
            MahjongDiceRollTimeline.totalTicks(listOf(2, 3, 2), initialDelayTicks = 7),
        )
    }

    /** 確認沒有骰子時仍保留初始延遲。 */
    @Test
    fun `empty timeline keeps initial delay`() {
        assertEquals(0, MahjongDiceRollTimeline.segments(emptyList(), initialDelayTicks = 5).size)
        assertEquals(5, MahjongDiceRollTimeline.totalTicks(emptyList(), initialDelayTicks = 5))
    }

    /** 確認負的初始延遲會被拒絕。 */
    @Test
    fun `negative initial delay is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MahjongDiceRollTimeline.segments(listOf(2), initialDelayTicks = -1)
        }
    }

    /** 確認不支援的骰子數量會被拒絕。 */
    @Test
    fun `unsupported dice count is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MahjongDiceRollTimeline.segments(listOf(1))
        }
    }
}
