package com.doublemoon1119.mahjongcraft.logic.table

/**
 * 麻將方位定義。
 *
 * 用於表示玩家的座位（家風）或目前的場風（圈風）。
 * 依照東、南、西、北的順序排列。
 */
enum class Wind {
    /** 東 (Ton) */
    EAST,

    /** 南 (Nan) */
    SOUTH,

    /** 西 (Sha) */
    WEST,

    /** 北 (Pei) */
    NORTH,
}
