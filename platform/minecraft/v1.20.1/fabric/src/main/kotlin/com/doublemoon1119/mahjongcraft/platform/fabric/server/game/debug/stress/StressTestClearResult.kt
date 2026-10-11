package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/** 刪除壓力測試資料庫的結果。 */
enum class StressTestClearResult {
    /** 已刪除。 */
    CLEARED,

    /** 沒有壓力測試資料庫。 */
    NOTHING_TO_CLEAR,

    /** 壓力測試仍在執行，須先停止。 */
    RUNNING,
}
