package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 規則桌面物件呈現請求的處理結果。 */
enum class TablePropPresentationResult {
    /** 桌上的規則桌面物件已更新為 [TablePropPresentation.placements] 描述的樣子。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 搜尋範圍的實體尚未載入；已排入刷新佇列，不生成部分副本。 */
    WAITING_FOR_CHUNKS,

    /** 內容、位置或生成失敗；清除失效顯示，不留下部分結果。 */
    SPAWN_FAILED,
}
