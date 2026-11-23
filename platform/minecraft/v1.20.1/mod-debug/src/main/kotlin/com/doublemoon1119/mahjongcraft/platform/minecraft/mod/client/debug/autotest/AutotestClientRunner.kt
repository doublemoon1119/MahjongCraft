package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import kotlin.time.TimeSource

/**
 * 用戶端自動測試的執行者：遊戲到主選單後依序執行各步驟，寫出報告並關閉遊戲。
 *
 * 報告寫在遊戲資料夾的 `autotest/<開始時間>/`，包含 `report.md`、`report.json` 與截圖；`autotest/latest.txt` 記錄最近一次的
 * 資料夾名稱。遊戲在測試途中被關閉時，仍寫出報告，未完成的步驟記為中止。
 *
 * @property selection 要執行的步驟。
 * @property steps 全部步驟，依執行順序；第一個步驟建立並進入測試世界，一律執行。
 */
internal class AutotestClientRunner(
    private val selection: AutotestSelection,
    private val steps: List<AutotestStep>,
) {
    /** 記錄自動測試進度的 logger。 */
    private val logger = mahjongCraftLogger(AutotestClientRunner::class)

    /** 是否已經開始。 */
    private var started = false

    /** 已完成的步驟結果。 */
    private val results = mutableListOf<AutotestStepResult>()

    /** 正在執行的步驟；沒有時為 null。 */
    @Volatile
    private var running: String? = null

    /** 報告是否已經寫出。 */
    @Volatile
    private var reported = false

    /** 開始時間。 */
    private val startedAt = LocalDateTime.now()

    /** 開始時的單調時間。 */
    private val startMark = TimeSource.Monotonic.markNow()

    /** 報告所在的資料夾。 */
    private lateinit var outputDirectory: Path

    /**
     * 掛上用戶端事件；遊戲第一次到主選單、而且啟動時的資源載入已經結束時開始執行。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onClientTickEnd { client ->
            if (!started && client.screen is TitleScreen && client.overlay == null) {
                started = true
                start(client)
            }
        }
    }

    /**
     * 準備報告資料夾並在用戶端主執行緒上執行全部步驟。
     *
     * @param client 目前客戶端。
     */
    private fun start(client: Minecraft) {
        val root = client.gameDirectory.toPath().resolve("autotest")
        outputDirectory = root.resolve(startedAt.format(FOLDER_FORMAT)).toAbsolutePath().normalize()
        Files.createDirectories(outputDirectory)
        Files.writeString(root.resolve("latest.txt"), outputDirectory.fileName.toString())
        Runtime.getRuntime().addShutdownHook(Thread({ writeReport(aborted = true) }, "MahjongCraft autotest report"))
        logger.info("Autotest started; report folder {}", outputDirectory)
        CoroutineScope(SupervisorJob() + (client as Executor).asCoroutineDispatcher()).launch {
            steps.forEachIndexed { index, step ->
                if (index == 0 || selection.includes(step.id)) runStep(client, step) else results += skipped(step.id)
            }
            writeReport(aborted = false)
            logger.info("Autotest finished; closing the game")
            client.stop()
        }
    }

    /**
     * 執行一個步驟並記錄結果；步驟拋出的例外記為失敗，不中斷之後的步驟。
     *
     * @param client 目前客戶端。
     * @param step 要執行的步驟。
     */
    private suspend fun runStep(client: Minecraft, step: AutotestStep) {
        running = step.id
        logger.info("Autotest step {} started", step.id)
        val context = AutotestContext(client, outputDirectory, step.id)
        val mark = TimeSource.Monotonic.markNow()
        AutotestRuntime.drainSounds()
        val error = runCatching { step.run(context) }.exceptionOrNull()
        val passed = error == null && context.checks.all(AutotestCheck::passed)
        results += AutotestStepResult(
            id = step.id,
            status = if (passed) AutotestStatus.PASSED else AutotestStatus.FAILED,
            durationMillis = mark.elapsedNow().inWholeMilliseconds,
            checks = context.checks.toList(),
            screenshots = context.screenshots.toList(),
            sounds = AutotestRuntime.drainSounds().map { AutotestSoundRecord(it.soundId, it.at.inWholeMilliseconds) },
            error = error?.stackTraceToString(),
        )
        running = null
        logger.info("Autotest step {} {}", step.id, if (passed) "passed" else "failed")
    }

    /**
     * 寫出報告；只寫一次。
     *
     * @param aborted 是否因遊戲在測試途中關閉而寫出。
     */
    @Synchronized
    private fun writeReport(aborted: Boolean) {
        if (reported || !::outputDirectory.isInitialized) return
        reported = true
        val stepResults = results.toList() + listOfNotNull(running?.let(::abortedStep)) + steps.drop(results.size + if (running != null) 1 else 0).map { skipped(it.id) }
        val status = when {
            aborted -> AutotestStatus.ABORTED
            stepResults.any { it.status == AutotestStatus.FAILED } -> AutotestStatus.FAILED
            else -> AutotestStatus.PASSED
        }
        val report = AutotestReport(
            target = System.getProperty(TARGET_PROPERTY) ?: "unknown",
            startedAt = startedAt.toString(),
            durationMillis = startMark.elapsedNow().inWholeMilliseconds,
            status = status,
            steps = stepResults,
        )
        Files.writeString(outputDirectory.resolve("report.json"), JSON.encodeToString(AutotestReport.serializer(), report))
        Files.writeString(outputDirectory.resolve("report.md"), autotestReportMarkdown(report))
    }

    private companion object {
        /** 開發執行以這個系統屬性傳入測試的目標。 */
        const val TARGET_PROPERTY = "mahjongcraft.autotest.target"

        /** 報告資料夾名稱的時間格式。 */
        val FOLDER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        /** 報告的 JSON 格式。 */
        val JSON = Json { prettyPrint = true }

        /**
         * 沒有執行的步驟。
         *
         * @param id 步驟識別碼。
         * @return 略過的結果。
         */
        fun skipped(id: String) = AutotestStepResult(id, AutotestStatus.SKIPPED, 0, emptyList(), emptyList(), emptyList(), null)

        /**
         * 遊戲關閉時正在執行的步驟。
         *
         * @param id 步驟識別碼。
         * @return 中止的結果。
         */
        fun abortedStep(id: String) = AutotestStepResult(id, AutotestStatus.ABORTED, 0, emptyList(), emptyList(), emptyList(), null)
    }
}
