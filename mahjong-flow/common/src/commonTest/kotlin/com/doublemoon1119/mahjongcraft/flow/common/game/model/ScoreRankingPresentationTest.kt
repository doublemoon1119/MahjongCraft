package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** [ScoreRankingPresentation] 的名次資料驗證測試。 */
class ScoreRankingPresentationTest {
    /** 驗證不完整或重複名次不會被接受。 */
    @Test
    fun `rejects incomplete rank sequences`() {
        val players = listOf(
            ScoreRankingPlayer(Uuid.random(), 0, false, 25_000, 25_000, 1, 1),
            ScoreRankingPlayer(Uuid.random(), 1, false, 25_000, 25_000, 1, 2),
        )

        assertFailsWith<IllegalArgumentException> { ScoreRankingPresentation(players) }
    }
}
