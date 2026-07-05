package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Hand

/**
 * 計算麻將手牌「向聽數」的介面。
 *
 * 「向聽數」(Shanten) 是麻將術語，代表手牌距離「聽牌」(Tenpai) 狀態所需的最小進張數。
 * - 向聽數為 -1：代表已胡牌 (Agari)。
 * - 向聽數為 0：代表已聽牌 (Tenpai)，距離胡牌僅差一張。
 * - 向聽數 > 0：代表距離聽牌還需交換的牌數。
 *
 * 實作此介面的類別將根據特定的麻將規則（如立直麻將或台灣麻將），
 * 判斷手牌結構是否符合該規則的面子定義。
 */
interface ShantenCalculator {
    /**
     * 計算給定手牌的向聽數。
     *
     * @param hand 待分析的玩家手牌。
     * @return 包含計算結果的 [ShantenResult]。
     */
    fun calculate(hand: Hand): ShantenResult
}
