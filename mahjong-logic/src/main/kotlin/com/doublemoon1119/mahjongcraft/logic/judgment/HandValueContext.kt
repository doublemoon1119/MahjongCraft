package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 手牌價值計算所需的上下文資訊介面。
 *
 * 各規則實作類（如立直麻將、台灣麻將）可實作此介面以提供具體的上下文類別。
 *
 * 此介面僅定義最通用的核心參數，各規則特有的參數應在具體實作中擴展。
 */
interface HandValueContext {
    /**
     * 玩家手牌（包含立牌與副露）。
     */
    val hand: Hand

    /**
     * 胡牌張（放銃或自摸的牌）。
     */
    val winningTile: Tile

    /**
     * 是否為自摸。
     */
    val isTsumo: Boolean

    /**
     * 是否有門前清（無副露）。
     */
    val isMenzen: Boolean

    /**
     * 圈風。
     */
    val roundWind: Wind

    /**
     * 自風。
     */
    val seatWind: Wind
}