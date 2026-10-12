package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/**
 * 開始壓力測試時可在指令中指定的設定。
 *
 * @property pace 每桌推進節奏。
 * @property historyMode 歷史處理方式。
 * @property stutterLimitMillis 相鄰兩個 tick 開始的實際間隔超過此毫秒數時算一次卡頓。
 * @property warmupSeconds 開始後不列入統計與卡頓判斷的秒數；0 表示不暖機。
 */
data class StressRunOptions(
    val pace: StressTestPace,
    val historyMode: StressHistoryMode,
    val stutterLimitMillis: Int,
    val warmupSeconds: Int,
) {
    init {
        require(stutterLimitMillis in requireNotNull(StressOption.MAX_MSPT.range)) { "Stutter limit is out of range" }
        require(warmupSeconds in requireNotNull(StressOption.WARMUP.range)) { "Warm-up seconds are out of range" }
    }

    /** 暖機的 tick 數。 */
    val warmupTicks: Long get() = warmupSeconds.toLong() * TICKS_PER_SECOND

    /** 依這些設定建立的安全閥門檻。 */
    fun thresholds(): StressSafetyThresholds = StressSafetyThresholds(stutterLimitMillis = stutterLimitMillis.toDouble())

    companion object {
        /** 指令中沒有指定任何選項時的設定。 */
        val DEFAULT: StressRunOptions = StressRunOptions(
            pace = StressTestPace.REALTIME,
            historyMode = StressHistoryMode.WRITE,
            stutterLimitMillis = 100,
            warmupSeconds = 60,
        )

        /** 一秒的伺服器 tick 數。 */
        private const val TICKS_PER_SECOND = 20L
    }
}
