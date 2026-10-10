package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 正式桌面局況顯示呈現請求的處理結果。 */
enum class MahjongRoundInfoPresentationResult {
    /** 已把這桌的局況顯示更新為本次要呈現的內容。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成局況顯示 entity 失敗（例如世界拒絕 spawn）。 */
    SPAWN_FAILED,
}
