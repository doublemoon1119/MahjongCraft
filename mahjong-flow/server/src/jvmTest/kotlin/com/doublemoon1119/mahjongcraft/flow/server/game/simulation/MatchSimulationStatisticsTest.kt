package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * 以環境變數啟動的大量 AI 對局統計。一般建置不設定環境變數，因此這個測試會直接結束、不打任何一場。
 *
 * 啟用方式：設定 `MAHJONGCRAFT_SIMULATION_MATCHES` 後執行這個測試。Gradle 不會因為環境變數改變而重新執行
 * 已經通過的測試，因此要加上 `--rerun`：
 *
 * ```
 * # PowerShell
 * $env:MAHJONGCRAFT_SIMULATION_MATCHES = "1000"
 * ./gradlew -PmahjongcraftTarget=core :mahjong-flow:mahjong-flow-server:jvmTest --tests "*MatchSimulationStatisticsTest*" --rerun
 *
 * # bash
 * MAHJONGCRAFT_SIMULATION_MATCHES=1000 ./gradlew -PmahjongcraftTarget=core :mahjong-flow:mahjong-flow-server:jvmTest \
 *     --tests "*MatchSimulationStatisticsTest*" --rerun
 * ```
 *
 * 可調整的環境變數：
 *
 * - `MAHJONGCRAFT_SIMULATION_MATCHES`：對局數；未設定時不執行。
 * - `MAHJONGCRAFT_SIMULATION_LINEUP`：以逗號分隔的四個策略 key；預設為初級、中級、高級與隨機出牌各一位。
 * - `MAHJONGCRAFT_SIMULATION_GAME_LENGTH`：`one_game`、`east` 或 `two_winds`；預設為 `east`。
 * - `MAHJONGCRAFT_SIMULATION_PARALLELISM`：同時進行的對局數；預設為可用的處理器數。每場對局使用獨立的對局環境，
 *   模擬全部是 CPU 運算，同時進行的場數超過處理器數不會更快。
 *
 * 輸出每個策略的和牌率、放銃率、平均名次與各自的 95% 信賴區間，以及每次決策的平均與最長計算時間與總耗時，
 * 同時寫入 `mahjong-flow/server/build/simulation/report.txt`。任何一場偵測到問題時測試失敗，並列出每個問題的描述。
 */
class MatchSimulationStatisticsTest {
    /** 依環境變數執行指定場數並輸出統計。 */
    @Test
    fun `simulate matches configured by environment variables`() = runTest(timeout = 24.hours) {
        val matches = System.getenv(MATCHES_VARIABLE)?.toIntOrNull() ?: return@runTest
        val lineup = System.getenv(LINEUP_VARIABLE)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: DEFAULT_LINEUP
        val gameLength = when (System.getenv(GAME_LENGTH_VARIABLE)?.lowercase()) {
            null, "east" -> RiichiGameLength.East
            "one_game" -> RiichiGameLength.OneGame
            "two_winds" -> RiichiGameLength.TwoWinds
            else -> error("Unknown $GAME_LENGTH_VARIABLE: ${System.getenv(GAME_LENGTH_VARIABLE)}")
        }
        val parallelism = (System.getenv(PARALLELISM_VARIABLE)?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors())
            .coerceIn(1, matches.coerceAtLeast(1))
        val simulator = MatchSimulator(RiichiRuleConfig(gameLength = gameLength))

        val started = TimeSource.Monotonic.markNow()
        val batches = withContext(Dispatchers.Default) {
            val nextMatch = AtomicInteger(0)
            coroutineScope {
                List(parallelism) {
                    async {
                        val batch = WorkerBatch()
                        while (nextMatch.getAndIncrement() < matches) {
                            val recorder = DecisionRecorder()
                            batch.results += simulator.play(lineup, recorder)
                            batch.recorders += recorder
                        }
                        batch
                    }
                }.awaitAll()
            }
        }
        val elapsed = started.elapsedNow()

        val statistics = SimulationStatistics()
        val failures = mutableListOf<SimulationFailure>()
        batches.forEach { batch ->
            batch.recorders.forEach { statistics.addTimings(it.timings) }
            batch.results.forEach { result -> result.failure?.let(failures::add) ?: statistics.add(result) }
        }
        val report = buildString {
            appendLine("Lineup: ${lineup.joinToString()}; game length: $gameLength; requested matches: $matches; failures: ${failures.size}")
            appendLine("Parallelism: $parallelism; elapsed: ${elapsed.inWholeSeconds} s")
            append(statistics.report(order = lineup.distinct()))
        }
        println(report)
        File(REPORT_PATH).apply { parentFile.mkdirs() }.writeText(report)
        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n") { it.describe() })
    }

    /**
     * 一個同時進行的工作者打完的對局。
     *
     * @property results 每場對局的結果。
     * @property recorders 每場對局的決策紀錄。
     */
    private class WorkerBatch(
        val results: MutableList<MatchResult> = mutableListOf(),
        val recorders: MutableList<DecisionRecorder> = mutableListOf(),
    )

    /** 環境變數與預設值。 */
    private companion object {
        /** 對局數的環境變數。 */
        const val MATCHES_VARIABLE = "MAHJONGCRAFT_SIMULATION_MATCHES"

        /** 策略組合的環境變數。 */
        const val LINEUP_VARIABLE = "MAHJONGCRAFT_SIMULATION_LINEUP"

        /** 對局長度的環境變數。 */
        const val GAME_LENGTH_VARIABLE = "MAHJONGCRAFT_SIMULATION_GAME_LENGTH"

        /** 同時進行對局數的環境變數。 */
        const val PARALLELISM_VARIABLE = "MAHJONGCRAFT_SIMULATION_PARALLELISM"

        /** 報告的輸出位置，相對於模組目錄。 */
        const val REPORT_PATH = "build/simulation/report.txt"

        /** 預設的策略組合。 */
        val DEFAULT_LINEUP = listOf(
            BuiltInAiStrategyKeys.BEGINNER,
            BuiltInAiStrategyKeys.INTERMEDIATE,
            BuiltInAiStrategyKeys.ADVANCED,
            RandomAiStrategy.KEY,
        )
    }
}
