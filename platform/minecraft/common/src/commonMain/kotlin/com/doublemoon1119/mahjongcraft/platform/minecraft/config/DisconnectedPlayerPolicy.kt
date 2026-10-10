package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** 玩家中斷 Minecraft 連線後，伺服器如何處理其房間或牌局座位。 */
enum class DisconnectedPlayerPolicy {
    /** 保留座位，讓相同 UUID 的玩家重新連線後繼續。 */
    KEEP_SEAT,

    /** 連線中斷時立即嘗試讓玩家離開。 */
    LEAVE_IMMEDIATELY,

    /** 保留座位一段時間，逾時後才嘗試讓玩家離開。 */
    LEAVE_AFTER_TIMEOUT,
}
