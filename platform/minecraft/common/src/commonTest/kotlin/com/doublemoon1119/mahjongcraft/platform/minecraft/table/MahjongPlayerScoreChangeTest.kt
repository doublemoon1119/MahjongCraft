package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證玩家資訊上短暫顯示的分數增減。 */
class MahjongPlayerScoreChangeTest {
    /** 只在顯示期間內顯示，結束時刻不含在內。 */
    @Test
    fun `a change is shown only during its window`() {
        val changes = listOf(MahjongPlayerScoreChange(seatIndex = 0, delta = 1000, startGameTime = 10L, endGameTime = 20L))

        assertNull(changes.shownDeltaFor(0, 9L))
        assertEquals(1000, changes.shownDeltaFor(0, 10L))
        assertEquals(1000, changes.shownDeltaFor(0, 19L))
        assertNull(changes.shownDeltaFor(0, 20L))
        assertNull(changes.shownDeltaFor(1, 15L))
    }

    /** 同一座位同時顯示多筆時加總；加總為 0 時不顯示。 */
    @Test
    fun `overlapping changes for a seat are summed`() {
        val changes = listOf(
            MahjongPlayerScoreChange(seatIndex = 0, delta = 1000, startGameTime = 0L, endGameTime = 30L),
            MahjongPlayerScoreChange(seatIndex = 0, delta = 500, startGameTime = 10L, endGameTime = 40L),
            MahjongPlayerScoreChange(seatIndex = 1, delta = 700, startGameTime = 0L, endGameTime = 30L),
            MahjongPlayerScoreChange(seatIndex = 1, delta = -700, startGameTime = 0L, endGameTime = 30L),
        )

        assertEquals(1500, changes.shownDeltaFor(0, 15L))
        assertNull(changes.shownDeltaFor(1, 15L))
    }

    /** 增減為 0 或顯示期間不為正時拒絕建立。 */
    @Test
    fun `invalid changes are rejected`() {
        assertFailsWith<IllegalArgumentException> { MahjongPlayerScoreChange(0, 0, 0L, 10L) }
        assertFailsWith<IllegalArgumentException> { MahjongPlayerScoreChange(0, 100, 10L, 10L) }
    }
}
