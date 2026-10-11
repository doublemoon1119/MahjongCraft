package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

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
