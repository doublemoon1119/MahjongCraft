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
 * @property ronWins 榮和次數，也就是其他玩家放銃給這個策略的次數。
 * @property scoreDeltas 每局的分數變化，包含立直棒與本場。
 * @property winScoreDeltas 和牌那幾局的分數變化，包含立直棒與本場。
 * @property opponentRiichiRounds 局結束時有其他玩家已經立直的局數。
 * @property winsAgainstRiichi [opponentRiichiRounds] 中和牌的次數。
 * @property dealInsAgainstRiichi [opponentRiichiRounds] 中放銃的次數。
 * @property placements 每場整場結束時的名次。
 * @property timing 決策時間。
 */
internal data class StrategyStatistics(
    val strategyKey: String,
    val playerRounds: Int,
    val wins: Int,
    val dealIns: Int,
    val ronWins: Int,
    val scoreDeltas: List<Int>,
    val winScoreDeltas: List<Int>,
    val opponentRiichiRounds: Int,
    val winsAgainstRiichi: Int,
    val dealInsAgainstRiichi: Int,
    val placements: List<Int>,
    val timing: DecisionTiming,
) {
    /** 每局和牌率。 */
    val winRate: Estimate get() = Estimate.rate(wins, playerRounds)

    /** 每局放銃率。 */
    val dealInRate: Estimate get() = Estimate.rate(dealIns, playerRounds)

    /** 每局榮和率，也就是其他玩家每局放銃給這個策略的比例。 */
    val ronWinRate: Estimate get() = Estimate.rate(ronWins, playerRounds)

    /** 每局平均分數變化。 */
    val averageScoreDelta: Estimate get() = Estimate.mean(scoreDeltas)

    /** 和牌那幾局的平均分數變化。 */
    val averageWinScoreDelta: Estimate get() = Estimate.mean(winScoreDeltas)

    /** 有其他玩家立直的局中的和牌率。 */
    val winRateAgainstRiichi: Estimate get() = Estimate.rate(winsAgainstRiichi, opponentRiichiRounds)

    /** 有其他玩家立直的局中的放銃率。 */
    val dealInRateAgainstRiichi: Estimate get() = Estimate.rate(dealInsAgainstRiichi, opponentRiichiRounds)

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
            val againstRiichi = results.filter { it.opponentRiichi }
            StrategyStatistics(
                strategyKey = key,
                playerRounds = results.size,
                wins = results.count { it.won },
                dealIns = results.count { it.dealtIn },
                ronWins = results.count { it.wonByRon },
                scoreDeltas = results.map { it.scoreDelta },
                winScoreDeltas = results.filter { it.won }.map { it.scoreDelta },
                opponentRiichiRounds = againstRiichi.size,
                winsAgainstRiichi = againstRiichi.count { it.won },
                dealInsAgainstRiichi = againstRiichi.count { it.dealtIn },
                placements = placements[key].orEmpty(),
                timing = timings[key] ?: DecisionTiming(),
            )
        }
    }

    /**
     * 以文字表格呈現的彙整結果：第一張表為和牌、放銃、名次與決策時間，第二張表為分數得失、
     * 其他玩家放銃給它的比例，以及有其他玩家立直時的攻守。
     */
    fun report(order: List<String> = emptyList()): String = buildString {
        val statistics = byStrategy(order)
        appendLine("Matches: $matchCount")
        appendLine("strategy | player-rounds | win rate | deal-in rate | average placement | decisions | avg ms | max ms")
        statistics.forEach { stats ->
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
        appendLine()
        appendLine(
            "strategy | score delta per round | score delta per win | ron win rate | " +
                "rounds with opponent riichi | win rate vs riichi | deal-in rate vs riichi",
        )
        statistics.forEach { stats ->
            appendLine(
                listOf(
                    stats.strategyKey,
                    stats.averageScoreDelta.format(),
                    stats.averageWinScoreDelta.format(),
                    stats.ronWinRate.format(),
                    stats.opponentRiichiRounds.toString(),
                    stats.winRateAgainstRiichi.format(),
                    stats.dealInRateAgainstRiichi.format(),
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
