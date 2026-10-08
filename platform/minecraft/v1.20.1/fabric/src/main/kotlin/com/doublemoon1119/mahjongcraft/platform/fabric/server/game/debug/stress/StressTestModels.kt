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
     * @property tables 同時進行的桌數。
     */
    data class Fixed(val tables: Int) : StressTestMode {
        init {
            require(tables > 0) { "Stress test table count must be positive" }
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
 * 安全閥的門檻。
 *
 * 推進對局集中在少數 tick（正式伺服器也是每 20 tick 一次推進所有對局），因此以最近一段時間的整體表現判斷，
 * 而不是要求連續多個 tick 都很慢。
 *
 * @property stutterLimitMillis 單一 tick 耗時超過這個毫秒數就算一次卡頓。
 * @property windowTicks 判斷落後與卡頓所看的最近 tick 數。
 * @property stutterTicks 最近 [windowTicks] 個 tick 中至少有這麼多次卡頓，才判定為持續卡頓；偶發的單次尖峰不算。
 * @property fallingBehindAverageMillis 最近 [windowTicks] 個 tick 的平均耗時超過這個毫秒數，就表示伺服器跟不上每秒 20 tick。
 * @property backlogRatio 歷史待寫佇列積壓佔容量的比例上限。
 */
data class StressSafetyThresholds(
    val stutterLimitMillis: Double = 100.0,
    val windowTicks: Int = 200,
    val stutterTicks: Int = 5,
    val fallingBehindAverageMillis: Double = 50.0,
    val backlogRatio: Double = 0.8,
) {
    init {
        require(stutterLimitMillis > 0) { "Stutter limit must be positive" }
        require(windowTicks > 0) { "Tick window must be positive" }
        require(stutterTicks in 1..windowTicks) { "Stutter tick count must be within the tick window" }
        require(fallingBehindAverageMillis > 0) { "Falling-behind average must be positive" }
        require(backlogRatio in 0.0..1.0) { "Backlog ratio must be between 0 and 1" }
    }
}

/**
 * 判斷壓力測試是否該停止，並偵測持續卡頓。
 *
 * 最近 [StressSafetyThresholds.windowTicks] 個 tick 的平均耗時超過門檻時停止：此時伺服器已跟不上每秒 20 tick。
 * 持續卡頓只回報狀態、不停止，讓同一次測試同時量到「開始卡頓」與「撐不住」兩個桌數。歷史遺失、寫入失敗與積壓
 * 超過比例則立即停止，讓測試在真的遺失歷史之前停下。
 *
 * @property thresholds 停止門檻。
 */
class StressSafetyValve(private val thresholds: StressSafetyThresholds) {
    /** 最近的每 tick 耗時。 */
    private val window = ArrayDeque<Double>(thresholds.windowTicks)

    /** [window] 中耗時的總和。 */
    private var windowTotalMillis = 0.0

    /** [window] 中的卡頓次數。 */
    private var stutters = 0

    /** 最近一段時間是否持續卡頓。 */
    val stuttering: Boolean get() = stutters >= thresholds.stutterTicks

    /** 記錄一個 tick 的耗時；最近一段時間的平均超過門檻時回傳停止原因。 */
    fun recordTick(msptMillis: Double): StressStopReason? {
        window.addLast(msptMillis)
        windowTotalMillis += msptMillis
        if (msptMillis > thresholds.stutterLimitMillis) stutters++
        if (window.size > thresholds.windowTicks) {
            val removed = window.removeFirst()
            windowTotalMillis -= removed
            if (removed > thresholds.stutterLimitMillis) stutters--
        }
        val full = window.size == thresholds.windowTicks
        return if (full && windowTotalMillis / window.size > thresholds.fallingBehindAverageMillis) StressStopReason.FALLING_BEHIND else null
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

/** 單步耗時拆解出的環節。 */
enum class StressStepStage {
    /** 建立 AI 視角快照與策略思考。 */
    AI_DECISION,

    /** 規則判斷、狀態提交等其餘流程：單步總耗時扣掉其他三項。 */
    RULES_AND_STATE,

    /** 為每位觀察者裁切可見快照。 */
    SNAPSHOT_SYNC,

    /** 權威交易中比對桌況並把事件加入待寫佇列。 */
    HISTORY_RECORDING,
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

/** 暖機時間：開始後這段期間 JIT 編譯尚未穩定，不列入統計，也不以每 tick 耗時判斷落後或卡頓；爬坡在暖機結束後才開始加桌。 */
const val STRESS_WARMUP_TICKS: Long = 1_200

/** 統計「每 tick 耗時超過門檻」比例的門檻毫秒數。 */
val STRESS_SLOW_TICK_THRESHOLDS_MILLIS: List<Int> = listOf(50, 100, 250)
