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

/** 依目前是否存在 Room／Game 判斷此政策是否允許玩家破壞桌子。 */
fun TableBreakPolicy.allowsTableBreak(hasRoom: Boolean, hasGame: Boolean): Boolean = when (this) {
    TableBreakPolicy.DENY_WHILE_OCCUPIED -> !hasRoom && !hasGame
    TableBreakPolicy.ALLOW_WAITING_ROOM_ONLY -> !hasGame
    TableBreakPolicy.ALLOW_AND_TERMINATE -> true
}

/** 已載入位置確認麻將桌缺失後，伺服器如何處理相關權威狀態。 */
enum class OrphanedTablePolicy {
    /** 保留 Room／Game 與位置索引，只記錄可診斷 warning。 */
    KEEP_AND_WARN,

    /** 只移除等待中的 Room；進行中 Game 仍保留。 */
    REMOVE_WAITING_ROOM,

    /** 移除缺失桌子對應的 Room 或 Game。 */
    REMOVE_ALL,
}

/** MahjongCraft Minecraft 平台的伺服器生命週期設定。 */
data class MinecraftServerConfig(
    /** 玩家斷線時採用的座位保留政策。 */
    val disconnectedPlayerPolicy: DisconnectedPlayerPolicy = DisconnectedPlayerPolicy.KEEP_SEAT,
    /** 麻將桌被破壞時採用的政策。 */
    val tableBreakPolicy: TableBreakPolicy = TableBreakPolicy.DENY_WHILE_OCCUPIED,
    /** 已確認桌子缺失時採用的資料清理政策。 */
    val orphanedTablePolicy: OrphanedTablePolicy = OrphanedTablePolicy.REMOVE_ALL,
    /** `LEAVE_AFTER_TIMEOUT` 使用的離線寬限秒數。 */
    val disconnectedPlayerTimeoutSeconds: Long = 300,
)
