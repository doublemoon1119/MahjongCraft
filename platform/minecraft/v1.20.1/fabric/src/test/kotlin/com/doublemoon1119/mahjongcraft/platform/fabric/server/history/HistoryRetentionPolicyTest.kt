package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** [HistoryRetentionPolicy] 純函式評估與確定性排序測試。 */
class HistoryRetentionPolicyTest {
    /** 零數量與零天數限制仍應接受正值磁碟上限。 */
    @Test
    fun `test zero count and day limits are unlimited`() {
        val policy = HistoryRetentionPolicy(
            includeInterruptedMatches = false,
            maxMatches = 0,
            retentionDuration = Duration.ZERO,
            maxDiskBytes = 1,
        )
        val candidates = listOf(candidate("old", endedAt = 0), candidate("new", endedAt = 1))

        val plan = evaluateHistoryCleanup(candidates, emptySet(), policy, instant(2))

        assertTrue(plan.removals.isEmpty(), "Unlimited count and age should remove nothing")
        assertEquals(listOf("old", "new"), plan.remaining.map { it.matchId })
    }

    /** 保留期限應在精確邊界過期，未來資料不應過期。 */
    @Test
    fun `test retention expiry boundary and future candidate`() {
        val policy = policy(retentionDuration = 1.days)
        val now = instant(2.days.inWholeMilliseconds)
        val boundary = candidate("boundary", endedAt = 1.days.inWholeMilliseconds)
        val future = candidate("future", endedAt = 3.days.inWholeMilliseconds)

        val plan = evaluateHistoryCleanup(listOf(boundary, future), emptySet(), policy, now)

        assertEquals(HistoryCleanupReason.EXPIRED, plan.removals["boundary"])
        assertFalse("future" in plan.removals)
        assertEquals(listOf("future"), plan.remaining.map { it.matchId })
    }

    /** 無限保留期限應保留極早資料而不發生 duration 溢位。 */
    @Test
    fun `test extreme retention duration does not expire`() {
        val policy = policy(retentionDuration = Duration.INFINITE)

        val plan = evaluateHistoryCleanup(
            listOf(candidate("ancient", endedAt = 0)),
            emptySet(),
            policy,
            instant(2.days.inWholeMilliseconds),
        )

        assertTrue(plan.removals.isEmpty(), "Infinite retention should not expire candidates")
    }

    /** 停用部分紀錄時應以中斷原因移除該候選。 */
    @Test
    fun `test interrupted candidate is excluded when disabled`() {
        val plan = evaluateHistoryCleanup(
            listOf(candidate("partial", interrupted = true)),
            emptySet(),
            policy(includeInterruptedMatches = false),
            instant(100),
        )

        assertEquals(HistoryCleanupReason.INTERRUPTED_EXCLUDED, plan.removals["partial"])
    }

    /** 受保護場次不得進入移除或剩餘候選清單。 */
    @Test
    fun `test protected match is excluded from evaluation`() {
        val plan = evaluateHistoryCleanup(
            listOf(candidate("active"), candidate("finished")),
            setOf("active"),
            policy(),
            instant(100),
        )

        assertFalse("active" in plan.removals)
        assertFalse(plan.remaining.any { it.matchId == "active" })
        assertEquals(listOf("finished"), plan.remaining.map { it.matchId })
    }

    /** 相同時間戳應依開局時間與 ID 產生穩定的最舊優先順序。 */
    @Test
    fun `test same timestamps use deterministic id ordering`() {
        val candidates = listOf(
            candidate("z", startedAt = 10, endedAt = 20),
            candidate("a", startedAt = 10, endedAt = 20),
            candidate("m", startedAt = 9, endedAt = 20),
        )

        val plan = evaluateHistoryCleanup(candidates, emptySet(), policy(maxMatches = 1), instant(30))

        assertEquals(listOf("m", "a"), plan.removals.keys.toList())
        assertEquals(listOf("z"), plan.remaining.map { it.matchId })
    }

    /** 數量上限應保留最新場次，包含已允許的部分紀錄。 */
    @Test
    fun `test match limit keeps newest included partial matches`() {
        val candidates = listOf(
            candidate("old-partial", endedAt = 1, interrupted = true),
            candidate("middle", endedAt = 2),
            candidate("new-partial", endedAt = 3, interrupted = true),
        )

        val plan = evaluateHistoryCleanup(
            candidates,
            emptySet(),
            policy(includeInterruptedMatches = true, maxMatches = 2),
            instant(4),
        )

        assertEquals(HistoryCleanupReason.MATCH_LIMIT, plan.removals["old-partial"])
        assertEquals(listOf("middle", "new-partial"), plan.remaining.map { it.matchId })
    }

    /** 重複對局 ID 應在評估前拒絕。 */
    @Test
    fun `test duplicate candidates are rejected`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            evaluateHistoryCleanup(
                listOf(candidate("duplicate"), candidate("duplicate")),
                emptySet(),
                policy(),
                instant(1),
            )
        }

        assertTrue(exception.message.orEmpty().contains("unique"))
    }

    /** 建立測試用保留政策。 */
    private fun policy(
        includeInterruptedMatches: Boolean = true,
        maxMatches: Int = 0,
        retentionDuration: Duration = Duration.ZERO,
    ): HistoryRetentionPolicy = HistoryRetentionPolicy(
        includeInterruptedMatches = includeInterruptedMatches,
        maxMatches = maxMatches,
        retentionDuration = retentionDuration,
        maxDiskBytes = 1,
    )

    /** 建立測試用候選摘要。 */
    private fun candidate(
        matchId: String,
        startedAt: Long = 0,
        endedAt: Long = 0,
        interrupted: Boolean = false,
    ): HistoryRetentionCandidate = HistoryRetentionCandidate(
        matchId = matchId,
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = endedAt,
        interrupted = interrupted,
    )

    /** 將 epoch milliseconds 轉為測試用 UTC 時刻。 */
    private fun instant(epochMilliseconds: Long): Instant = Instant.fromEpochMilliseconds(epochMilliseconds)
}
