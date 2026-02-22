package com.doublemoon1119.mahjongcraft.model

import java.util.*

/**
 * 代表一名麻將玩家及其持有的資源與基本狀態。
 *
 * 本類別作為玩家數據的載體，封裝了手牌、牌河、分數及方位。
 *
 * @property id 玩家的唯一識別碼（通常對應 Minecraft 玩家的 UUID）。
 * @property name 玩家顯示名稱。
 * @property initialSeat 初始座位方位。
 * @property hand 該玩家的手牌實體。
 * @property discardPile 該玩家的牌河實體，其具體類型由遊戲規則決定。
 */
class MahjongPlayer(
    val id: UUID,
    val name: String,
    val initialSeat: Wind,
    val hand: Hand = Hand(),
    val discardPile: DiscardPile<*>
) {
    /** 玩家目前的總分（持點） */
    var score: Int = 0

    /** * 玩家目前的方位。
     * 隨連莊或過莊改變，用於判定當前局數中的親家/子家關係。
     */
    var currentWind: Wind = initialSeat
}

/**
 * 麻將方位定義。
 */
enum class Wind {
    /** 東 (Ton) */
    EAST,
    /** 南 (Nan) */
    SOUTH,
    /** 西 (Sha) */
    WEST,
    /** 北 (Pei) */
    NORTH
}