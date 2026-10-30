package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 麻將牌排序策略介面。
 * 基於 [Comparator] 實作，用於處理不同規則下的牌面排序。
 */
interface TileOrder : Comparator<Tile> {
    /** 完整牌值的排列方式；預設按基本牌面，規則可另外比較已知特性。 */
    val valueOrder: Comparator<TileValue> get() = Comparator { first, second -> compare(first.tile, second.tile) }
}
