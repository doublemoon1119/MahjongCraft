package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 既有牌牆 entity transition 的處理結果。 */
enum class MahjongTileWallTransitionResult {
    /** 所有需要的動畫已原子排入既有 entity 佇列。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 至少一張必要的既有管理中牌 entity 不存在。 */
    TILE_NOT_FOUND,

    /** 至少一段抽象 placement 無法建立安全路徑。 */
    INVALID_PATH,
}
