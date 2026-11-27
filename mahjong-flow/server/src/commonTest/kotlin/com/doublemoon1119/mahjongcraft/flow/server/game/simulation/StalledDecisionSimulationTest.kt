package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/**
 * 部分 AI 決策逾時、改用固定命令時，台灣麻將仍能正常打完整場。
 *
 * - 伺服器忙碌時 AI 可能來不及在等待上限內決定，流程改用固定命令。這條路徑只在逾時才會走到。
 * - 哪些決策逾時由種子決定，同一個種子重現同一場。
 */
class StalledDecisionSimulationTest {
    /** 三成決策逾時時，每一場都正常結束，沒有流程中止、不合法命令或點數不守恆。 */
    @Test
    fun `Taiwan matches finish when some AI decisions time out`() = runTest(timeout = TEST_TIMEOUT) {
        SEEDS.forEach { seed ->
            val result = MatchSimulator(TaiwanRuleConfig(), stalledDecisionRate = STALLED_DECISION_RATE)
                .play(List(PLAYER_COUNT) { BuiltInAiStrategyKeys.BEGINNER }, seed = seed)

            assertNull(result.failure, result.failure?.describe())
        }
    }

    private companion object {
        /** 台灣麻將的玩家人數。 */
        const val PLAYER_COUNT = 4

        /** 每次決策逾時的機率。 */
        const val STALLED_DECISION_RATE = 0.3

        /** 打的對局。每個數字決定一場不同的牌局與逾時位置。 */
        val SEEDS: List<Int> = listOf(1, 2)

        /** 逾時會實際等待決策執行器的等待上限，整場比一般模擬慢。 */
        val TEST_TIMEOUT = 5.minutes
    }
}
