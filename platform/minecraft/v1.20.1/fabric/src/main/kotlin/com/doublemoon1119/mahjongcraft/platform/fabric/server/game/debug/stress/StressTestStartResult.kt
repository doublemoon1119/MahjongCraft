package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

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
