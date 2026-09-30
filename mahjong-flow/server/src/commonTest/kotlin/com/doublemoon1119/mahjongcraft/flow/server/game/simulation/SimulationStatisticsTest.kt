package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** 驗證模擬統計的信賴區間與依策略彙整。 */
class SimulationStatisticsTest {
    /** 比例的區間半寬為 1.96 × √(p(1 − p) / n)。 */
    @Test
    fun `rate estimate uses the normal approximation`() {
        val estimate = Estimate.rate(successes = 25, trials = 100)

        assertEquals(0.25, estimate.value)
        assertEquals(1.96 * sqrt(0.25 * 0.75 / 100), estimate.halfWidth95, absoluteTolerance = 1e-12)
        assertEquals(Estimate(0.0, 0.0), Estimate.rate(successes = 0, trials = 0))
    }

    /** 平均的區間半寬以樣本標準差計算；只有一個樣本時半寬為 0。 */
    @Test
    fun `mean estimate uses the sample standard deviation`() {
        val estimate = Estimate.mean(listOf(1, 2, 3, 4))

        assertEquals(2.5, estimate.value)
        assertEquals(1.96 * sqrt((5.0 / 3.0) / 4), estimate.halfWidth95, absoluteTolerance = 1e-12)
        assertEquals(Estimate(3.0, 0.0), Estimate.mean(listOf(3)))
    }

    /** 依策略彙整局數、和牌、放銃、名次與決策時間，並依指定順序排列。 */
    @Test
    fun `results are grouped by strategy`() {
        val a = Uuid.random()
        val b = Uuid.random()
        val statistics = SimulationStatistics()
        statistics.add(
            MatchResult(
                strategyKeysByPlayer = mapOf(a to "a", b to "b"),
                rounds = listOf(
                    listOf(seat(a, "a", won = true), seat(b, "b", dealtIn = true)),
                    listOf(seat(a, "a"), seat(b, "b", won = true)),
                ),
                placementsByPlayer = mapOf(a to 1, b to 2),
                failure = null,
            ),
        )
        statistics.addTimings(mapOf("a" to DecisionTiming() + 10.milliseconds + 30.milliseconds))

        val (first, second) = statistics.byStrategy(order = listOf("b", "a"))

        assertEquals("b", first.strategyKey)
        assertEquals(2, first.playerRounds)
        assertEquals(1, first.wins)
        assertEquals(1, first.dealIns)
        assertEquals(listOf(2), first.placements)
        assertEquals("a", second.strategyKey)
        assertEquals(2, second.timing.count)
        assertEquals(20.milliseconds, second.timing.average)
        assertEquals(30.milliseconds, second.timing.max)
    }

    /** 分數得失只計入該策略自己的局，和牌得點只計入和牌的局；立直下的攻守只計入有其他玩家立直的局。 */
    @Test
    fun `score and riichi metrics count only the matching rounds`() {
        val a = Uuid.random()
        val b = Uuid.random()
        val statistics = SimulationStatistics()
        statistics.add(
            MatchResult(
                strategyKeysByPlayer = mapOf(a to "a", b to "b"),
                rounds = listOf(
                    listOf(
                        seat(a, "a", scoreDelta = 8000, won = true, wonByRon = true, opponentRiichi = true),
                        seat(b, "b", scoreDelta = -8000, dealtIn = true),
                    ),
                    listOf(
                        seat(a, "a", scoreDelta = -1000, opponentRiichi = true),
                        seat(b, "b", scoreDelta = 1000),
                    ),
                    listOf(
                        seat(a, "a", scoreDelta = 2000, won = true),
                        seat(b, "b", scoreDelta = -2000),
                    ),
                ),
                placementsByPlayer = mapOf(a to 1, b to 2),
                failure = null,
            ),
        )

        val stats = statistics.byStrategy(order = listOf("a")).first()

        assertEquals(3000.0, stats.averageScoreDelta.value)
        assertEquals(5000.0, stats.averageWinScoreDelta.value)
        assertEquals(Estimate.rate(successes = 1, trials = 3), stats.ronWinRate)
        assertEquals(2, stats.opponentRiichiRounds)
        assertEquals(Estimate.rate(successes = 1, trials = 2), stats.winRateAgainstRiichi)
        assertEquals(Estimate.rate(successes = 0, trials = 2), stats.dealInRateAgainstRiichi)
    }

    /** 測試用的一位玩家在一局的結果。 */
    private fun seat(
        playerId: Uuid,
        strategyKey: String,
        scoreDelta: Int = 0,
        won: Boolean = false,
        dealtIn: Boolean = false,
        wonByRon: Boolean = false,
        opponentRiichi: Boolean = false,
    ): SeatRoundResult = SeatRoundResult(
        playerId = playerId,
        strategyKey = strategyKey,
        scoreDelta = scoreDelta,
        won = won,
        dealtIn = dealtIn,
        wonByRon = wonByRon,
        opponentRiichi = opponentRiichi,
    )
}
