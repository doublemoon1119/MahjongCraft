package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultKindDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 最終決策提交鎖與亂序 ACK 的單元測試。 */
class DecisionSubmissionTrackerTest {
    /** 接受與 stale 都維持鎖定，直到權威 prompt 消失。 */
    @Test
    fun `test accepted and stale acknowledgements wait for authoritative update`() {
        PlayerDecisionSubmissionResultKindDto.entries
            .filter { it != PlayerDecisionSubmissionResultKindDto.REJECTED }
            .forEach { result ->
                val tracker = DecisionSubmissionTracker()
                tracker.begin("game", "decision", "submission")

                assertEquals(AcknowledgementEffect.KEPT_LOCKED, tracker.acknowledge(ack(result)))
                assertTrue(tracker.isPending("decision"))

                tracker.applyAuthoritativeDecision(null)
                assertFalse(tracker.isPending())
            }
    }

    /** 拒絕會解除同一 decision 的提交鎖並允許重試。 */
    @Test
    fun `test rejected acknowledgement unlocks current decision`() {
        val tracker = DecisionSubmissionTracker()
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
        val tracker = DecisionSubmissionTracker()
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
        val tracker = DecisionSubmissionTracker()
        tracker.begin("game", "decision", "submission")

        tracker.applyAuthoritativeDecision("decision")
        assertTrue(tracker.isPending())

        tracker.applyAuthoritativeDecision("next-decision")
        assertFalse(tracker.isPending())

        tracker.begin("game", "next-decision", "next-submission")
        tracker.clear()
        assertFalse(tracker.isPending())
    }

    /** 建立目前提交的 ACK。 */
    private fun ack(result: PlayerDecisionSubmissionResultKindDto) = PlayerDecisionSubmissionResultDto(
        gameId = "game",
        decisionKey = "decision",
        submissionId = "submission",
        result = result,
    )
}
