package com.doublemoon1119.mahjongcraft.logic.table.layout

/** 保留區軌道可使用的牌牆範圍。 */
internal enum class ReservedWallTrackScope {
    /** 整段軌道固定在開門面，不跨越桌角。 */
    SINGLE_SIDE,

    /** 軌道可沿環狀牌牆跨越桌角。 */
    CIRCULAR,
}
