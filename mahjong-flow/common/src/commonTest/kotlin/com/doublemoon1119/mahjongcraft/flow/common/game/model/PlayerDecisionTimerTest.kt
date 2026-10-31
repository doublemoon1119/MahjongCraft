package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** [PlayerDecisionTimer] 的單元測試。 */
class PlayerDecisionTimerTest {
    /** 驗證基本思考時間尚未用完時不會消耗保留思考時間。 */
    @Test
    fun `test action time is consumed before reserve time`() {
        val timer = timer(startedAt = 1_000L)

        assertEquals(
            DecisionTimeStatus(
                baseRemaining = 3_000.milliseconds,
                reserveRemaining = 20_000.milliseconds,
                isTimedOut = false,
            ),
            timer.statusAt(3_000.milliseconds),
        )
    }

    /** 驗證超過 A 後只從 B 扣除超出的時間。 */
    @Test
    fun `test elapsed time beyond action time consumes reserve time`() {
        val timer = timer(startedAt = 1_000L)

        assertEquals(
            DecisionTimeStatus(
                baseRemaining = 0.milliseconds,
                reserveRemaining = 18_000.milliseconds,
                isTimedOut = false,
            ),
            timer.statusAt(8_000.milliseconds),
        )
    }

    /** 驗證基本思考時間與保留思考時間剛好耗盡時即視為逾時。 */
    @Test
    fun `test decision times out when action and reserve time are exhausted`() {
        val timer = timer(startedAt = 1_000L)

        val status = timer.statusAt(26_000.milliseconds)

        assertEquals(0.milliseconds, status.baseRemaining)
        assertEquals(0.milliseconds, status.reserveRemaining)
        assertTrue(status.isTimedOut)
    }

    /** 驗證零基本思考時間設定會從決策開始立即消耗保留思考時間。 */
    @Test
    fun `test zero action time immediately consumes reserve time`() {
        val timer = ActionTimeControl.Custom(baseSeconds = 0, reserveSeconds = 20).startDecisionTimer(
            playerId = Uuid.random(),
            remainingReserve = 20_000.milliseconds,
            startedAt = 1_000.milliseconds,
        )

        val status = timer.statusAt(2_500.milliseconds)

        assertEquals(0.milliseconds, status.baseRemaining)
        assertEquals(18_500.milliseconds, status.reserveRemaining)
        assertFalse(status.isTimedOut)
    }

    /** 驗證早於開始點的時間不會增加基本思考時間或消耗保留思考時間。 */
    @Test
    fun `test time before decision start is clamped to zero elapsed time`() {
        val timer = timer(startedAt = 1_000L)

        assertEquals(
            DecisionTimeStatus(
                baseRemaining = 5_000.milliseconds,
                reserveRemaining = 20_000.milliseconds,
                isTimedOut = false,
            ),
            timer.statusAt(500.milliseconds),
        )
    }

    /**
     * 驗證供 [ActionTimeControl.startDecisionTimer] 接續中斷決策的 `resumedBase`、使用中斷時剩餘的基本思考時間，而不是重新取得完整基本思考時間。
     *
     * 這是重連不會免費重置思考倒數的直接依據：新計時器的 `baseDuration` 必須是接續剩餘量，不能回到
     * [ActionTimeControl.baseSeconds] 定義的完整值。
     */
    @Test
    fun `test resumed base millis is used instead of a fresh base duration`() {
        val timer = ActionTimeControl.Custom(baseSeconds = 5, reserveSeconds = 20).startDecisionTimer(
            playerId = Uuid.random(),
            remainingReserve = 20_000.milliseconds,
            startedAt = 1_000.milliseconds,
            resumedBase = 1_200.milliseconds,
        )

        assertEquals(1_200.milliseconds, timer.baseDuration)
        assertEquals(
            DecisionTimeStatus(baseRemaining = 700.milliseconds, reserveRemaining = 20_000.milliseconds, isTimedOut = false),
            timer.statusAt(1_500.milliseconds),
        )
    }

    /** 驗證未指定 `resumedBase` 時仍取得 [ActionTimeControl.baseSeconds] 定義的完整基本思考時間。 */
    @Test
    fun `test omitted resumed base millis grants a fresh base duration`() {
        val timer = ActionTimeControl.Custom(baseSeconds = 5, reserveSeconds = 20).startDecisionTimer(
            playerId = Uuid.random(),
            remainingReserve = 20_000.milliseconds,
            startedAt = 1_000.milliseconds,
        )

        assertEquals(5_000.milliseconds, timer.baseDuration)
    }

    /** 驗證負數時間輸入會被拒絕。 */
    @Test
    fun `test negative timer values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerDecisionTimer(
                playerId = Uuid.random(),
                startedAt = (-1).milliseconds,
                baseDuration = 5_000.milliseconds,
                reserveAtStart = 20_000.milliseconds,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            timer(startedAt = 1_000L).statusAt((-1).milliseconds)
        }
    }

    /**
     * 建立使用五秒基本思考時間與二十秒保留思考時間的測試計時器。
     *
     * @param startedAt 計時器開始的單調時間毫秒數。
     * @return 使用指定開始時間建立的測試計時器。
     */
    private fun timer(startedAt: Long): PlayerDecisionTimer = ActionTimeControl.Custom(
        baseSeconds = 5,
        reserveSeconds = 20,
    ).startDecisionTimer(
        playerId = Uuid.random(),
        remainingReserve = 20_000.milliseconds,
        startedAt = startedAt.milliseconds,
    )
}
