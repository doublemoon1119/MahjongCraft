package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 規則桌面物件呈現請求的處理結果。 */
enum class TablePropPresentationResult {
    /** 桌上的規則桌面物件已更新為 [TablePropPresentation.placements] 描述的樣子。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成新物件失敗；這次已生成的部分會被移除，桌上原有的物件維持不變。 */
    SPAWN_FAILED,
}
