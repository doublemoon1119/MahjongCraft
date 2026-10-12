package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/** 壓力測試停止的原因。 */
enum class StressStopReason {
    /** 管理員要求停止。 */
    REQUESTED,

    /** 實際 tick 間隔連續超過視窗平均門檻，或相對目標節奏的累計落後量超過上限。 */
    FALLING_BEHIND,

    /** 歷史待寫佇列積壓達到容量比例門檻。 */
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
