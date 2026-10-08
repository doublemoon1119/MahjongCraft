package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessStepTimer
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.OngoingAiDecision
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.FabricHistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryDatabasePath
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.StressTestHistorySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Single
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 壓力測試一次執行的結果與目前數據。
 *
 * 除了場數與即時狀態，所有統計都只涵蓋暖機結束之後（見 [STRESS_WARMUP_TICKS]）。
 *
 * @property scenarioId 對局情境識別碼。
 * @property mode 桌數安排。
 * @property pace 每桌推進節奏。
 * @property historyMode 歷史處理方式。
 * @property running 是否仍在執行。
 * @property tables 目前同時進行的桌數。
 * @property completedMatches 開始以來已打完的場數。
 * @property failedMatches 開始以來卡住而中止的場數。
 * @property elapsedTicks 開始以來已經過的伺服器 tick 數。
 * @property warmupRemainingTicks 暖機剩餘的 tick 數；暖機已結束時為 0。
 * @property measuredSeconds 暖機結束後經過的實際秒數。
 * @property tickAverageMillis 最近一段時間每 tick 耗時的平均毫秒數。
 * @property tickP95Millis 最近一段時間每 tick 耗時的第 95 百分位毫秒數。
 * @property tickMaxMillis 最近一段時間每 tick 耗時的最大毫秒數。
 * @property stepAverageMillis 最近一段時間單桌單步耗時的平均毫秒數。
 * @property stepP95Millis 最近一段時間單桌單步耗時的第 95 百分位毫秒數。
 * @property stepMaxMillis 最近一段時間單桌單步耗時的最大毫秒數。
 * @property stepStages 單步各環節的耗時。
 * @property writerStages 歷史背景工作各環節的耗時；只列出有發生的環節。
 * @property eventsProduced 新加入待寫佇列的歷史事件數。
 * @property eventsWritten 背景工作處理完（寫入或編碼後確認移除）的歷史事件數。
 * @property maxStepsInTick 單一 tick 內推進的最多步數。
 * @property maxEventsInTick 單一 tick 內產生的最多歷史事件數。
 * @property measuredTicks 列入統計的 tick 數。
 * @property slowTicks 以 [STRESS_SLOW_TICK_THRESHOLDS_MILLIS] 的每個門檻毫秒數為鍵，耗時超過門檻的 tick 數。
 * @property gc 記憶體回收次數與耗時。
 * @property stutterLimitMillis 單一 tick 耗時超過這個毫秒數就算一次卡頓。
 * @property stutterTables 第一次判定持續卡頓時的桌數；尚未發生時為 null。
 * @property pendingEvents 目前歷史待寫佇列中的事件數。
 * @property pendingPeak 歷史待寫佇列的最大事件數。
 * @property pendingCapacity 歷史待寫佇列的容量。
 * @property lostSegments 出現序號缺口（遺失歷史）的場次數。
 * @property writerFailed 歷史背景工作是否發生錯誤或暫停。
 * @property usedMemoryMiB 目前 JVM 已使用的記憶體 MiB。
 * @property timeSeriesFile 每秒時間序列 CSV 的檔名；無法建立時為 null。
 * @property stopReason 停止原因；仍在執行時為 null。
 * @property sustainedTables 爬坡模式因安全閥停止時，已完整撐過一個階段的最大桌數；其他情況為 null。
 */
data class StressTestReport(
    val scenarioId: String,
    val mode: StressTestMode,
    val pace: StressTestPace,
    val historyMode: StressHistoryMode,
    val running: Boolean,
    val tables: Int,
    val completedMatches: Int,
    val failedMatches: Int,
    val elapsedTicks: Long,
    val warmupRemainingTicks: Long,
    val measuredSeconds: Double,
    val tickAverageMillis: Double,
    val tickP95Millis: Double,
    val tickMaxMillis: Double,
    val stepAverageMillis: Double,
    val stepP95Millis: Double,
    val stepMaxMillis: Double,
    val stepStages: Map<StressStepStage, TimingSummary>,
    val writerStages: Map<HistoryWriterStage, TimingSummary>,
    val eventsProduced: Long,
    val eventsWritten: Long,
    val maxStepsInTick: Int,
    val maxEventsInTick: Int,
    val measuredTicks: Long,
    val slowTicks: Map<Int, Long>,
    val gc: GcTotals,
    val stutterLimitMillis: Double,
    val stutterTables: Int?,
    val pendingEvents: Int,
    val pendingPeak: Int,
    val pendingCapacity: Int,
    val lostSegments: Int,
    val writerFailed: Boolean,
    val usedMemoryMiB: Long,
    val timeSeriesFile: String?,
    val stopReason: StressStopReason?,
    val sustainedTables: Int?,
)

/** 開始壓力測試的結果。 */
sealed interface StressTestStartResult {
    /** 已開始。 */
    data object Started : StressTestStartResult

    /** 已有壓力測試執行中。 */
    data object Busy : StressTestStartResult

    /** 不支援指定情境。 */
    data object InvalidScenario : StressTestStartResult

    /** 壓力測試資料庫無法開啟。 */
    data object StorageUnavailable : StressTestStartResult
}

/** 刪除壓力測試資料庫的結果。 */
enum class StressTestClearResult {
    /** 已刪除。 */
    CLEARED,

    /** 沒有壓力測試資料庫。 */
    NOTHING_TO_CLEAR,

    /** 壓力測試仍在執行，須先停止。 */
    RUNNING,
}

/**
 * 以伺服器 tick 驅動的壓力測試。
 *
 * 所有測試對局共用 [StressTestEnvironment] 的權威來源，並在伺服器主執行緒上依 [StressTestPace] 逐 tick 推進。每桌都對齊在
 * 同一個推進週期上（見 [alignedStepTick]），與正式對局由 tick 巡查在同一個 tick 推進所有對局的方式相同，因此每 tick 耗時直接
 * 反映 AI 與流程的負擔；歷史依 [StressHistoryMode] 經由同一個權威來源的待寫佇列交給背景工作處理。新桌每個 tick 最多建立
 * [STRESS_TABLES_CREATED_PER_TICK] 桌，打完或卡住的桌會從權威來源移除。
 *
 * 開始後先暖機 [STRESS_WARMUP_TICKS] 個 tick：期間維持初始桌數、不列入統計與卡頓判斷。每 tick 都檢查 [StressSafetyValve]，
 * 包括暖機期間：伺服器落後或歷史出問題就停止並保留報告，持續卡頓則在暖機後記下當時的桌數後繼續。不記錄歷史時不檢查
 * 歷史相關的門檻。執行期間每秒在存檔資料夾的時間序列 CSV 追加一列。
 *
 * 單步耗時超過 [SLOW_STEP_LOG_THRESHOLD] 時，在 log 記下該步最久的 AI 決策情境；另有背景工作每秒檢查進行中的 AI 決策，
 * 超過 [STUCK_DECISION_LOG_THRESHOLD] 仍未結束就先記下情境，即使該 tick 最後讓伺服器被強制關閉也留有線索。
 *
 * @property environments 建立壓力測試資料環境。
 * @property runtimes 在共用權威來源中建立全 AI 對局。
 * @property scope 執行 tick 工作的應用作用域。
 * @property dispatchers 切換到伺服器主執行緒。
 * @property historySource 讓對局歷史畫面的壓力測試範圍讀到目前的壓力測試資料庫。
 */
@Single
class StressTestController(
    private val environments: StressTestEnvironmentFactory,
    private val runtimes: FabricHistoryGenerationRuntimeFactory,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val historySource: StressTestHistorySource,
) {
    /** 記錄壓力測試開始、停止與報告的 logger。 */
    private val logger = mahjongCraftLogger(StressTestController::class)

    /**
     * 目前的壓力測試資料環境；尚未開始過、已刪除，或上一輪沒有寫進資料庫時為 null。設定時一併更新對局歷史畫面讀取的
     * 資料來源。
     */
    @Volatile var environment: StressTestEnvironment? = null
        private set(value) {
            field = value
            historySource.current = value?.historySource
        }

    /** 執行中的壓力測試；沒有執行時為 null。 */
    private var run: StressRun? = null

    /** 最近一次已停止的報告。 */
    private var lastReport: StressTestReport? = null

    /** 本 tick 開始的時刻。 */
    private var tickStart = TimeSource.Monotonic.markNow()

    /** 上一個 tick 的壓力測試工作是否仍在執行。 */
    private var processing = false

    /** 註冊 tick 量測、逐 tick 推進與伺服器關閉時的清理。 */
    fun registerTicking() {
        ServerTickEvents.START_SERVER_TICK.register { tickStart = TimeSource.Monotonic.markNow() }
        ServerTickEvents.END_SERVER_TICK.register(::onTickEnd)
        ServerLifecycleEvents.SERVER_STOPPING.register {
            runBlocking {
                stopRun(StressStopReason.SERVER_STOPPING)
                environment?.closeAndDelete()
                environment = null
            }
        }
    }

    /**
     * 開始壓力測試；上一輪的壓力測試資料庫會先刪除，寫入模式再重新建立。
     *
     * @param server 執行中的伺服器。
     * @param scenarioId 對局情境識別碼。
     * @param mode 桌數安排。
     * @param pace 每桌推進節奏。
     * @param historyMode 歷史處理方式。
     * @param thresholds 安全閥門檻。
     * @param starterId 下指令的玩家；停止時收到報告。null 表示由主控台執行。
     */
    suspend fun start(
        server: MinecraftServer,
        scenarioId: String,
        mode: StressTestMode,
        pace: StressTestPace,
        historyMode: StressHistoryMode,
        thresholds: StressSafetyThresholds,
        starterId: Uuid?,
    ): StressTestStartResult {
        if (run != null) return StressTestStartResult.Busy
        val scenario = HeadlessHistoryScenario.entries.firstOrNull { it.identifier == scenarioId } ?: return StressTestStartResult.InvalidScenario
        environment?.closeAndDelete()
        environment = null
        val opened = environments.open(server, historyMode) ?: return StressTestStartResult.StorageUnavailable
        environment = opened
        lastReport = null
        val timeSeries = openTimeSeries(server, scenario, pace, historyMode)
        run = StressRun(scenario, mode, pace, thresholds, opened, starterId, timeSeries).also { it.startDecisionWatch() }
        logger.info(
            "Stress test started: scenario={}, mode={}, pace={}, historyMode={}, timeSeries={}",
            scenario.identifier,
            mode,
            pace,
            historyMode,
            timeSeries?.path,
        )
        return StressTestStartResult.Started
    }

    /** 要求停止執行中的壓力測試；沒有執行中的測試時回傳 false。 */
    suspend fun stop(): Boolean = stopRun(StressStopReason.REQUESTED) != null

    /** 目前或最近一次的報告；從未執行過時為 null。 */
    fun report(): StressTestReport? = run?.report(running = true, stopReason = null) ?: lastReport

    /** 刪除壓力測試資料庫；測試仍在執行時不刪除。 */
    suspend fun clear(): StressTestClearResult {
        if (run != null) return StressTestClearResult.RUNNING
        val current = environment ?: return StressTestClearResult.NOTHING_TO_CLEAR
        current.closeAndDelete()
        environment = null
        lastReport = null
        return StressTestClearResult.CLEARED
    }

    /** 在存檔資料夾建立這一輪的時間序列 CSV；無法建立時記錄警告並回傳 null。 */
    private fun openTimeSeries(
        server: MinecraftServer,
        scenario: HeadlessHistoryScenario,
        pace: StressTestPace,
        historyMode: StressHistoryMode,
    ): StressTimeSeriesFile? {
        val timestamp = LocalDateTime.now().format(TIME_SERIES_TIMESTAMP)
        val path = FabricHistoryDatabasePath.resolve(server).resolveSibling("stress-test-$timestamp-${historyMode.commandName}.csv").toAbsolutePath().normalize()
        return try {
            StressTimeSeriesFile.create(path, scenario.identifier, pace, historyMode)
        } catch (error: IOException) {
            logger.warn("Stress test time series file could not be created; continuing without it", error)
            null
        }
    }

    /** tick 結束時推進到期的對局，並以本 tick 的耗時檢查安全閥。 */
    private fun onTickEnd(server: MinecraftServer) {
        val current = run ?: return
        if (processing) return
        processing = true
        scope.launch(dispatchers.main) {
            try {
                current.advance()
                val stopReason = current.recordTick(tickStart.elapsedNow().toDouble(DurationUnit.MILLISECONDS))
                if (stopReason != null) stopRun(stopReason)?.let { report -> notifyStarter(server, current.starterId, report) }
            } catch (error: Exception) {
                logger.error("Stress test tick failed; stopping the stress test", error)
                stopRun(StressStopReason.RUN_FAILED)?.let { report -> notifyStarter(server, current.starterId, report) }
            } finally {
                processing = false
            }
        }
    }

    /** 停止執行中的測試並保留報告：剩下的桌從權威來源移除，沒有寫進資料庫的環境一併關閉。沒有執行中的測試時回傳 null。 */
    private suspend fun stopRun(reason: StressStopReason): StressTestReport? {
        val current = run ?: return null
        run = null
        val report = current.report(running = false, stopReason = reason)
        current.closeTimeSeries()
        current.stopDecisionWatch()
        try {
            current.discardRemainingTables()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.error("Stress test tables could not be removed after the run stopped", error)
        }
        if (current.environment.historySource == null) {
            current.environment.closeAndDelete()
            if (environment === current.environment) environment = null
        }
        lastReport = report
        logger.info("Stress test stopped: {}", stressTestReportLogLine(report))
        return report
    }

    /** 把安全閥觸發後的最終報告送給下指令的玩家。 */
    private fun notifyStarter(server: MinecraftServer, starterId: Uuid?, report: StressTestReport) {
        val player = starterId?.let { server.playerManager.getPlayer(it.toJavaUuid()) } ?: return
        player.sendMessage(stressTestReportMessage(report))
    }

    /**
     * 一次壓力測試的執行狀態；只在伺服器主執行緒上存取。
     *
     * @property scenario 對局情境。
     * @property mode 桌數安排。
     * @property pace 每桌推進節奏。
     * @property thresholds 安全閥門檻。
     * @property environment 共用的權威來源、計時器與歷史背景工作。
     * @property starterId 下指令的玩家。
     * @property timeSeries 每秒時間序列 CSV；無法建立或寫入失敗後為 null。
     */
    private inner class StressRun(
        val scenario: HeadlessHistoryScenario,
        val mode: StressTestMode,
        val pace: StressTestPace,
        val thresholds: StressSafetyThresholds,
        val environment: StressTestEnvironment,
        val starterId: Uuid?,
        private var timeSeries: StressTimeSeriesFile?,
    ) {
        /** 同時進行中的桌。 */
        private val tables = mutableListOf<StressTable>()

        /** 開始的時刻。 */
        private val startMark = TimeSource.Monotonic.markNow()

        /** 時間序列檔名，停止後仍保留在報告中。 */
        private val timeSeriesFileName = timeSeries?.path?.fileName?.toString()

        /** 已經過的 tick 數。 */
        private var elapsedTicks = 0L

        /** 已打完的場數。 */
        private var completedMatches = 0

        /** 卡住而中止的場數。 */
        private var failedMatches = 0

        /** 暖機結束的時刻；仍在暖機時為 null。 */
        private var measureStart: TimeMark? = null

        /** 暖機結束時背景工作已處理的事件數。 */
        private var writtenBaseline = 0L

        /** 暖機結束時的記憶體回收累計值。 */
        private var gcBaseline = GcTotals(0, 0)

        /** 最近一段時間每 tick 耗時。 */
        private val tickSamples = RollingSamples(SAMPLE_WINDOW_TICKS)

        /** 最近一段時間單桌單步耗時。 */
        private val stepSamples = RollingSamples(STEP_SAMPLE_CAPACITY)

        /** 單步各環節的耗時。 */
        private val stepStages = StressStepStage.entries.associateWith { TimingAccumulator() }

        /** 新加入待寫佇列的歷史事件數。 */
        private var eventsProduced = 0L

        /** 單一 tick 內推進的最多步數。 */
        private var maxStepsInTick = 0

        /** 單一 tick 內產生的最多歷史事件數。 */
        private var maxEventsInTick = 0

        /** 列入統計的 tick 數。 */
        private var measuredTicks = 0L

        /** 各門檻的超時 tick 數，順序與 [STRESS_SLOW_TICK_THRESHOLDS_MILLIS] 相同。 */
        private val slowTicks = LongArray(STRESS_SLOW_TICK_THRESHOLDS_MILLIS.size)

        /** 本 tick 推進的步數。 */
        private var tickSteps = 0

        /** 本 tick 產生的歷史事件數。 */
        private var tickEvents = 0

        /** 目前歷史待寫佇列的事件數。 */
        private var pendingEvents = 0

        /** 歷史待寫佇列的最大事件數。 */
        private var pendingPeak = 0

        /** 出現序號缺口的場次數。 */
        private var lostSegments = 0

        /** 歷史背景工作是否失敗。 */
        private var writerFailed = false

        /** 安全閥。 */
        private val valve = StressSafetyValve(thresholds)

        /** 第一次判定持續卡頓時的桌數。 */
        private var stutterTables: Int? = null

        /** 檢查進行中 AI 決策的背景工作。 */
        private var decisionWatch: Job? = null

        /** 目前這一列時間序列的彙總。 */
        private var bucket = TimeSeriesBucket()

        /** 上一列時間序列寫出的時刻。 */
        private var lastRowMark = TimeSource.Monotonic.markNow()

        /** 上一列時間序列寫出時背景工作已處理的事件數。 */
        private var lastRowWritten = 0L

        /** 上一列時間序列寫出時的記憶體回收累計值。 */
        private var lastRowGc = GcTotals.read()

        /** 是否已結束暖機。 */
        private val measuring: Boolean get() = measureStart != null

        /** 補足目標桌數，推進到期的桌，並更新歷史狀態。 */
        suspend fun advance() {
            elapsedTicks++
            if (elapsedTicks == STRESS_WARMUP_TICKS + 1) beginMeasuring()
            tickSteps = 0
            tickEvents = 0
            val timer = environment.stepTimer
            repeat(minOf(targetTables() - tables.size, STRESS_TABLES_CREATED_PER_TICK)) {
                timer.reset()
                val runtime = runtimes.createIn(scenario, environment.store, timer)
                tables += StressTable(runtime, nextStepTick = alignedStepTick(elapsedTicks, pace.stepIntervalTicks))
                tickEvents += timer.historyEvents
            }
            val iterator = tables.iterator()
            while (iterator.hasNext()) {
                val table = iterator.next()
                if (table.nextStepTick > elapsedTicks) continue
                timer.reset()
                val stepStart = TimeSource.Monotonic.markNow()
                val progressed = table.runtime.step()
                val stepTime = stepStart.elapsedNow()
                recordStep(stepTime, timer)
                if (stepTime >= SLOW_STEP_LOG_THRESHOLD) logSlowStep(stepTime, timer)
                table.nextStepTick = elapsedTicks + pace.stepIntervalTicks
                if (table.runtime.currentGame() == null) {
                    completedMatches++
                    iterator.remove()
                    discardStressTables(environment.store, listOf(table.runtime.venueId))
                } else if (progressed) {
                    table.stalledSteps = 0
                } else if (++table.stalledSteps >= MAX_STALLED_STEPS) {
                    failedMatches++
                    logger.warn("Stress test match made no progress for {} steps; dropping it", MAX_STALLED_STEPS)
                    iterator.remove()
                    discardStressTables(environment.store, listOf(table.runtime.venueId))
                }
            }
            val recording = environment.store.snapshot().historyRecordingState
            pendingEvents = recording.pendingEvents.size
            lostSegments = recording.firstMissingSequenceByMatchId.size
            if (elapsedTicks % WRITER_CHECK_INTERVAL_TICKS == 0L) writerFailed = environment.historyFailed()
        }

        /** 把剩下的桌從權威來源移除；未結束的對局記為未完成，待寫歷史仍交給背景工作處理。 */
        suspend fun discardRemainingTables() {
            discardStressTables(environment.store, tables.map { it.runtime.venueId })
            tables.clear()
        }

        /** 暖機結束：記下基準值並清除暖機期間的背景工作統計。 */
        private fun beginMeasuring() {
            environment.writerTimer.resetStages()
            writtenBaseline = environment.writerTimer.eventsWritten()
            gcBaseline = GcTotals.read()
            measureStart = TimeSource.Monotonic.markNow()
        }

        /** 在 log 記下一個過久的步驟與該步最久的 AI 決策情境。 */
        private fun logSlowStep(total: Duration, timer: HeadlessStepTimer) {
            logger.warn(
                "Stress test step took {} ms (aiDecision={} ms, snapshotSync={} ms, historyRecording={} ms); slowest AI decision: {} ms, {}",
                total.inWholeMilliseconds,
                timer.aiDecision.inWholeMilliseconds,
                timer.snapshotSync.inWholeMilliseconds,
                timer.historyRecording.inWholeMilliseconds,
                timer.slowestAiDecision?.duration?.inWholeMilliseconds,
                timer.slowestAiDecision?.let { stressAiDecisionLogText(it.context) },
            )
        }

        /** 開始每秒檢查進行中的 AI 決策；同一次決策只記錄一次。 */
        fun startDecisionWatch() {
            decisionWatch = scope.launch(dispatchers.io) {
                var reported: OngoingAiDecision? = null
                while (isActive) {
                    delay(DECISION_WATCH_INTERVAL)
                    val ongoing = environment.stepTimer.ongoingAiDecision ?: continue
                    val elapsed = ongoing.startedAt.elapsedNow()
                    if (ongoing === reported || elapsed < STUCK_DECISION_LOG_THRESHOLD) continue
                    reported = ongoing
                    logger.warn("Stress test AI decision still running after {} s: {}", elapsed.inWholeSeconds, stressAiDecisionLogText(ongoing.context))
                }
            }
        }

        /** 停止檢查進行中的 AI 決策。 */
        fun stopDecisionWatch() {
            decisionWatch?.cancel()
            decisionWatch = null
        }

        /** 記錄一步的總耗時與各環節耗時。 */
        private fun recordStep(total: Duration, timer: HeadlessStepTimer) {
            val stages = mapOf(
                StressStepStage.AI_DECISION to timer.aiDecision,
                StressStepStage.RULES_AND_STATE to (total - timer.aiDecision - timer.snapshotSync - timer.historyRecording).coerceAtLeast(Duration.ZERO),
                StressStepStage.SNAPSHOT_SYNC to timer.snapshotSync,
                StressStepStage.HISTORY_RECORDING to timer.historyRecording,
            )
            tickSteps++
            tickEvents += timer.historyEvents
            bucket.addStep(total, stages)
            if (!measuring) return
            stepSamples.add(total.toDouble(DurationUnit.MILLISECONDS))
            stages.forEach { (stage, duration) -> stepStages.getValue(stage).add(duration) }
        }

        /** 記錄本 tick 耗時、寫出到期的時間序列，並回傳安全閥的停止原因。 */
        fun recordTick(msptMillis: Double): StressStopReason? {
            bucket.addTick(msptMillis, tickEvents)
            if (measuring) {
                tickSamples.add(msptMillis)
                measuredTicks++
                STRESS_SLOW_TICK_THRESHOLDS_MILLIS.forEachIndexed { index, threshold -> if (msptMillis > threshold) slowTicks[index]++ }
                maxStepsInTick = maxOf(maxStepsInTick, tickSteps)
                maxEventsInTick = maxOf(maxEventsInTick, tickEvents)
                eventsProduced += tickEvents
                pendingPeak = maxOf(pendingPeak, pendingEvents)
            }
            if (lastRowMark.elapsedNow() >= TIME_SERIES_INTERVAL) writeTimeSeriesRow()
            val tickStop = valve.recordTick(msptMillis, countStutter = measuring)
            if (measuring && stutterTables == null && valve.stuttering) stutterTables = tables.size
            return tickStop ?: if (environment.historyMode == StressHistoryMode.OFF) {
                null
            } else {
                valve.checkHistory(pendingEvents, environment.store.maxPendingHistoryEvents, lostSegments, writerFailed)
            }
        }

        /** 寫出一列時間序列並開始下一段彙總；寫入失敗時記錄警告並停止寫入時間序列。 */
        private fun writeTimeSeriesRow() {
            val written = environment.writerTimer.eventsWritten()
            val gc = GcTotals.read()
            val row = bucket.toRow(
                elapsedSeconds = startMark.elapsedNow().toDouble(DurationUnit.SECONDS),
                eventsWritten = written - lastRowWritten,
                gc = gc - lastRowGc,
            )
            bucket = TimeSeriesBucket()
            lastRowMark = TimeSource.Monotonic.markNow()
            lastRowWritten = written
            lastRowGc = gc
            val file = timeSeries ?: return
            try {
                file.append(row)
            } catch (error: IOException) {
                logger.warn("Stress test time series could not be written; continuing without it", error)
                closeTimeSeries()
            }
        }

        /** 關閉時間序列檔。 */
        fun closeTimeSeries() {
            val file = timeSeries ?: return
            timeSeries = null
            try {
                file.close()
            } catch (error: IOException) {
                logger.warn("Stress test time series could not be closed", error)
            }
        }

        /** 目前應同時進行的桌數；爬坡在暖機結束後才開始加桌，最多 [STRESS_MAX_TABLES] 桌。 */
        private fun targetTables(): Int = when (mode) {
            is StressTestMode.Fixed -> mode.tables
            is StressTestMode.Ramp -> mode.plan.tablesAt(elapsedTicks - STRESS_WARMUP_TICKS).coerceAtMost(STRESS_MAX_TABLES)
        }

        /** 目前的數據。 */
        fun report(running: Boolean, stopReason: StressStopReason?): StressTestReport {
            val runtime = Runtime.getRuntime()
            val measured = measuring
            return StressTestReport(
                scenarioId = scenario.identifier,
                mode = mode,
                pace = pace,
                historyMode = environment.historyMode,
                running = running,
                tables = tables.size,
                completedMatches = completedMatches,
                failedMatches = failedMatches,
                elapsedTicks = elapsedTicks,
                warmupRemainingTicks = (STRESS_WARMUP_TICKS - elapsedTicks).coerceAtLeast(0),
                measuredSeconds = measureStart?.elapsedNow()?.toDouble(DurationUnit.SECONDS) ?: 0.0,
                tickAverageMillis = tickSamples.average(),
                tickP95Millis = tickSamples.percentile(P95),
                tickMaxMillis = tickSamples.max(),
                stepAverageMillis = stepSamples.average(),
                stepP95Millis = stepSamples.percentile(P95),
                stepMaxMillis = stepSamples.max(),
                stepStages = stepStages.mapValues { (_, accumulator) -> accumulator.summary() },
                writerStages = if (measured) environment.writerTimer.stageSummaries() else emptyMap(),
                eventsProduced = eventsProduced,
                eventsWritten = if (measured) environment.writerTimer.eventsWritten() - writtenBaseline else 0,
                maxStepsInTick = maxStepsInTick,
                maxEventsInTick = maxEventsInTick,
                measuredTicks = measuredTicks,
                slowTicks = STRESS_SLOW_TICK_THRESHOLDS_MILLIS.withIndex().associate { (index, threshold) -> threshold to slowTicks[index] },
                gc = if (measured) GcTotals.read() - gcBaseline else GcTotals(0, 0),
                stutterLimitMillis = thresholds.stutterLimitMillis,
                stutterTables = stutterTables,
                pendingEvents = pendingEvents,
                pendingPeak = pendingPeak,
                pendingCapacity = environment.store.maxPendingHistoryEvents,
                lostSegments = lostSegments,
                writerFailed = writerFailed,
                usedMemoryMiB = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MIB,
                timeSeriesFile = timeSeriesFileName,
                stopReason = stopReason,
                sustainedTables = (mode as? StressTestMode.Ramp)
                    ?.takeIf { stopReason in VALVE_STOP_REASONS }
                    ?.plan
                    ?.sustainedTablesAt(elapsedTicks - STRESS_WARMUP_TICKS)
                    ?.coerceAtMost(STRESS_MAX_TABLES),
            )
        }

        /** 一列時間序列期間的彙總。 */
        private inner class TimeSeriesBucket {
            /** tick 數。 */
            private var ticks = 0

            /** tick 耗時總毫秒數。 */
            private var tickTotalMillis = 0.0

            /** tick 耗時最大毫秒數。 */
            private var tickMaxMillis = 0.0

            /** 推進的步數。 */
            private var steps = 0

            /** 單步總耗時。 */
            private var stepTotal = Duration.ZERO

            /** 各環節總耗時。 */
            private val stageTotals = StressStepStage.entries.associateWithTo(mutableMapOf()) { Duration.ZERO }

            /** 產生的歷史事件數。 */
            private var eventsProduced = 0

            /** 加入一步。 */
            fun addStep(total: Duration, stages: Map<StressStepStage, Duration>) {
                steps++
                stepTotal += total
                stages.forEach { (stage, duration) -> stageTotals[stage] = stageTotals.getValue(stage) + duration }
            }

            /** 加入一個 tick。 */
            fun addTick(msptMillis: Double, events: Int) {
                ticks++
                tickTotalMillis += msptMillis
                tickMaxMillis = maxOf(tickMaxMillis, msptMillis)
                eventsProduced += events
            }

            /** 以目前狀態組成一列。 */
            fun toRow(elapsedSeconds: Double, eventsWritten: Long, gc: GcTotals): StressTimeSeriesRow {
                val runtime = Runtime.getRuntime()
                return StressTimeSeriesRow(
                    elapsedSeconds = elapsedSeconds,
                    warmup = !measuring,
                    tables = tables.size,
                    ticks = ticks,
                    tickAverageMillis = if (ticks == 0) 0.0 else tickTotalMillis / ticks,
                    tickMaxMillis = tickMaxMillis,
                    steps = steps,
                    stepAverageMillis = if (steps == 0) 0.0 else stepTotal.toDouble(DurationUnit.MILLISECONDS) / steps,
                    stageTotalMillis = stageTotals.mapValues { (_, duration) -> duration.toDouble(DurationUnit.MILLISECONDS) },
                    eventsProduced = eventsProduced,
                    eventsWritten = eventsWritten,
                    pendingEvents = pendingEvents,
                    lostSegments = lostSegments,
                    completedMatches = completedMatches,
                    usedMemoryMiB = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MIB,
                    gc = gc,
                )
            }
        }
    }

    /**
     * 一桌測試對局。
     *
     * @property runtime 對局環境。
     * @property nextStepTick 下一次推進的 tick。
     */
    private class StressTable(
        val runtime: HeadlessHistoryMatchRuntime,
        var nextStepTick: Long,
    ) {
        /** 連續沒有進展的步數。 */
        var stalledSteps = 0
    }

    private companion object {
        /** 每 tick 耗時的統計視窗：最近 10 秒。 */
        const val SAMPLE_WINDOW_TICKS = 200

        /** 單步耗時保留的筆數。 */
        const val STEP_SAMPLE_CAPACITY = 2_000

        /** 連續沒有進展多少步就視為卡住。 */
        const val MAX_STALLED_STEPS = 200

        /** 檢查歷史背景工作狀態的間隔 tick 數。 */
        const val WRITER_CHECK_INTERVAL_TICKS = 20L

        /** 第 95 百分位。 */
        const val P95 = 0.95

        /** 位元組換算 MiB。 */
        const val BYTES_PER_MIB = 1_048_576L

        /** 單步耗時達到這個長度時記錄 log。 */
        val SLOW_STEP_LOG_THRESHOLD: Duration = 1.seconds

        /** 進行中的 AI 決策超過這個長度仍未結束時記錄 log。 */
        val STUCK_DECISION_LOG_THRESHOLD: Duration = 10.seconds

        /** 檢查進行中 AI 決策的間隔。 */
        val DECISION_WATCH_INTERVAL: Duration = 1.seconds

        /** 時間序列每列涵蓋的實際時間。 */
        val TIME_SERIES_INTERVAL: Duration = 1.seconds

        /** 時間序列檔名中的開始時間格式。 */
        val TIME_SERIES_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /** 由安全閥觸發的停止原因；爬坡模式只在這些情況回報最大可承受桌數。 */
        val VALVE_STOP_REASONS: Set<StressStopReason> = setOf(
            StressStopReason.FALLING_BEHIND,
            StressStopReason.HISTORY_BACKLOG,
            StressStopReason.HISTORY_LOST,
            StressStopReason.HISTORY_WRITER_FAILED,
        )
    }
}
