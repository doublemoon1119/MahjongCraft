package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestCheck
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestReport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestReportRetention
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestSelection
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestSoundRecord
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestStepResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.autotestReportMarkdown
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.client.CloudStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.tutorial.TutorialSteps
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Executor
import kotlin.time.TimeSource

/**
 * 用戶端自動測試的執行者：遊戲到主選單後依序執行各步驟，寫出報告並關閉遊戲。
 *
 * - 報告寫在遊戲資料夾的 `autotest/<開始時間>/`，包含 `report.md`、`report.json` 與截圖。
 * - `autotest/latest.txt` 記錄最近一次的資料夾名稱。
 * - 只保留最近幾次的報告（[AutotestReportRetention]），更舊的在開始時刪掉。
 * - 遊戲在測試途中被關閉時，仍寫出報告，未完成的步驟記為中止。
 *
 * @property selection 要執行的步驟。
 * @property steps 全部步驟，依執行順序。第一個步驟建立並進入測試世界，一律執行。
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

    /** 正在執行的步驟，沒有時為 null。 */
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
     * 掛上用戶端事件。
     *
     * - 遊戲第一次到主選單、而且啟動時的資源載入已經結束時，開始執行。
     * - 第一次啟動的無障礙設定引導會被略過。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onClientTickEnd { client ->
            // Gradle 的自動測試啟動前已寫好選項，不會出現無障礙設定引導；不經過 Gradle 啟動時（例如從 IDE 執行）執行目錄
            // 第一次啟動仍會顯示，略過它並記下已完成，之後直接到主選單。
            if (!started && client.screen is AccessibilityOnboardingScreen && client.overlay == null) {
                client.options.onboardAccessibility = false
                client.options.save()
                client.setScreen(TitleScreen())
            }
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
        applyDisplayOptions(client)
        val root = client.gameDirectory.toPath().resolve("autotest")
        outputDirectory = root.resolve(startedAt.format(AutotestReportRetention.FOLDER_FORMAT)).toAbsolutePath().normalize()
        Files.createDirectories(outputDirectory)
        Files.writeString(root.resolve("latest.txt"), outputDirectory.fileName.toString())
        val keep = AutotestReportRetention.keepCount(System.getProperty(AutotestReportRetention.KEEP_PROPERTY))
        val pruned = AutotestReportRetention.prune(root, keep)
        if (pruned.isNotEmpty()) logger.info("Autotest kept the latest {} reports and removed {} older ones", keep, pruned.size)
        Runtime.getRuntime().addShutdownHook(Thread({ writeReport(aborted = true) }, "MahjongCraft autotest report"))
        logger.info("Autotest started; report folder {}", outputDirectory)
        CoroutineScope(SupervisorJob() + (client as Executor).asCoroutineDispatcher()).launch {
            unknownSelection()?.let(results::add)
            steps.forEachIndexed { index, step ->
                if (index == 0 || selection.includes(step.id)) runStep(client, step) else results += skipped(step.id)
            }
            writeReport(aborted = false)
            logger.info("Autotest finished; closing the game")
            client.stop()
        }
    }

    /**
     * 列出了不存在的步驟時的失敗結果，例如拼錯名稱。不檢查的話，測試只會執行進入世界就回報通過。
     *
     * @return 失敗結果。列出的步驟都存在時為 null。
     */
    private fun unknownSelection(): AutotestStepResult? {
        val known = steps.map(AutotestStep::id)
        val unknown = selection.unknownStepIds(known).ifEmpty { return null }
        logger.error("Autotest steps {} do not exist; known steps are {}", unknown, known)
        return AutotestStepResult(
            id = SELECTION_STEP_ID,
            status = AutotestStatus.FAILED,
            durationMillis = 0,
            checks = listOf(AutotestCheck("All selected steps exist", passed = false, detail = "Unknown: ${unknown.joinToString()}; known: ${known.joinToString()}")),
            screenshots = emptyList(),
            sounds = emptyList(),
            error = null,
        )
    }

    /**
     * 固定影響截圖的畫面設定。
     *
     * - 固定視野、介面縮放，並關掉視角搖晃、雲與新手教學提示。
     * - 設定存在自動測試獨立的執行目錄，不影響平常的開發執行。
     *
     * @param client 目前客戶端。
     */
    private fun applyDisplayOptions(client: Minecraft) {
        val options = client.options
        options.fov().set(FOV)
        options.guiScale().set(GUI_SCALE)
        options.bobView().set(false)
        options.cloudStatus().set(CloudStatus.OFF)
        options.tutorialStep = TutorialSteps.NONE
        client.tutorial.setStep(TutorialSteps.NONE)
        options.save()
        client.resizeDisplay()
    }

    /**
     * 執行一個步驟並記錄結果。步驟拋出的例外記為失敗，不中斷之後的步驟。
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
     * 寫出報告，只寫一次。
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
            notes = emptyList(),
            steps = stepResults,
        )
        Files.writeString(outputDirectory.resolve("report.json"), JSON.encodeToString(AutotestReport.serializer(), report))
        Files.writeString(outputDirectory.resolve("report.md"), autotestReportMarkdown(report))
    }

    private companion object {
        /** 列出不存在的步驟時，報告中記錄失敗的那一項的識別碼。 */
        const val SELECTION_STEP_ID = "selection"

        /** 開發執行以這個系統屬性傳入測試的目標。 */
        const val TARGET_PROPERTY = "mahjongcraft.autotest.target"

        /** 截圖時的視野角度。 */
        const val FOV = 70

        /** 截圖時的介面縮放。 */
        const val GUI_SCALE = 2

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
