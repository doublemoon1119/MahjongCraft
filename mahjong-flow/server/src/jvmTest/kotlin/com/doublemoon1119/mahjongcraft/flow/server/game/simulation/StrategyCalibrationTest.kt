package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationParameters
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectedValueAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.InformationLevel
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
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

/**
 * 以環境變數啟動的 AI 設定校準：[CANDIDATES] 中的每個候選設定輪流坐在中間的座位，與初級、高級、隨機出牌同桌打東風戰，
 * 比較各等級的名次順序與差距。一般建置不設定環境變數，因此這個測試會直接結束、不打任何一場。
 *
 * 要比較其他設定時，修改 [CANDIDATES] 的內容，例如中級看打點的比例或估計參數。
 *
 * 啟用方式：設定 `MAHJONGCRAFT_CALIBRATION_MATCHES` 後執行這個測試，並加上 `--rerun`：
 *
 * ```
 * # PowerShell
 * $env:MAHJONGCRAFT_CALIBRATION_MATCHES = "2000"
 * ./gradlew -PmahjongcraftTarget=core :mahjong-flow:mahjong-flow-server:jvmTest --tests "*StrategyCalibrationTest*" --rerun
 *
 * # bash
 * MAHJONGCRAFT_CALIBRATION_MATCHES=2000 ./gradlew -PmahjongcraftTarget=core :mahjong-flow:mahjong-flow-server:jvmTest \
 *     --tests "*StrategyCalibrationTest*" --rerun
 * ```
 *
 * 可調整的環境變數：
 *
 * - `MAHJONGCRAFT_CALIBRATION_MATCHES`：每個候選設定的對局數；未設定時不執行。
 * - `MAHJONGCRAFT_CALIBRATION_PARALLELISM`：同時進行的對局數；預設為可用的處理器數。
 *
 * 每跑完一個候選設定，就把累積的統計寫入 `mahjong-flow/server/build/simulation/calibration.txt`，
 * 跑到一半中斷時仍可查看已完成的結果。任何一場偵測到問題時測試失敗，並列出每個問題的描述。
 */
class StrategyCalibrationTest {
    /** 依環境變數對每個候選設定執行指定場數並輸出統計。 */
    @Test
    fun `compare candidate settings against the built-in levels`() = runTest(timeout = 24.hours) {
        val matches = System.getenv(MATCHES_VARIABLE)?.toIntOrNull() ?: return@runTest
        val parallelism = (System.getenv(PARALLELISM_VARIABLE)?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors())
            .coerceIn(1, matches.coerceAtLeast(1))
        val simulator = MatchSimulator(RiichiRuleConfig(gameLength = RiichiGameLength.East)) { registry, runtime ->
            registry.registerBuiltInAiStrategies(
                moduleRegistry = runtime.moduleRegistry,
                extensionActionRegistry = runtime.extensionActionRegistry,
                opponentModelRegistry = runtime.opponentModelRegistry,
            )
            CANDIDATES.forEach { candidate ->
                registry.register(candidate.key) {
                    ExpectedValueAiStrategy(
                        level = candidate.level,
                        moduleRegistry = runtime.moduleRegistry,
                        extensionActionRegistry = runtime.extensionActionRegistry,
                        opponentModels = runtime.opponentModelRegistry,
                        parameters = candidate.parameters,
                    )
                }
            }
        }

        val report = StringBuilder()
        val failures = mutableListOf<SimulationFailure>()
        CANDIDATES.forEach { candidate ->
            val lineup = listOf(BuiltInAiStrategyKeys.BEGINNER, candidate.key, BuiltInAiStrategyKeys.ADVANCED, RandomAiStrategy.KEY)
            val results = withContext(Dispatchers.Default) {
                val nextMatch = AtomicInteger(0)
                coroutineScope {
                    List(parallelism) {
                        async { buildList { while (nextMatch.getAndIncrement() < matches) add(simulator.play(lineup)) } }
                    }.awaitAll().flatten()
                }
            }
            val statistics = SimulationStatistics()
            results.forEach { result -> result.failure?.let(failures::add) ?: statistics.add(result) }
            report.appendLine("=== ${candidate.key} (failures ${results.count { it.failure != null }})")
            report.append(statistics.report(order = lineup))
            report.appendLine()
            File(REPORT_PATH).apply { parentFile.mkdirs() }.writeText(report.toString())
        }
        println(report)
        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n") { it.describe() })
    }

    /**
     * 一個要比較的 AI 設定。
     *
     * @property key 在報告中顯示的策略 key。
     * @property level 可使用的資訊範圍。
     * @property parameters 估計參數。
     */
    private data class CalibrationCandidate(
        val key: String,
        val level: InformationLevel,
        val parameters: ExpectationParameters = ExpectationParameters.DEFAULT,
    )

    /** 環境變數、輸出位置與候選設定。 */
    private companion object {
        /** 每個候選設定對局數的環境變數。 */
        const val MATCHES_VARIABLE = "MAHJONGCRAFT_CALIBRATION_MATCHES"

        /** 同時進行對局數的環境變數。 */
        const val PARALLELISM_VARIABLE = "MAHJONGCRAFT_CALIBRATION_PARALLELISM"

        /** 報告的輸出位置，相對於模組目錄。 */
        const val REPORT_PATH = "build/simulation/calibration.txt"

        /** 要比較的候選設定：內建中級，以及看打點比例稍低與稍高的中級。 */
        val CANDIDATES: List<CalibrationCandidate> = listOf(0.2, InformationLevel.INTERMEDIATE.winValueDetail, 0.4).map { detail ->
            CalibrationCandidate(
                key = "calibration:intermediate-detail-$detail",
                level = InformationLevel.INTERMEDIATE.copy(winValueDetail = detail),
            )
        }
    }
}
