package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 正式牌牆呈現請求的處理結果。 */
enum class MahjongTileWallPresentationResult {
    /** 已替換同桌舊牌並建立所有新牌（或 [MahjongTileWallPresentation.finalLayout] 為空、只清除舊牌）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 其中一張牌無法加入世界；已回滾本次建立的牌。 */
    SPAWN_FAILED,

    /** 開門或翻面需要的既有管理中牌 entity 不存在。 */
    TILE_NOT_FOUND,

    /** 初始 assembly 與 final layout 無法建立安全開門路徑。 */
    INVALID_PATH,
}
