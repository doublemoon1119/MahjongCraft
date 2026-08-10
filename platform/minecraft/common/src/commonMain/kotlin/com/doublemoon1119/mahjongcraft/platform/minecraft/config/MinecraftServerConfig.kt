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

/** 玩家嘗試破壞已有房間或牌局的麻將桌時，伺服器採用的政策。 */
enum class TableBreakPolicy {
    /** 只要麻將桌仍被 Room 或 Game 使用就拒絕破壞。 */
    DENY_WHILE_OCCUPIED,

    /** 只允許破壞仍處於等待室階段的麻將桌。 */
    ALLOW_WAITING_ROOM_ONLY,

    /** 允許破壞並終止相關 Room 或 Game；正式終止語意完成前不得設為預設值。 */
    ALLOW_AND_TERMINATE,
}

/** MahjongCraft Minecraft 平台的伺服器生命週期設定。 */
data class MinecraftServerConfig(
    /** 玩家斷線時採用的座位保留政策。 */
    val disconnectedPlayerPolicy: DisconnectedPlayerPolicy = DisconnectedPlayerPolicy.KEEP_SEAT,
    /** 麻將桌被破壞時採用的政策。 */
    val tableBreakPolicy: TableBreakPolicy = TableBreakPolicy.DENY_WHILE_OCCUPIED,
    /** `LEAVE_AFTER_TIMEOUT` 使用的離線寬限秒數。 */
    val disconnectedPlayerTimeoutSeconds: Long = 300,
)
