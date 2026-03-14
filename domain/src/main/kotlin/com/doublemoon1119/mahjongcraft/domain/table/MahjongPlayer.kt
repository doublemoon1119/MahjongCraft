package com.doublemoon1119.mahjongcraft.domain.table

import com.doublemoon1119.mahjongcraft.domain.base.Hand
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
 * @property playerRuleState 用於儲存規則特有的玩家狀態（如立直、振聽等）。
 *                          具體類型由各規則決定，例如 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiPlayerState]。
 */
class MahjongPlayer(
    val id: UUID,
    val name: String,
    val initialSeat: Wind,
    val hand: Hand = Hand(),
    val discardPile: DiscardPile<*>,
    val playerRuleState: PlayerRuleState? = null
) {
    /**
     * 玩家目前的總分（持點）。
     *
     * 其初始值通常由 [TableState] 根據規則配置進行初始化。
     */
    var score: Int = 0

    /**
     * 玩家目前的方位。
     *
     * 隨連莊或過莊改變，用於判定當前局數中的親家/子家關係。
     */
    var currentWind: Wind = initialSeat
}
