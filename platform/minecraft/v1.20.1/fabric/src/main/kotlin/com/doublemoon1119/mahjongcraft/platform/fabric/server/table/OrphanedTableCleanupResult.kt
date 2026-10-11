package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

/** 缺失麻將桌的清理結果。 */
enum class OrphanedTableCleanupResult {
    /** 延遲請求已過期，不變更任何狀態。 */
    STALE_REQUEST,

    /** 政策要求保留目前 Room／Game 與位置索引。 */
    RETAINED,

    /** 已移除等待中的 Room 與其衍生狀態。 */
    REMOVED_ROOM,

    /** 已移除進行中的 Game 與其衍生狀態。 */
    REMOVED_GAME,

    /** 沒有權威狀態，只移除過期位置索引。 */
    REMOVED_LOCATION,
}
