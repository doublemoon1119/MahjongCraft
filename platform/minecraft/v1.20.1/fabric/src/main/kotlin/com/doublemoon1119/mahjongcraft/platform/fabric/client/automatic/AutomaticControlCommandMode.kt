package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

/** Client 指令要求套用的自動操作狀態。 */
enum class AutomaticControlCommandMode {
    /** 明確開啟。 */
    ON,

    /** 明確關閉。 */
    OFF,

    /** 依已確認狀態反轉。 */
    TOGGLE,
}
