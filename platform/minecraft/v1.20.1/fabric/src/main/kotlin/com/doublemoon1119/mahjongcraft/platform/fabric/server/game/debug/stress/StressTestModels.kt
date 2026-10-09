package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * 壓力測試中每桌推進對局的節奏。
 *
 * @property commandName 指令中使用的名稱。
 * @property stepIntervalTicks 同一桌兩步之間間隔的伺服器 tick 數。
 */
enum class StressTestPace(val commandName: String, val stepIntervalTicks: Int) {
    /** 每桌約每秒一步，模擬真實對局的步調。 */
    REALTIME("realtime", 20),

    /** 每桌每 tick 一步，去掉動畫等待後最快的節奏。 */
    FAST("fast", 1),
}

/** 壓力測試的桌數安排。 */
sealed interface StressTestMode {
    /**
     * 固定維持 [tables] 桌同時進行。
     *
     * @property tables 同時進行的桌數，介於 1 與 [STRESS_MAX_TABLES] 之間。
     */
    data class Fixed(val tables: Int) : StressTestMode {
        init {
            require(tables in 1..STRESS_MAX_TABLES) { "Stress test table count must be between 1 and $STRESS_MAX_TABLES" }
        }
    }

    /**
     * 依 [plan] 逐步增加桌數，直到安全閥停止。
     *
     * @property plan 桌數增加的方式。
     */
    data class Ramp(val plan: StressRampPlan) : StressTestMode
}

/** 壓力測試停止的原因。 */
enum class StressStopReason {
    /** 管理員要求停止。 */
    REQUESTED,

    /** 伺服器跟不上每秒 20 tick：最近一段時間的平均每 tick 耗時超過門檻。 */
    FALLING_BEHIND,

    /** 歷史待寫佇列積壓超過容量比例門檻。 */
    HISTORY_BACKLOG,

    /** 歷史出現遺失的序號缺口。 */
    HISTORY_LOST,

    /** 歷史寫入元件發生錯誤或暫停寫入。 */
    HISTORY_WRITER_FAILED,

    /** 伺服器關閉。 */
    SERVER_STOPPING,

    /** 推進測試對局時發生未預期的錯誤。 */
    RUN_FAILED,
}

/**
 * 爬坡模式的桌數增加方式。
 *
 * @property initialTables 開始時的桌數。
 * @property stepTables 每次增加的桌數。
 * @property intervalTicks 每次增加之間的伺服器 tick 數。
 */
data class StressRampPlan(
    val initialTables: Int = 4,
    val stepTables: Int = 4,
    val intervalTicks: Long = 600,
) {
    init {
        require(initialTables > 0) { "Initial table count must be positive" }
        require(stepTables > 0) { "Table step must be positive" }
        require(intervalTicks > 0) { "Ramp interval must be positive" }
    }

    /** 開始後經過 [elapsedTicks] 時應同時進行的桌數。 */
    fun tablesAt(elapsedTicks: Long): Int = initialTables + stepTables * (elapsedTicks.coerceAtLeast(0) / intervalTicks).toInt()

    /**
     * 在 [elapsedTicks] 時停止，已完整撐過一個階段的最大桌數；第一個階段就停止時為 0。
     */
    fun sustainedTablesAt(elapsedTicks: Long): Int = (tablesAt(elapsedTicks) - stepTables).coerceAtLeast(0)
}

/**
 * 安全閥的門檻；這是壓力測試自己的停止政策。
 *
 * 所有判斷都看實際 tick 間隔（相鄰兩個 tick 開始的時間差），而不是 tick 本身的處理時間：AI 結果回到伺服器主執行緒後的工作
 * 在 tick 之間執行，只看 tick 本身會漏掉這些負擔。
 *
 * @property stutterLimitMillis 單一 tick 間隔超過這個毫秒數就算一次卡頓。
 * @property windowTicks 判斷落後與卡頓所看的 tick 數。
 * @property stutterTicks 最近 [windowTicks] 個 tick 中至少有這麼多次卡頓，才判定為持續卡頓；偶發的單次尖峰不算。
 * @property fallingBehindAverageMillis 一個 [windowTicks] 視窗的平均間隔超過這個毫秒數，這個視窗就算跟不上；目標節奏 50 ms
 *   之上保留正常排程抖動的餘裕。
 * @property sustainedWindows 連續這麼多個不重疊的視窗都跟不上，才判定伺服器持續跟不上而停止。
 * @property targetIntervalMillis 目標 tick 間隔。
 * @property maxLagMillis 相對目標節奏的落後量上限；超過時不等視窗判定就緊急停止，搶在 Minecraft 的 watchdog 強制關閉之前。
 * @property backlogRatio 歷史待寫佇列積壓佔容量的比例上限。
 */
data class StressSafetyThresholds(
    val stutterLimitMillis: Double = 100.0,
    val windowTicks: Int = 200,
    val stutterTicks: Int = 5,
    val fallingBehindAverageMillis: Double = 55.0,
    val sustainedWindows: Int = 2,
    val targetIntervalMillis: Double = 50.0,
    val maxLagMillis: Double = 10_000.0,
    val backlogRatio: Double = 0.8,
) {
    init {
        require(stutterLimitMillis > 0) { "Stutter limit must be positive" }
        require(windowTicks > 0) { "Tick window must be positive" }
        require(stutterTicks in 1..windowTicks) { "Stutter tick count must be within the tick window" }
        require(fallingBehindAverageMillis > 0) { "Falling-behind average must be positive" }
        require(sustainedWindows > 0) { "Sustained window count must be positive" }
        require(targetIntervalMillis > 0) { "Target interval must be positive" }
        require(maxLagMillis > 0) { "Maximum lag must be positive" }
        require(backlogRatio in 0.0..1.0) { "Backlog ratio must be between 0 and 1" }
    }
}

/**
 * 判斷壓力測試是否該停止，並偵測持續卡頓。
 *
 * 以實際 tick 間隔判斷伺服器是否跟不上：連續 [StressSafetyThresholds.sustainedWindows] 個不重疊視窗的平均間隔都超過門檻就停止；
 * 另外追蹤相對目標節奏的落後量（每個 tick 加上間隔與目標的差，最低為 0），伺服器追上時會下降，超過
 * [StressSafetyThresholds.maxLagMillis] 就緊急停止。爬坡進入新的階段時以 [resetLag] 重設落後量。持續卡頓（間隔尖峰）只回報
 * 狀態、不停止，讓同一次測試同時量到「開始卡頓」與「撐不住」兩個桌數。歷史遺失、寫入失敗與積壓超過比例則立即停止，讓測試
 * 在真的遺失歷史之前停下。
 *
 * @property thresholds 停止門檻。
 */
class StressSafetyValve(private val thresholds: StressSafetyThresholds) {
    /** 最近 [StressSafetyThresholds.windowTicks] 個 tick 是否算作卡頓。 */
    private val stutterFlags = ArrayDeque<Boolean>(thresholds.windowTicks)

    /** [stutterFlags] 中的卡頓次數。 */
    private var stutters = 0

    /** 目前視窗已記錄的 tick 數。 */
    private var windowCount = 0

    /** 目前視窗的間隔總和。 */
    private var windowTotalMillis = 0.0

    /** 連續跟不上的視窗數。 */
    private var slowWindows = 0

    /** 相對目標節奏的落後毫秒數。 */
    var lagMillis: Double = 0.0
        private set

    /** 最近一段時間是否持續卡頓。 */
    val stuttering: Boolean get() = stutters >= thresholds.stutterTicks

    /**
     * 記錄一個 tick 的實際間隔；伺服器持續跟不上或落後太多時回傳停止原因。
     *
     * @param intervalMillis 這個 tick 與上一個 tick 開始的時間差毫秒數。
     * @param countStutter 這個 tick 是否列入卡頓判斷；暖機期間不列入，但仍會判斷是否落後。
     */
    fun recordTick(intervalMillis: Double, countStutter: Boolean): StressStopReason? {
        val stutter = countStutter && intervalMillis > thresholds.stutterLimitMillis
        stutterFlags.addLast(stutter)
        if (stutter) stutters++
        if (stutterFlags.size > thresholds.windowTicks && stutterFlags.removeFirst()) stutters--
        lagMillis = (lagMillis + intervalMillis - thresholds.targetIntervalMillis).coerceAtLeast(0.0)
        windowCount++
        windowTotalMillis += intervalMillis
        if (windowCount == thresholds.windowTicks) {
            slowWindows = if (windowTotalMillis / windowCount > thresholds.fallingBehindAverageMillis) slowWindows + 1 else 0
            windowCount = 0
            windowTotalMillis = 0.0
        }
        return if (slowWindows >= thresholds.sustainedWindows || lagMillis > thresholds.maxLagMillis) StressStopReason.FALLING_BEHIND else null
    }

    /** 重設相對目標節奏的落後量，例如爬坡進入新的階段時。 */
    fun resetLag() {
        lagMillis = 0.0
    }

    /**
     * 檢查歷史寫入狀態。
     *
     * @param pendingEvents 待寫佇列中的事件數。
     * @param capacity 待寫佇列容量。
     * @param lostSegments 出現序號缺口的場次數。
     * @param writerFailed 寫入元件是否發生錯誤或暫停寫入。
     */
    fun checkHistory(pendingEvents: Int, capacity: Int, lostSegments: Int, writerFailed: Boolean): StressStopReason? = when {
        lostSegments > 0 -> StressStopReason.HISTORY_LOST
        writerFailed -> StressStopReason.HISTORY_WRITER_FAILED
        capacity > 0 && pendingEvents >= ceil(capacity * thresholds.backlogRatio) -> StressStopReason.HISTORY_BACKLOG
        else -> null
    }
}

/**
 * 保留最近 [capacity] 筆數值的滾動統計。
 *
 * @property capacity 保留的筆數上限。
 */
class RollingSamples(private val capacity: Int) {
    init {
        require(capacity > 0) { "Sample capacity must be positive" }
    }

    /** 依加入順序保留的數值。 */
    private val values = ArrayDeque<Double>(capacity)

    /** 目前保留的筆數。 */
    val size: Int get() = values.size

    /** 加入一筆數值；超過容量時捨棄最舊的一筆。 */
    fun add(value: Double) {
        if (values.size == capacity) values.removeFirst()
        values.addLast(value)
    }

    /** 平均值；沒有數值時為 0。 */
    fun average(): Double = if (values.isEmpty()) 0.0 else values.average()

    /** 最大值；沒有數值時為 0。 */
    fun max(): Double = values.maxOrNull() ?: 0.0

    /** 第 [fraction] 分位數（例如 0.95），以最接近的排名取值；沒有數值時為 0。 */
    fun percentile(fraction: Double): Double {
        require(fraction in 0.0..1.0) { "Percentile fraction must be between 0 and 1" }
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}

/**
 * 以固定刻度累計毫秒數的直方圖：記憶體用量固定，可統計長時間量測中全部數值的分位數。
 *
 * 分位數以落點那一格的上緣回報（不超過實際最大值），誤差最多 [resolutionMillis]；超過 [limitMillis] 的數值歸入最後一格，
 * 分位數落在最後一格時回報實際最大值。
 *
 * @property resolutionMillis 每格的寬度毫秒數。
 * @property limitMillis 有獨立刻度的上限毫秒數。
 */
class MillisHistogram(
    private val resolutionMillis: Double = DEFAULT_RESOLUTION_MILLIS,
    private val limitMillis: Double = DEFAULT_LIMIT_MILLIS,
) {
    init {
        require(resolutionMillis > 0.0) { "Histogram resolution must be positive" }
        require(limitMillis >= resolutionMillis) { "Histogram limit must be at least one resolution step" }
    }

    /** 每格的筆數；最後一格收容超過上限的數值。 */
    private val counts = LongArray(ceil(limitMillis / resolutionMillis).toInt() + 1)

    /** 累計的筆數。 */
    var count: Long = 0L
        private set

    /** 累計的總毫秒數。 */
    private var totalMillis = 0.0

    /** 累計的最大毫秒數。 */
    private var maxMillis = 0.0

    /** 加入一筆毫秒數；負值視為 0。 */
    fun add(millis: Double) {
        val value = millis.coerceAtLeast(0.0)
        counts[(value / resolutionMillis).toInt().coerceAtMost(counts.lastIndex)]++
        count++
        totalMillis += value
        maxMillis = maxOf(maxMillis, value)
    }

    /** 平均值；沒有數值時為 0。 */
    fun average(): Double = if (count == 0L) 0.0 else totalMillis / count

    /** 最大值；沒有數值時為 0。 */
    fun max(): Double = maxMillis

    /** 第 [fraction] 分位數（例如 0.99），以最接近的排名取值；沒有數值時為 0。 */
    fun percentile(fraction: Double): Double {
        require(fraction in 0.0..1.0) { "Percentile fraction must be between 0 and 1" }
        if (count == 0L) return 0.0
        val rank = ceil(fraction * count).toLong().coerceIn(1L, count)
        var seen = 0L
        for (index in counts.indices) {
            seen += counts[index]
            if (seen < rank) continue
            return if (index == counts.lastIndex) maxMillis else minOf((index + 1) * resolutionMillis, maxMillis)
        }
        return maxMillis
    }

    /** 預設刻度所在的伴生物件。 */
    private companion object {
        /** 預設每格寬度毫秒數。 */
        const val DEFAULT_RESOLUTION_MILLIS = 0.1

        /** 預設有獨立刻度的上限毫秒數。 */
        const val DEFAULT_LIMIT_MILLIS = 10_000.0
    }
}

/**
 * 壓力測試的歷史處理方式，用來分辨瓶頸在記錄、編碼還是資料庫寫入。
 *
 * @property commandName 指令中使用的名稱。
 */
enum class StressHistoryMode(val commandName: String) {
    /** 記錄歷史並以正式寫入流程寫進壓力測試資料庫。 */
    WRITE("write"),

    /** 記錄歷史，背景工作只把事件編碼成持久化格式後就確認移除，不寫進資料庫。 */
    ENCODE("encode"),

    /** 不記錄歷史。 */
    OFF("off"),
}

/**
 * 單步耗時拆解出的環節。
 *
 * 前三項在伺服器主執行緒上，加總就是單步耗時（單步從開始到結束的時間扣掉等待 AI 結果的時間）；[AI_DECISION] 在背景執行緒上，
 * 不計入單步耗時。歷史記錄無法分攤到同時進行的各步，改以每 tick 統計。
 */
enum class StressStepStage {
    /** 建立 AI 視角快照。 */
    AI_CONTEXT,

    /** 規則判斷、狀態提交與歷史記錄等其餘主執行緒流程：單步耗時扣掉建立 AI 視角快照與快照同步。 */
    RULES_AND_STATE,

    /** 為每位觀察者裁切可見快照。 */
    SNAPSHOT_SYNC,

    /** 策略在背景執行緒上的思考。 */
    AI_DECISION,
}

/**
 * 一組耗時的統計結果。
 *
 * @property count 筆數。
 * @property averageMillis 平均毫秒數；沒有資料時為 0。
 * @property maxMillis 最大毫秒數；沒有資料時為 0。
 * @property totalMillis 總毫秒數。
 */
data class TimingSummary(
    val count: Long,
    val averageMillis: Double,
    val maxMillis: Double,
    val totalMillis: Double,
) {
    companion object {
        /** 沒有資料的統計。 */
        val EMPTY: TimingSummary = TimingSummary(count = 0, averageMillis = 0.0, maxMillis = 0.0, totalMillis = 0.0)
    }
}

/** 累計筆數、總耗時與最大耗時；不保留個別數值。 */
class TimingAccumulator {
    /** 筆數。 */
    private var count = 0L

    /** 總耗時。 */
    private var total = Duration.ZERO

    /** 最大耗時。 */
    private var max = Duration.ZERO

    /** 加入一筆耗時。 */
    fun add(duration: Duration) {
        count++
        total += duration
        if (duration > max) max = duration
    }

    /** 目前的統計結果。 */
    fun summary(): TimingSummary = if (count == 0L) {
        TimingSummary.EMPTY
    } else {
        TimingSummary(
            count = count,
            averageMillis = total.toDouble(DurationUnit.MILLISECONDS) / count,
            maxMillis = max.toDouble(DurationUnit.MILLISECONDS),
            totalMillis = total.toDouble(DurationUnit.MILLISECONDS),
        )
    }
}

/** 同時進行桌數的上限；固定桌數的指令引數與爬坡的目標桌數都不會超過。 */
const val STRESS_MAX_TABLES: Int = 1_000

/** 每個 tick 最多新建的桌數；需要大量建桌時分散到連續幾個 tick，避免安全閥介入前單一 tick 就卡住。 */
const val STRESS_TABLES_CREATED_PER_TICK: Int = 4

/** 統計「每 tick 耗時超過門檻」比例的門檻毫秒數。 */
val STRESS_SLOW_TICK_THRESHOLDS_MILLIS: List<Int> = listOf(50, 100, 250)
