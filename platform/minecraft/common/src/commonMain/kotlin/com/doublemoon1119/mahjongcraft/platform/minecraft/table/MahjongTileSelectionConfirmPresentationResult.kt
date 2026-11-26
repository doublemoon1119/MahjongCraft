package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 選牌確認面板呈現請求的處理結果。 */
enum class MahjongTileSelectionConfirmPresentationResult {
    /** 已生成或更新這位玩家的確認面板。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成確認面板 entity 失敗（例如世界拒絕 spawn）。 */
    SPAWN_FAILED,
}
