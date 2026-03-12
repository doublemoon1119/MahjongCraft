package com.doublemoon1119.mahjongcraft.domain.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenResult

/**
 * 台灣麻將規則的向聽數計算器。
 *
 * 負責根據台灣麻將的規則（標準型：5面子 + 1雀頭）分析手牌。
 */
class TaiwanShantenCalculator : ShantenCalculator {
    /**
     * 計算給定手牌在台灣麻將規則下的向聽數。
     *
     * @param hand 待分析的玩家手牌。
     * @return 包含計算結果的 [ShantenResult]。
     */
    override fun calculate(hand: Hand): ShantenResult {
        // TODO: 實作台灣麻將的向聽數計算邏輯
        // 台灣麻將為 16 張手牌，標準型需組成 5 組面子與 1 組雀頭
        return ShantenResult(shanten = 8) // 暫時回傳一個預設值
    }
}
