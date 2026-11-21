package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

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
