package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.platform.fabric.client.time.PauseAwareClientMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultKindDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 最終決策提交鎖、亂序 ACK 與等不到 ACK 時的單元測試。 */
class DecisionSubmissionTrackerTest {
    /** 測試控制的客戶端時間，單位為毫秒。 */
    private var now = 0L

    /** 測試用的權威決策更新序號。 */
    private var updateSequence = 0L

    /** 回傳 [now] 的測試時鐘。 */
    private val clock = object : MonotonicClock {
        override fun nowMillis(): Long = now
    }

    /** 受測的提交追蹤器。 */
    private val tracker = DecisionSubmissionTracker(clock)

    /** 接受與 stale 都維持鎖定，直到權威 prompt 消失。 */
    @Test
    fun `test accepted and stale acknowledgements wait for authoritative update`() {
        PlayerDecisionSubmissionResultKindDto.entries
            .filter { it != PlayerDecisionSubmissionResultKindDto.REJECTED }
            .forEach { result ->
                val tracker = DecisionSubmissionTracker(clock)
                tracker.begin("game", "decision", "submission")

                assertEquals(AcknowledgementEffect.KEPT_LOCKED, tracker.acknowledge(ack(result)))
                assertTrue(tracker.isPending("decision"))

                tracker.applyAuthoritativeDecision(null, updateSequence)
                assertFalse(tracker.isPending())
            }
    }

    /** 拒絕會解除同一 decision 的提交鎖並允許重試。 */
    @Test
    fun `test rejected acknowledgement unlocks current decision`() {
        assertTrue(tracker.begin("game", "decision", "submission"))

        assertEquals(
            AcknowledgementEffect.UNLOCKED,
            tracker.acknowledge(ack(PlayerDecisionSubmissionResultKindDto.REJECTED)),
        )
        assertTrue(tracker.begin("game", "decision", "retry"))
    }

    /** 晚到 ACK 不得解除新提交，重複 begin 也不得覆蓋目前識別。 */
    @Test
    fun `test late acknowledgement and duplicate submission are ignored`() {
        assertTrue(tracker.begin("game", "new-decision", "new-submission"))
        assertFalse(tracker.begin("game", "other", "other-submission"))

        val late = PlayerDecisionSubmissionResultDto(
            gameId = "game",
            decisionKey = "old-decision",
            submissionId = "old-submission",
            result = PlayerDecisionSubmissionResultKindDto.REJECTED,
        )
        assertEquals(AcknowledgementEffect.IGNORED, tracker.acknowledge(late))
        assertTrue(tracker.isPending("new-decision"))
    }

    /** 同 key 的 timer refresh 不解鎖，換 key 與斷線清除則必須解鎖。 */
    @Test
    fun `test authoritative decision lifecycle clears only obsolete submission`() {
        tracker.begin("game", "decision", "submission")

        tracker.applyAuthoritativeDecision("decision", updateSequence)
        assertTrue(tracker.isPending())

        tracker.applyAuthoritativeDecision("next-decision", updateSequence)
        assertFalse(tracker.isPending())

        tracker.begin("game", "next-decision", "next-submission")
        tracker.clear()
        assertFalse(tracker.isPending())
    }

    /** 提交被丟棄、伺服器仍每秒重送同一決策時，等滿逾時就解除鎖定讓玩家重新選擇。 */
    @Test
    fun `test dropped submission unlocks after the timeout while the decision is still offered`() {
        tracker.begin("game", "decision", "submission")

        runHeartbeats(untilMillis = DecisionSubmissionTracker.CONFIRMATION_TIMEOUT.inWholeMilliseconds - 1)
        assertEquals(TimeoutEffect.NONE, tracker.checkTimeout())
        runHeartbeats(untilMillis = DecisionSubmissionTracker.CONFIRMATION_TIMEOUT.inWholeMilliseconds)

        assertEquals(TimeoutEffect.UNLOCKED, tracker.checkTimeout())
        assertFalse(tracker.isPending())
        assertTrue(tracker.begin("game", "decision", "retry"))
    }

    /** 已收到接受的 ACK、只是決策結束的更新還沒到時，不會因逾時解除鎖定。 */
    @Test
    fun `test acknowledged submission never times out`() {
        tracker.begin("game", "decision", "submission")
        tracker.acknowledge(ack(PlayerDecisionSubmissionResultKindDto.ACCEPTED))

        runHeartbeats(untilMillis = LONG_WAIT_MILLIS)

        assertEquals(TimeoutEffect.NONE, tracker.checkTimeout())
        assertTrue(tracker.isPending("decision"))
        tracker.applyAuthoritativeDecision(null, updateSequence)
        assertFalse(tracker.isPending())
    }

    /** 接受後若合法動作集合改變，新的權威決策鍵仍能解除舊提交；晚到的舊確認不影響新提交。 */
    @Test
    fun `test accepted submission unlocks when authoritative candidate set changes`() {
        tracker.begin("game", "old-candidates", "submission")
        assertEquals(
            AcknowledgementEffect.KEPT_LOCKED,
            tracker.acknowledge(ack(PlayerDecisionSubmissionResultKindDto.ACCEPTED, "old-candidates", "submission")),
        )

        tracker.applyAuthoritativeDecision("new-candidates", updateSequence)
        assertFalse(tracker.isPending())

        assertTrue(tracker.begin("game", "new-candidates", "new-submission"))
        assertEquals(
            AcknowledgementEffect.IGNORED,
            tracker.acknowledge(ack(PlayerDecisionSubmissionResultKindDto.REJECTED, "old-candidates", "submission")),
        )
        assertTrue(tracker.isPending("new-candidates"))
    }

    /** 送出後完全收不到權威更新時，先回報一次正在重新同步，再等重新同步期限後解除鎖定。 */
    @Test
    fun `test silent server reports resyncing once and then unlocks within the limit`() {
        tracker.begin("game", "decision", "submission")

        now = DecisionSubmissionTracker.CONFIRMATION_TIMEOUT.inWholeMilliseconds
        assertEquals(TimeoutEffect.RESYNCING, tracker.checkTimeout())
        assertEquals(TimeoutEffect.NONE, tracker.checkTimeout())
        now += DecisionSubmissionTracker.RESYNC_LIMIT.inWholeMilliseconds - 1
        assertEquals(TimeoutEffect.NONE, tracker.checkTimeout())
        now += 1

        assertEquals(TimeoutEffect.UNLOCKED, tracker.checkTimeout())
        assertFalse(tracker.isPending())
    }

    /** 只在送出後不久收到過更新、之後就沒有更新時，不視為伺服器仍在等待。 */
    @Test
    fun `test only a recent update proves the server is still waiting`() {
        tracker.begin("game", "decision", "submission")
        tracker.applyAuthoritativeDecision("decision", updateSequence)
        now = HEARTBEAT_MILLIS
        updateSequence += 1
        tracker.applyAuthoritativeDecision("decision", updateSequence)

        now = DecisionSubmissionTracker.CONFIRMATION_TIMEOUT.inWholeMilliseconds

        assertEquals(TimeoutEffect.RESYNCING, tracker.checkTimeout())
        assertTrue(tracker.isPending())
    }

    /** 逾時解除後重新送出，舊提交晚到的 ACK 不影響新提交，新提交的逾時從它自己送出時開始計算。 */
    @Test
    fun `test late acknowledgement of a timed out submission does not affect the retry`() {
        tracker.begin("game", "decision", "submission")
        runHeartbeats(untilMillis = DecisionSubmissionTracker.CONFIRMATION_TIMEOUT.inWholeMilliseconds)
        assertEquals(TimeoutEffect.UNLOCKED, tracker.checkTimeout())
        tracker.begin("game", "decision", "retry")
        val retryStartedAt = now

        assertEquals(AcknowledgementEffect.IGNORED, tracker.acknowledge(ack(PlayerDecisionSubmissionResultKindDto.REJECTED)))
        runHeartbeats(untilMillis = retryStartedAt + DecisionSubmissionTracker.CONFIRMATION_TIMEOUT.inWholeMilliseconds - 1)

        assertEquals(TimeoutEffect.NONE, tracker.checkTimeout())
        assertTrue(tracker.isPending("decision"))
    }

    /** 單人遊戲暫停期間時鐘不前進，等再久也不會因逾時解除鎖定或提示重新同步。 */
    @Test
    fun `test pausing the game never times out a submission`() {
        val clock = PauseAwareClientMonotonicClock()
        val pausedTracker = DecisionSubmissionTracker(clock)
        clock.onTick(paused = false, nowNanos = 0L)
        pausedTracker.begin("game", "decision", "submission")

        var nanos = 0L
        repeat(LONG_WAIT_MILLIS.toInt() / TICK_MILLIS.toInt()) {
            nanos += TICK_MILLIS * NANOS_PER_MILLI
            clock.onTick(paused = true, nowNanos = nanos)
            assertEquals(TimeoutEffect.NONE, pausedTracker.checkTimeout())
        }

        assertTrue(pausedTracker.isPending("decision"))
    }

    /**
     * 模擬伺服器每秒重送同一決策，客戶端每個 tick 核對一次，直到指定時間。
     *
     * @param untilMillis 模擬結束的客戶端時間。
     */
    private fun runHeartbeats(untilMillis: Long) {
        while (now < untilMillis) {
            now = minOf(now + TICK_MILLIS, untilMillis)
            if (now % HEARTBEAT_MILLIS == 0L) updateSequence += 1
            tracker.applyAuthoritativeDecision("decision", updateSequence)
        }
    }

    /**
     * 建立目前提交的 ACK。
     *
     * @param result 權威回覆結果。
     * @param decisionKey 回覆所屬的決策識別碼。
     * @param submissionId 回覆所屬的提交識別碼。
     * @return 使用測試對局、決策與提交識別的 ACK。
     */
    private fun ack(
        result: PlayerDecisionSubmissionResultKindDto,
        decisionKey: String = "decision",
        submissionId: String = "submission",
    ) = PlayerDecisionSubmissionResultDto(
        gameId = "game",
        decisionKey = decisionKey,
        submissionId = submissionId,
        result = result,
    )

    private companion object {
        /** 客戶端 tick 間隔。 */
        const val TICK_MILLIS = 50L

        /** 伺服器重送決策的間隔。 */
        const val HEARTBEAT_MILLIS = 1_000L

        /** 遠超過逾時與重新同步期限的等待時間。 */
        const val LONG_WAIT_MILLIS = 60_000L

        /** 每毫秒的奈秒數。 */
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
