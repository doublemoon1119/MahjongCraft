package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [PlayerDecisionTimer] 的單元測試。 */
class PlayerDecisionTimerTest {
    /** 驗證 A 尚未用完時不會消耗 B。 */
    @Test
    fun `test action time is consumed before reserve time`() {
        val timer = timer(startedAtMillis = 1_000L)

        assertEquals(
            DecisionTimeStatus(
                actionRemainingMillis = 3_000L,
                reserveRemainingMillis = 20_000L,
                isTimedOut = false,
            ),
            timer.statusAt(3_000L),
        )
    }

    /** 驗證超過 A 後只從 B 扣除超出的時間。 */
    @Test
    fun `test elapsed time beyond action time consumes reserve time`() {
        val timer = timer(startedAtMillis = 1_000L)

        assertEquals(
            DecisionTimeStatus(
                actionRemainingMillis = 0L,
                reserveRemainingMillis = 18_000L,
                isTimedOut = false,
            ),
            timer.statusAt(8_000L),
        )
    }

    /** 驗證 A 與 B 剛好耗盡時即視為逾時。 */
    @Test
    fun `test decision times out when action and reserve time are exhausted`() {
        val timer = timer(startedAtMillis = 1_000L)

        val status = timer.statusAt(26_000L)

        assertEquals(0L, status.actionRemainingMillis)
        assertEquals(0L, status.reserveRemainingMillis)
        assertTrue(status.isTimedOut)
    }

    /** 驗證零 A 設定會從決策開始立即消耗 B。 */
    @Test
    fun `test zero action time immediately consumes reserve time`() {
        val timer = ActionTimeControl.Custom(actionSeconds = 0, reserveSeconds = 20).startDecisionTimer(
            playerId = Uuid.random(),
            remainingReserveMillis = 20_000L,
            startedAtMillis = 1_000L,
        )

        val status = timer.statusAt(2_500L)

        assertEquals(0L, status.actionRemainingMillis)
        assertEquals(18_500L, status.reserveRemainingMillis)
        assertFalse(status.isTimedOut)
    }

    /** 驗證早於開始點的時間不會增加 A 或消耗 B。 */
    @Test
    fun `test time before decision start is clamped to zero elapsed time`() {
        val timer = timer(startedAtMillis = 1_000L)

        assertEquals(
            DecisionTimeStatus(
                actionRemainingMillis = 5_000L,
                reserveRemainingMillis = 20_000L,
                isTimedOut = false,
            ),
            timer.statusAt(500L),
        )
    }

    /** 驗證負數時間輸入會被拒絕。 */
    @Test
    fun `test negative timer values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerDecisionTimer(
                playerId = Uuid.random(),
                startedAtMillis = -1L,
                actionDurationMillis = 5_000L,
                reserveAtStartMillis = 20_000L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            timer(startedAtMillis = 1_000L).statusAt(-1L)
        }
    }

    /** 建立使用五秒 A 與二十秒 B 的測試計時器。 */
    private fun timer(startedAtMillis: Long): PlayerDecisionTimer = ActionTimeControl.Custom(
        actionSeconds = 5,
        reserveSeconds = 20,
    ).startDecisionTimer(
        playerId = Uuid.random(),
        remainingReserveMillis = 20_000L,
        startedAtMillis = startedAtMillis,
    )
}
