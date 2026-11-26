package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** 玩家嘗試破壞已有房間或牌局的麻將桌時，伺服器採用的政策。 */
enum class TableBreakPolicy {
    /** 只要麻將桌仍被 Room 或 Game 使用就拒絕破壞。 */
    DENY_WHILE_OCCUPIED,

    /** 只允許破壞仍處於等待室階段的麻將桌。 */
    ALLOW_WAITING_ROOM_ONLY,

    /** 允許破壞並終止相關 Room 或 Game。 */
    ALLOW_AND_TERMINATE,
}
