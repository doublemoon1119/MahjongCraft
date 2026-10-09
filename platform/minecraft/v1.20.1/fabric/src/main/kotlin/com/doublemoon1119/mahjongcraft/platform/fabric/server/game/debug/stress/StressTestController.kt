package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessStepTimer
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.OngoingAiDecision
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionObserver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionOutcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionReporter
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.GameAdvanceRotation
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.FabricHistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryDatabasePath
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.StressTestHistorySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
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
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
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
 * 除了場數與即時狀態，所有統計都只涵蓋暖機結束之後（見 [StressRunOptions.warmupSeconds]）。
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
 * @property warmupTicks 暖機的 tick 數；0 表示不暖機。
 * @property warmupRemainingTicks 暖機剩餘的 tick 數；暖機已結束時為 0。
 * @property measuredSeconds 暖機結束後經過的實際秒數。
 * @property tickAverageMillis 最近一段時間 tick 本身處理時間的平均毫秒數；只供診斷，不含 tick 之間執行的工作。
 * @property tickP95Millis 最近一段時間 tick 本身處理時間的第 95 百分位毫秒數。
 * @property tickMaxMillis 最近一段時間 tick 本身處理時間的最大毫秒數。
 * @property tickIntervalAverageMillis 最近一段時間實際 tick 間隔（相鄰兩個 tick 開始的時間差）的平均毫秒數。
 * @property tickIntervalP95Millis 最近一段時間實際 tick 間隔的第 95 百分位毫秒數。
 * @property tickIntervalMaxMillis 最近一段時間實際 tick 間隔的最大毫秒數。
 * @property lagMillis 目前相對每 tick 50 ms 節奏的落後毫秒數；見 [StressSafetyValve]。
 * @property advanceMillisPerSecond 暖機結束後每秒 MahjongCraft 推進（各桌推進協程在主執行緒上實際執行的片段）的平均毫秒數。
 * @property stepAverageMillis 最近一段時間單桌單步 MahjongCraft 推進耗時的平均毫秒數：這一步在主執行緒上實際執行的片段加總，
 *   不含等待 AI 結果的時間。
 * @property stepP95Millis 最近一段時間單桌單步 MahjongCraft 推進耗時的第 95 百分位毫秒數。
 * @property stepMaxMillis 最近一段時間單桌單步 MahjongCraft 推進耗時的最大毫秒數。
 * @property stepStages 單步各環節的耗時；見 [StressStepStage]。
 * @property historyRecording 每 tick 歷史記錄的耗時。
 * @property aiDecisions 有結果的 AI 決策數。
 * @property aiTimeouts 等待超過上限而使用固定命令的 AI 決策數。
 * @property aiPreviousStillRunning 同一局先前逾時的策略呼叫尚未結束，直接使用固定命令的 AI 決策數。
 * @property aiLatencyAverageMillis 暖機結束後 AI 決策延遲（開始決策到得到結果，含排隊）的平均毫秒數。
 * @property aiLatencyP95Millis 暖機結束後 AI 決策延遲的第 95 百分位毫秒數。
 * @property aiLatencyP99Millis 暖機結束後 AI 決策延遲的第 99 百分位毫秒數。
 * @property aiLatencyMaxMillis 暖機結束後 AI 決策延遲的最大毫秒數。
 * @property staleDecisions 權威遊戲已改變而沒有套用的 AI 決策數。
 * @property unfinishedStrategyCalls 目前尚未真正結束的策略工作數。
 * @property unfinishedStrategyPeak 暖機結束後同時存在、尚未真正結束的策略工作數的最大值，包含 tick 之間短暫出現的工作。
 * @property strategyCapacity 同時存在、尚未結束的策略工作上限。
 * @property writerStages 歷史背景工作各環節的耗時；只列出有發生的環節。
 * @property eventsProduced 新加入待寫佇列的歷史事件數。
 * @property eventsWritten 背景工作處理完（寫入或編碼後確認移除）的歷史事件數。
 * @property maxStepsInTick 單一 tick 內完成的最多步數。
 * @property maxEventsInTick 單一 tick 內產生的最多歷史事件數。
 * @property measuredTicks 列入統計的 tick 數。
 * @property slowTicks 以 [STRESS_SLOW_TICK_THRESHOLDS_MILLIS] 的每個門檻毫秒數為鍵，耗時超過門檻的 tick 數。
 * @property gc 記憶體回收次數與耗時。
 * @property stutterLimitMillis 單一 tick 間隔超過這個毫秒數就算一次卡頓。
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
    val warmupTicks: Long,
    val warmupRemainingTicks: Long,
    val measuredSeconds: Double,
    val tickAverageMillis: Double,
    val tickP95Millis: Double,
    val tickMaxMillis: Double,
    val tickIntervalAverageMillis: Double,
    val tickIntervalP95Millis: Double,
    val tickIntervalMaxMillis: Double,
    val lagMillis: Double,
    val advanceMillisPerSecond: Double,
    val stepAverageMillis: Double,
    val stepP95Millis: Double,
    val stepMaxMillis: Double,
    val stepStages: Map<StressStepStage, TimingSummary>,
    val historyRecording: TimingSummary,
    val aiDecisions: Long,
    val aiTimeouts: Long,
    val aiPreviousStillRunning: Long,
    val aiLatencyAverageMillis: Double,
    val aiLatencyP95Millis: Double,
    val aiLatencyP99Millis: Double,
    val aiLatencyMaxMillis: Double,
    val staleDecisions: Long,
    val unfinishedStrategyCalls: Int,
    val unfinishedStrategyPeak: Int,
    val strategyCapacity: Int,
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
 * 所有測試對局共用 [StressTestEnvironment] 的權威來源，依 [StressTestPace] 逐 tick 推進。每桌推進的 tick 由與正式排程相同的
 * [GameAdvanceRotation] 決定（位置分配、待推進標記與每 tick 推進上限）；與正式環境相同，每桌在伺服器主執行緒上各自的協程中推進、
 * 不互相等待，AI 策略在背景執行緒上計算，因此每 tick 耗時直接反映正式環境中主執行緒的負擔。歷史依 [StressHistoryMode] 經由同一個
 * 權威來源的待寫佇列交給背景工作處理。新桌每個 tick 最多建立
 * [STRESS_TABLES_CREATED_PER_TICK] 桌，打完或卡住的桌會從權威來源移除。
 *
 * 開始後先依 [StressRunOptions.warmupSeconds] 暖機：期間維持初始桌數、不列入統計與卡頓判斷。每 tick 都檢查 [StressSafetyValve]，
 * 包括暖機期間：伺服器落後或歷史出問題就停止並保留報告，持續卡頓則在暖機後記下當時的桌數後繼續。不記錄歷史時不檢查
 * 歷史相關的門檻。執行期間每秒在存檔資料夾的時間序列 CSV 追加一列。
 *
 * 單步在主執行緒上的耗時超過 [SLOW_STEP_LOG_THRESHOLD] 時，在 log 記下該步最久的 AI 決策情境；另每秒檢查各桌進行中的 AI 決策，
 * 超過 [STUCK_DECISION_LOG_THRESHOLD] 仍未結束就先記下情境。
 *
 * @property environments 建立壓力測試資料環境。
 * @property runtimes 在共用權威來源中建立全 AI 對局。
 * @property scope 執行 tick 工作的應用作用域。
 * @property dispatchers 切換到伺服器主執行緒。
 * @property historySource 讓對局歷史畫面的壓力測試範圍讀到目前的壓力測試資料庫。
 * @param aiDecisionReporter 記錄 AI 決策逾時與舊策略呼叫仍未結束的情況。
 */
@Single
class StressTestController(
    private val environments: StressTestEnvironmentFactory,
    private val runtimes: FabricHistoryGenerationRuntimeFactory,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val historySource: StressTestHistorySource,
    aiDecisionReporter: AiDecisionReporter,
) {
    /** 記錄壓力測試開始、停止與報告的 logger。 */
    private val logger = mahjongCraftLogger(StressTestController::class)

    /** 同時存在、尚未結束的策略工作上限。 */
    private val strategyCapacity = dispatchers.aiDecisionParallelism * AiDecisionExecutor.CAPACITY_PER_THREAD

    /** 各輪共用的 AI 決策執行器與決策結果的分派；上一輪留下的策略工作仍計入下一輪的名額。 */
    private val sharedDecisions = StressAiDecisions { observer ->
        AiDecisionExecutor(
            dispatcher = dispatchers.aiDecision,
            capacity = strategyCapacity,
            reporter = aiDecisionReporter,
            observer = observer,
        )
    }

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

    /** 本 tick 與上一個 tick 開始的時間差；伺服器啟動後的第一個 tick 為 null。 */
    private var tickInterval: Duration? = null

    /** 上一個 tick 的壓力測試工作是否仍在執行。 */
    private var processing = false

    /** 是否已經過至少一個 tick 的開始。 */
    private var started = false

    /** 註冊 tick 量測、逐 tick 推進與伺服器關閉時的清理。 */
    fun registerTicking() {
        ServerTickEvents.START_SERVER_TICK.register {
            val now = TimeSource.Monotonic.markNow()
            tickInterval = tickStart.elapsedNow().takeIf { started }
            tickStart = now
            started = true
        }
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
     * @param options 節奏、歷史處理方式、卡頓門檻與暖機秒數。
     * @param starterId 下指令的玩家；停止時收到報告。null 表示由主控台執行。
     */
    suspend fun start(
        server: MinecraftServer,
        scenarioId: String,
        mode: StressTestMode,
        options: StressRunOptions,
        starterId: Uuid?,
    ): StressTestStartResult {
        if (run != null) return StressTestStartResult.Busy
        val scenario = HeadlessHistoryScenario.entries.firstOrNull { it.identifier == scenarioId } ?: return StressTestStartResult.InvalidScenario
        environment?.closeAndDelete()
        environment = null
        val opened = environments.open(server, options.historyMode) ?: return StressTestStartResult.StorageUnavailable
        environment = opened
        lastReport = null
        val timeSeries = openTimeSeries(server, scenario, options.pace, options.historyMode)
        run = StressRun(scenario, mode, options, opened, starterId, timeSeries).also { it.startDecisionWatch() }
        logger.info(
            "Stress test started: scenario={}, mode={}, options={}, timeSeries={}",
            scenario.identifier,
            mode,
            options,
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
        current.onServerTick()
        if (processing) return
        processing = true
        scope.launch(dispatchers.main) {
            try {
                current.advance()
                val stopReason = current.recordTick(
                    msptMillis = tickStart.elapsedNow().toDouble(DurationUnit.MILLISECONDS),
                    intervalMillis = tickInterval?.toDouble(DurationUnit.MILLISECONDS),
                )
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
     * 每桌的推進在各自的協程中進行：輪到的桌啟動推進後不等待，上一步還沒結束的桌這一輪略過。AI 策略在
     * [CoroutineDispatchers.aiDecision] 上由各輪共用的 [AiDecisionExecutor] 呼叫（名額規則與正式環境相同），這一輪的對局在建立時
     * 登記，決策結果只算進這一輪。
     *
     * @property scenario 對局情境。
     * @property mode 桌數安排。
     * @property options 節奏、歷史處理方式、卡頓門檻與暖機秒數。
     * @property environment 共用的權威來源、歷史計時器與歷史背景工作。
     * @property starterId 下指令的玩家。
     * @property timeSeries 每秒時間序列 CSV；無法建立或寫入失敗後為 null。
     */
    private inner class StressRun(
        val scenario: HeadlessHistoryScenario,
        val mode: StressTestMode,
        val options: StressRunOptions,
        val environment: StressTestEnvironment,
        val starterId: Uuid?,
        private var timeSeries: StressTimeSeriesFile?,
    ) {
        /** 同時進行中的桌，以場地識別碼索引。 */
        private val tables = LinkedHashMap<Uuid, StressTable>()

        /** 同時進行中的桌，以對局識別碼索引，供 AI 決策的量測對應到桌。 */
        private val tablesByGameId = HashMap<Uuid, StressTable>()

        /** 每桌推進節奏。 */
        private val pace = options.pace

        /** 各桌推進的 tick 位置與待推進標記；與正式排程使用相同的政策。 */
        private val rotation = GameAdvanceRotation(pace.stepIntervalTicks)

        /** 各桌推進協程的父工作；停止時一併取消。 */
        private val stepJob = SupervisorJob(scope.coroutineContext[Job])

        /** 啟動各桌推進協程的作用域；推進協程在主執行緒上執行的每個片段都會計時。 */
        private val stepScope = CoroutineScope(scope.coroutineContext + stepJob + SegmentTimingDispatcher(dispatchers.main))

        /** 各輪共用的 AI 決策執行器。 */
        private val decisions = sharedDecisions.executor

        /** 接收這一輪對局的 AI 決策結果；登記與取消登記使用同一個實例。 */
        private val decisionObserver = AiDecisionObserver(::onDecision)

        init {
            // 策略工作峰值從這一輪開始重新起算。
            decisions.takeUnfinishedStrategyPeak()
        }

        /** 安全閥門檻。 */
        private val thresholds = options.thresholds()

        /** 暖機的 tick 數。 */
        private val warmupTicks = options.warmupTicks

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

        /** 某一桌推進時發生的未預期錯誤；下一個 tick 據此停止測試。 */
        private var stepFailure: Exception? = null

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

        /** 每 tick 歷史記錄的耗時。 */
        private val historyRecording = TimingAccumulator()

        /** 暖機結束後全部 AI 決策的延遲。 */
        private val aiLatency = MillisHistogram()

        /** 有結果的 AI 決策數。 */
        private var aiDecisions = 0L

        /** 逾時而使用固定命令的 AI 決策數。 */
        private var aiTimeouts = 0L

        /** 舊策略呼叫尚未結束而直接使用固定命令的 AI 決策數。 */
        private var aiPreviousStillRunning = 0L

        /** 過期而沒有套用的 AI 決策數。 */
        private var staleDecisions = 0L

        /** 尚未結束的策略工作數的最大值。 */
        private var unfinishedStrategyPeak = 0

        /** 新加入待寫佇列的歷史事件數。 */
        private var eventsProduced = 0L

        /** 單一 tick 內完成的最多步數。 */
        private var maxStepsInTick = 0

        /** 單一 tick 內產生的最多歷史事件數。 */
        private var maxEventsInTick = 0

        /** 列入統計的 tick 數。 */
        private var measuredTicks = 0L

        /** 各門檻的超時 tick 數，順序與 [STRESS_SLOW_TICK_THRESHOLDS_MILLIS] 相同。 */
        private val slowTicks = LongArray(STRESS_SLOW_TICK_THRESHOLDS_MILLIS.size)

        /** 本 tick 完成的步數。 */
        private var tickSteps = 0

        /** 本 tick 推進協程在主執行緒上執行的總時間。 */
        private var tickAdvance = Duration.ZERO

        /** 暖機結束後推進協程在主執行緒上執行的總時間。 */
        private var advanceTotal = Duration.ZERO

        /** 最近一段時間的實際 tick 間隔。 */
        private val intervalSamples = RollingSamples(SAMPLE_WINDOW_TICKS)

        /** 上一次的目標桌數；爬坡進入新的階段時據此重設落後量。 */
        private var lastTargetTables: Int? = null

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

        /** 伺服器前進一個 tick：把輪到的桌標為待推進；上一個 tick 的工作仍在等待時也要標記。 */
        fun onServerTick() = rotation.advanceTick()

        /** 補足目標桌數，為輪到的桌啟動推進（不等待），並更新歷史狀態。 */
        suspend fun advance() {
            elapsedTicks++
            if (elapsedTicks == warmupTicks + 1) beginMeasuring()
            val target = targetTables()
            if (lastTargetTables != null && target != lastTargetTables) valve.resetLag()
            lastTargetTables = target
            repeat(minOf(target - tables.size, STRESS_TABLES_CREATED_PER_TICK)) {
                val timer = HeadlessStepTimer()
                val runtime = runtimes.createIn(scenario, environment.store, timer, decisions)
                val gameId = runtime.currentGame()?.id
                val table = StressTable(runtime, timer, gameId)
                tables[runtime.venueId] = table
                gameId?.let {
                    tablesByGameId[it] = table
                    sharedDecisions.register(it, decisionObserver)
                }
            }
            rotation.syncGames(tables.keys)
            rotation.takeDue().forEach { venueId ->
                val table = tables[venueId] ?: return@forEach
                if (table.stepping) return@forEach
                table.stepping = true
                table.stepTime = Duration.ZERO
                stepScope.launch(StepTiming { duration -> onSegment(table, duration) }) { step(venueId, table) }
            }
            val recording = environment.store.snapshot().historyRecordingState
            pendingEvents = recording.pendingEvents.size
            lostSegments = recording.firstMissingSequenceByMatchId.size
            if (elapsedTicks % WRITER_CHECK_INTERVAL_TICKS == 0L) writerFailed = environment.historyFailed()
        }

        /** 推進一桌一步；這一步的統計在它最後一個片段結束時記錄。未預期的錯誤留給下一個 tick 停止測試。 */
        private suspend fun step(venueId: Uuid, table: StressTable) {
            try {
                stepOnce(venueId, table)
                table.finished = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                stepFailure = stepFailure ?: error
            } finally {
                table.stepping = false
            }
        }

        /** 推進一桌一步並記錄；打完或卡住的桌從權威來源移除。 */
        private suspend fun stepOnce(venueId: Uuid, table: StressTable) {
            table.timer.reset()
            val progressed = table.runtime.step()
            val game = table.runtime.currentGame()
            when {
                game == null -> {
                    completedMatches++
                    removeTable(venueId, table)
                }
                progressed -> table.stalledSteps = 0
                ++table.stalledSteps >= MAX_STALLED_STEPS -> {
                    failedMatches++
                    logger.warn("Stress test match made no progress for {} steps; dropping it", MAX_STALLED_STEPS)
                    removeTable(venueId, table)
                }
            }
        }

        /**
         * 推進協程在主執行緒上執行完一個片段：累計到這一步與本 tick；這一步已經結束時記錄它的統計。
         *
         * @param table 推進的桌。
         * @param duration 這個片段的執行時間。
         */
        private fun onSegment(table: StressTable, duration: Duration) {
            table.stepTime += duration
            tickAdvance += duration
            if (!table.finished) return
            table.finished = false
            val stepTime = table.stepTime
            recordStep(stepTime, table.timer)
            if (stepTime >= SLOW_STEP_LOG_THRESHOLD) logSlowStep(stepTime, table.timer)
        }

        /** 把一桌從進行中的桌與權威來源移除。 */
        private suspend fun removeTable(venueId: Uuid, table: StressTable) {
            tables.remove(venueId)
            table.gameId?.let {
                tablesByGameId.remove(it)
                sharedDecisions.unregister(it, decisionObserver)
            }
            discardStressTables(environment.store, listOf(venueId))
        }

        /** 記錄這一輪對局的一次有結果的 AI 決策；在等待決策的推進協程（伺服器主執行緒）上呼叫。 */
        private fun onDecision(gameId: Uuid, outcome: AiDecisionOutcome, latency: Duration) {
            tablesByGameId[gameId]?.timer?.addAiWait(latency)
            bucket.addDecision(latency, outcome)
            if (!measuring) return
            aiDecisions++
            when (outcome) {
                AiDecisionOutcome.DECIDED -> Unit
                AiDecisionOutcome.TIMED_OUT -> aiTimeouts++
                AiDecisionOutcome.PREVIOUS_STILL_RUNNING -> aiPreviousStillRunning++
            }
            aiLatency.add(latency.toDouble(DurationUnit.MILLISECONDS))
        }

        /** 取消所有推進協程並把剩下的桌從權威來源移除；未結束的對局記為未完成，待寫歷史仍交給背景工作處理。 */
        suspend fun discardRemainingTables() {
            tablesByGameId.keys.forEach { sharedDecisions.unregister(it, decisionObserver) }
            stepJob.cancel()
            discardStressTables(environment.store, tables.keys.toList())
            tables.clear()
            tablesByGameId.clear()
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
                "Stress test step took {} ms on the server thread (aiContext={} ms, snapshotSync={} ms, aiWait={} ms, aiDecision={} ms); slowest AI decision: {} ms, {}",
                total.inWholeMilliseconds,
                timer.aiContext.inWholeMilliseconds,
                timer.snapshotSync.inWholeMilliseconds,
                timer.aiWait.inWholeMilliseconds,
                timer.aiDecision.inWholeMilliseconds,
                timer.slowestAiDecision?.duration?.inWholeMilliseconds,
                timer.slowestAiDecision?.let { stressAiDecisionLogText(it.context) },
            )
        }

        /** 開始每秒檢查各桌進行中的 AI 決策；同一次決策只記錄一次。 */
        fun startDecisionWatch() {
            decisionWatch = scope.launch(dispatchers.main) {
                val reported = mutableSetOf<OngoingAiDecision>()
                while (isActive) {
                    delay(DECISION_WATCH_INTERVAL)
                    val ongoing = tables.values.mapNotNullTo(mutableSetOf()) { it.timer.ongoingAiDecision }
                    reported.retainAll(ongoing)
                    ongoing.forEach { decision ->
                        val elapsed = decision.startedAt.elapsedNow()
                        if (elapsed < STUCK_DECISION_LOG_THRESHOLD || !reported.add(decision)) return@forEach
                        logger.warn("Stress test AI decision still running after {} s: {}", elapsed.inWholeSeconds, stressAiDecisionLogText(decision.context))
                    }
                }
            }
        }

        /** 停止檢查進行中的 AI 決策。 */
        fun stopDecisionWatch() {
            decisionWatch?.cancel()
            decisionWatch = null
        }

        /** 記錄一步主執行緒上的耗時與各環節耗時。 */
        private fun recordStep(total: Duration, timer: HeadlessStepTimer) {
            val stages = mapOf(
                StressStepStage.AI_CONTEXT to timer.aiContext,
                StressStepStage.RULES_AND_STATE to (total - timer.aiContext - timer.snapshotSync).coerceAtLeast(Duration.ZERO),
                StressStepStage.SNAPSHOT_SYNC to timer.snapshotSync,
                StressStepStage.AI_DECISION to timer.aiDecision,
            )
            tickSteps++
            bucket.addStep(total, stages, timer.staleDecisions)
            if (!measuring) return
            staleDecisions += timer.staleDecisions
            stepSamples.add(total.toDouble(DurationUnit.MILLISECONDS))
            stages.forEach { (stage, duration) -> stepStages.getValue(stage).add(duration) }
        }

        /**
         * 記錄本 tick 的處理時間與實際間隔、寫出到期的時間序列，並回傳安全閥的停止原因。
         *
         * @param msptMillis tick 本身的處理時間毫秒數。
         * @param intervalMillis 與上一個 tick 開始的時間差毫秒數；伺服器啟動後的第一個 tick 為 null。
         */
        fun recordTick(msptMillis: Double, intervalMillis: Double?): StressStopReason? {
            stepFailure?.let { error ->
                logger.error("Stress test table failed; stopping the stress test", error)
                return StressStopReason.RUN_FAILED
            }
            val history = environment.historyTimer
            val tickEvents = history.historyEvents
            val tickHistory = history.historyRecording
            history.reset()
            val strategyPeak = decisions.takeUnfinishedStrategyPeak()
            val advance = tickAdvance
            tickAdvance = Duration.ZERO
            val tickStop = intervalMillis?.let { valve.recordTick(it, countStutter = measuring) }
            bucket.addTick(msptMillis, intervalMillis, tickEvents, tickHistory, strategyPeak, advance, valve.lagMillis)
            if (measuring) {
                tickSamples.add(msptMillis)
                intervalMillis?.let(intervalSamples::add)
                advanceTotal += advance
                measuredTicks++
                STRESS_SLOW_TICK_THRESHOLDS_MILLIS.forEachIndexed { index, threshold -> if (msptMillis > threshold) slowTicks[index]++ }
                maxStepsInTick = maxOf(maxStepsInTick, tickSteps)
                maxEventsInTick = maxOf(maxEventsInTick, tickEvents)
                eventsProduced += tickEvents
                pendingPeak = maxOf(pendingPeak, pendingEvents)
                historyRecording.add(tickHistory)
                unfinishedStrategyPeak = maxOf(unfinishedStrategyPeak, strategyPeak)
            }
            tickSteps = 0
            if (lastRowMark.elapsedNow() >= TIME_SERIES_INTERVAL) writeTimeSeriesRow()
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
            is StressTestMode.Ramp -> mode.plan.tablesAt(elapsedTicks - warmupTicks).coerceAtMost(STRESS_MAX_TABLES)
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
                warmupTicks = warmupTicks,
                warmupRemainingTicks = (warmupTicks - elapsedTicks).coerceAtLeast(0),
                measuredSeconds = measureStart?.elapsedNow()?.toDouble(DurationUnit.SECONDS) ?: 0.0,
                tickAverageMillis = tickSamples.average(),
                tickP95Millis = tickSamples.percentile(P95),
                tickMaxMillis = tickSamples.max(),
                tickIntervalAverageMillis = intervalSamples.average(),
                tickIntervalP95Millis = intervalSamples.percentile(P95),
                tickIntervalMaxMillis = intervalSamples.max(),
                lagMillis = valve.lagMillis,
                advanceMillisPerSecond = advancePerSecond(),
                stepAverageMillis = stepSamples.average(),
                stepP95Millis = stepSamples.percentile(P95),
                stepMaxMillis = stepSamples.max(),
                stepStages = stepStages.mapValues { (_, accumulator) -> accumulator.summary() },
                historyRecording = historyRecording.summary(),
                aiDecisions = aiDecisions,
                aiTimeouts = aiTimeouts,
                aiPreviousStillRunning = aiPreviousStillRunning,
                aiLatencyAverageMillis = aiLatency.average(),
                aiLatencyP95Millis = aiLatency.percentile(P95),
                aiLatencyP99Millis = aiLatency.percentile(P99),
                aiLatencyMaxMillis = aiLatency.max(),
                staleDecisions = staleDecisions,
                unfinishedStrategyCalls = decisions.unfinishedStrategyCalls,
                unfinishedStrategyPeak = unfinishedStrategyPeak,
                strategyCapacity = strategyCapacity,
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
                    ?.sustainedTablesAt(elapsedTicks - warmupTicks)
                    ?.coerceAtMost(STRESS_MAX_TABLES),
            )
        }

        /** 暖機結束後每秒推進協程在主執行緒上執行的平均毫秒數。 */
        private fun advancePerSecond(): Double {
            val seconds = measureStart?.elapsedNow()?.toDouble(DurationUnit.SECONDS) ?: return 0.0
            return if (seconds <= 0.0) 0.0 else advanceTotal.toDouble(DurationUnit.MILLISECONDS) / seconds
        }

        /** 一列時間序列期間的彙總。 */
        private inner class TimeSeriesBucket {
            /** tick 數。 */
            private var ticks = 0

            /** tick 耗時總毫秒數。 */
            private var tickTotalMillis = 0.0

            /** tick 耗時最大毫秒數。 */
            private var tickMaxMillis = 0.0

            /** 有實際間隔的 tick 數。 */
            private var intervals = 0

            /** 實際 tick 間隔總毫秒數。 */
            private var intervalTotalMillis = 0.0

            /** 實際 tick 間隔最大毫秒數。 */
            private var intervalMaxMillis = 0.0

            /** 推進協程在主執行緒上執行的總時間。 */
            private var advanceTotal = Duration.ZERO

            /** 最後一個 tick 相對目標節奏的落後毫秒數。 */
            private var lag = 0.0

            /** 完成的步數。 */
            private var steps = 0

            /** 單步主執行緒總耗時。 */
            private var stepTotal = Duration.ZERO

            /** 各環節總耗時。 */
            private val stageTotals = StressStepStage.entries.associateWithTo(mutableMapOf()) { Duration.ZERO }

            /** 歷史記錄總耗時。 */
            private var historyTotal = Duration.ZERO

            /** 產生的歷史事件數。 */
            private var eventsProduced = 0

            /** 有結果的 AI 決策數。 */
            private var decisions = 0

            /** AI 決策延遲總時間。 */
            private var latencyTotal = Duration.ZERO

            /** AI 決策延遲最大值。 */
            private var latencyMax = Duration.ZERO

            /** 逾時而使用固定命令的 AI 決策數。 */
            private var timeouts = 0

            /** 過期而沒有套用的 AI 決策數。 */
            private var stale = 0

            /** 這段期間同時存在、尚未結束的策略工作數的最大值。 */
            private var strategyPeak = 0

            /** 加入一步。 */
            fun addStep(total: Duration, stages: Map<StressStepStage, Duration>, staleDecisions: Int) {
                steps++
                stepTotal += total
                stages.forEach { (stage, duration) -> stageTotals[stage] = stageTotals.getValue(stage) + duration }
                stale += staleDecisions
            }

            /** 加入一次有結果的 AI 決策。 */
            fun addDecision(latency: Duration, outcome: AiDecisionOutcome) {
                decisions++
                latencyTotal += latency
                latencyMax = maxOf(latencyMax, latency)
                if (outcome == AiDecisionOutcome.TIMED_OUT) timeouts++
            }

            /** 加入一個 tick。 */
            fun addTick(
                msptMillis: Double,
                intervalMillis: Double?,
                events: Int,
                history: Duration,
                strategyCallsPeak: Int,
                advance: Duration,
                lagMillis: Double,
            ) {
                ticks++
                tickTotalMillis += msptMillis
                tickMaxMillis = maxOf(tickMaxMillis, msptMillis)
                intervalMillis?.let { interval ->
                    intervals++
                    intervalTotalMillis += interval
                    intervalMaxMillis = maxOf(intervalMaxMillis, interval)
                }
                advanceTotal += advance
                lag = lagMillis
                eventsProduced += events
                historyTotal += history
                strategyPeak = maxOf(strategyPeak, strategyCallsPeak)
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
                    tickIntervalAverageMillis = if (intervals == 0) 0.0 else intervalTotalMillis / intervals,
                    tickIntervalMaxMillis = intervalMaxMillis,
                    lagMillis = lag,
                    advanceMillis = advanceTotal.toDouble(DurationUnit.MILLISECONDS),
                    steps = steps,
                    stepAverageMillis = if (steps == 0) 0.0 else stepTotal.toDouble(DurationUnit.MILLISECONDS) / steps,
                    stageTotalMillis = stageTotals.mapValues { (_, duration) -> duration.toDouble(DurationUnit.MILLISECONDS) },
                    historyRecordingMillis = historyTotal.toDouble(DurationUnit.MILLISECONDS),
                    aiDecisions = decisions,
                    aiLatencyAverageMillis = if (decisions == 0) 0.0 else latencyTotal.toDouble(DurationUnit.MILLISECONDS) / decisions,
                    aiLatencyMaxMillis = latencyMax.toDouble(DurationUnit.MILLISECONDS),
                    aiTimeouts = timeouts,
                    staleDecisions = stale,
                    strategyCallsPeak = strategyPeak,
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
     * @property timer 這一桌的分項計時器。
     * @property gameId 這一桌的對局；建立時已沒有對局為 null。
     */
    private class StressTable(
        val runtime: HeadlessHistoryMatchRuntime,
        val timer: HeadlessStepTimer,
        val gameId: Uuid?,
    ) {
        /** 連續沒有進展的步數。 */
        var stalledSteps = 0

        /** 是否有推進正在進行。 */
        var stepping = false

        /** 進行中這一步在主執行緒上已執行的時間。 */
        var stepTime = Duration.ZERO

        /** 進行中這一步是否已結束，等它最後一個片段結束時記錄統計。 */
        var finished = false
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

        /** 第 99 百分位。 */
        const val P99 = 0.99

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

/**
 * 推進協程的計時：協程在主執行緒上執行完一個片段時，以片段的執行時間呼叫 [onSegment]。
 *
 * @property onSegment 接收每個片段的執行時間。
 */
internal class StepTiming(val onSegment: (Duration) -> Unit) : AbstractCoroutineContextElement(Key) {
    /** 在協程情境中查詢 [StepTiming] 的 key。 */
    companion object Key : CoroutineContext.Key<StepTiming>
}

/**
 * 計時推進協程每個執行片段的主執行緒調度器：一律經 [delegate] 排程（在主執行緒上時 [delegate] 會立即執行），並量測片段的執行時間交給
 * 協程情境中的 [StepTiming]，因此等待 AI 結果等掛起期間不計入。另一個協程在片段中同步恢復時，它的時間會同時計入外層片段。
 *
 * 沒有實作延遲排程，推進協程中的等待逾時以實際時間計算。
 *
 * @property delegate 實際的主執行緒調度器。
 * @property timeSource 量測片段的時間來源。
 */
internal class SegmentTimingDispatcher(
    private val delegate: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val timing = context[StepTiming]
        if (timing == null) {
            delegate.dispatch(context, block)
            return
        }
        delegate.dispatch(context) {
            val mark = timeSource.markNow()
            try {
                block.run()
            } finally {
                timing.onSegment(mark.elapsedNow())
            }
        }
    }
}
