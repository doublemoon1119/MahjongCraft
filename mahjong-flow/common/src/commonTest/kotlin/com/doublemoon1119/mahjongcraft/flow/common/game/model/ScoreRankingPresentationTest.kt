package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** [ScoreRankingPresentation] 與 [ScoreRankingAnimation] 的規則中立動畫測試。 */
class ScoreRankingPresentationTest {
    /** 驗證大幅跨名次時會依連續位置逐一經過中間名次。 */
    @Test
    fun `moves through intermediate live ranks`() {
        val players = List(4) { index ->
            ScoreRankingPlayer(
                playerId = Uuid.random(),
                seatIndex = index,
                isAi = false,
                previousScore = 10_000 - index * 1_000,
                currentScore = if (index == 3) 20_000 else 9_000 - index * 1_000,
                previousRank = index + 1,
                currentRank = if (index == 3) 1 else index + 2,
            )
        }
        val presentation = ScoreRankingPresentation(players)

        val startRanks = ScoreRankingAnimation.liveRanks(ScoreRankingAnimation.rows(presentation, 0.0))
        val middleRanks = ScoreRankingAnimation.liveRanks(ScoreRankingAnimation.rows(presentation, 0.35))
        val finalRanks = ScoreRankingAnimation.liveRanks(ScoreRankingAnimation.rows(presentation, 1.0))

        assertEquals(4, startRanks.getValue(players[3].playerId))
        assertEquals(2, middleRanks.getValue(players[3].playerId))
        assertEquals(1, finalRanks.getValue(players[3].playerId))
    }

    /** 驗證最後一幀的分數與變化量精確等於權威終點。 */
    @Test
    fun `converges exactly to authoritative scores`() {
        val player = ScoreRankingPlayer(Uuid.random(), 0, false, Int.MAX_VALUE - 10, Int.MAX_VALUE, 1, 1)

        val row = ScoreRankingAnimation.rows(ScoreRankingPresentation(listOf(player)), 1.0).single()

        assertEquals(Int.MAX_VALUE, row.score)
        assertEquals(10, row.delta)
        assertEquals(1.0, row.position)
    }

    /** 驗證不完整或重複名次不會進入 renderer。 */
    @Test
    fun `rejects incomplete rank sequences`() {
        val players = listOf(
            ScoreRankingPlayer(Uuid.random(), 0, false, 25_000, 25_000, 1, 1),
            ScoreRankingPlayer(Uuid.random(), 1, false, 25_000, 25_000, 1, 2),
        )

        assertFailsWith<IllegalArgumentException> { ScoreRankingPresentation(players) }
    }
}
