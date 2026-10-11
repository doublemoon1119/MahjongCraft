package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

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
 *
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
