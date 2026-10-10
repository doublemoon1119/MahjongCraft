package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** 已載入位置確認麻將桌缺失後，伺服器如何處理相關權威狀態。 */
enum class OrphanedTablePolicy {
    /** 保留 Room／Game 與位置索引，只記錄可診斷 warning。 */
    KEEP_AND_WARN,

    /** 只移除等待中的 Room；進行中 Game 仍保留。 */
    REMOVE_WAITING_ROOM,

    /** 移除缺失桌子對應的 Room 或 Game。 */
    REMOVE_ALL,
}
