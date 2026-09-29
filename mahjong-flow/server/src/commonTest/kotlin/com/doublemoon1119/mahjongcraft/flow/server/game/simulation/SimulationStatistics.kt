package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 一個估計值與其 95% 信賴區間的半寬。
 *
 * @property value 估計值。
 * @property halfWidth95 95% 信賴區間的半寬；區間為 [value] ± [halfWidth95]。
 */
internal data class Estimate(
    val value: Double,
    val halfWidth95: Double,
) {
    /** [Estimate] 的建立方式。 */
    companion object {
        /** 常態近似下 95% 信賴區間的 z 值。 */
        private const val Z_95: Double = 1.96

        /** [successes] / [trials] 的比例；以常態近似計算區間。 */
        fun rate(successes: Int, trials: Int): Estimate {
            if (trials == 0) return Estimate(value = 0.0, halfWidth95 = 0.0)
            val p = successes.toDouble() / trials
            return Estimate(value = p, halfWidth95 = Z_95 * sqrt(p * (1 - p) / trials))
        }

        /** [samples] 的平均；以樣本標準差計算區間，少於兩個樣本時半寬為 0。 */
        fun mean(samples: List<Int>): Estimate {
            if (samples.isEmpty()) return Estimate(value = 0.0, halfWidth95 = 0.0)
            val mean = samples.average()
            if (samples.size < 2) return Estimate(value = mean, halfWidth95 = 0.0)
            val variance = samples.sumOf { (it - mean) * (it - mean) } / (samples.size - 1)
            return Estimate(value = mean, halfWidth95 = Z_95 * sqrt(variance / samples.size))
        }
    }
}

/**
 * 一個策略在所有模擬對局中的累計結果。
 *
 * @property strategyKey 策略。
 * @property playerRounds 參與的局數（每位使用此策略的玩家每局算一次）。
 * @property wins 和牌次數。
 * @property dealIns 放銃次數。
 * @property placements 每場整場結束時的名次。
 * @property timing 決策時間。
 */
internal data class StrategyStatistics(
    val strategyKey: String,
    val playerRounds: Int,
    val wins: Int,
    val dealIns: Int,
    val placements: List<Int>,
    val timing: DecisionTiming,
) {
    /** 每局和牌率。 */
    val winRate: Estimate get() = Estimate.rate(wins, playerRounds)

    /** 每局放銃率。 */
    val dealInRate: Estimate get() = Estimate.rate(dealIns, playerRounds)

    /** 平均名次。 */
    val averagePlacement: Estimate get() = Estimate.mean(placements)
}

/**
 * 累計多場模擬對局的結果，並依策略彙整。
 */
internal class SimulationStatistics {
    /** 已加入的對局。 */
    private val matches = mutableListOf<MatchResult>()

    /** 所有對局累計的決策時間。 */
    private val timings = mutableMapOf<String, DecisionTiming>()

    /** 已加入的對局數。 */
    val matchCount: Int get() = matches.size

    /** 加入一場對局的結果。 */
    fun add(match: MatchResult) {
        matches += match
    }

    /** 加入一份決策時間紀錄。 */
    fun addTimings(recorded: Map<String, DecisionTiming>) {
        recorded.forEach { (key, timing) -> timings[key] = timings.getOrElse(key) { DecisionTiming() } + timing }
    }

    /** 依策略彙整，依 [order] 排列；不在 [order] 中的策略排在後面。 */
    fun byStrategy(order: List<String> = emptyList()): List<StrategyStatistics> {
        val seats = matches.flatMap { match -> match.rounds.flatten() }.groupBy { it.strategyKey }
        val placements = matches.flatMap { match ->
            match.placementsByPlayer.map { (playerId, place) -> match.strategyKeysByPlayer.getValue(playerId) to place }
        }.groupBy({ it.first }, { it.second })
        val keys = (order + seats.keys + timings.keys).distinct().filter { it in seats || it in timings }
        return keys.map { key ->
            val results = seats[key].orEmpty()
            StrategyStatistics(
                strategyKey = key,
                playerRounds = results.size,
                wins = results.count { it.won },
                dealIns = results.count { it.dealtIn },
                placements = placements[key].orEmpty(),
                timing = timings[key] ?: DecisionTiming(),
            )
        }
    }

    /** 以文字表格呈現的彙整結果。 */
    fun report(order: List<String> = emptyList()): String = buildString {
        appendLine("Matches: $matchCount")
        appendLine("strategy | player-rounds | win rate | deal-in rate | average placement | decisions | avg ms | max ms")
        byStrategy(order).forEach { stats ->
            appendLine(
                listOf(
                    stats.strategyKey,
                    stats.playerRounds.toString(),
                    stats.winRate.format(),
                    stats.dealInRate.format(),
                    stats.averagePlacement.format(),
                    stats.timing.count.toString(),
                    stats.timing.average.inWholeMilliseconds.toString(),
                    stats.timing.max.inWholeMilliseconds.toString(),
                ).joinToString(" | "),
            )
        }
    }

    /** 以「值 ± 半寬」呈現，四捨五入到小數點後三位。 */
    private fun Estimate.format(): String = "${value.rounded()} ± ${halfWidth95.rounded()}"

    /** 四捨五入到小數點後三位的文字。 */
    private fun Double.rounded(): String {
        val scaled = round(this * THOUSAND).toLong()
        val sign = if (scaled < 0) "-" else ""
        val absolute = abs(scaled)
        return "$sign${absolute / THOUSAND.toLong()}.${(absolute % THOUSAND.toLong()).toString().padStart(3, '0')}"
    }

    /** [SimulationStatistics] 的常數。 */
    private companion object {
        /** 小數點後三位的倍數。 */
        const val THOUSAND: Double = 1000.0
    }
}
