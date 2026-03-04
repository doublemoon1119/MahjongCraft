package com.doublemoon1119.mahjongcraft.domain.base

/**
 * 麻將牌排序策略介面。
 * 基於 [Comparator] 實作，用於處理不同規則下的牌面排序。
 */
interface TileOrder : Comparator<Tile>