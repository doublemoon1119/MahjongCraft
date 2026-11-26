package com.doublemoon1119.mahjongcraft.logic.table.layout

/** 保留區靠缺口一端的初始牌張堆疊方式。 */
internal enum class ReservedWallInitialShape {
    /** 每墩依摸牌順序先上層、後下層。 */
    STACKED,

    /** 最前兩張各自放在相鄰墩的下層，其餘牌維持成墩。 */
    SPLIT_FIRST_TWO,
}
