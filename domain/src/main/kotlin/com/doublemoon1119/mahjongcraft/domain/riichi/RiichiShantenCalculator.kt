package com.doublemoon1119.mahjongcraft.domain.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenResult

/**
 * 立直麻將規則的向聽數計算器。
 *
 * 負責根據立直麻將的規則（包含標準型、七對子、國士無雙）分析手牌。
 */
class RiichiShantenCalculator : ShantenCalculator {
    /**
     * 計算給定手牌在立直麻將規則下的向聽數。
     *
     * @param hand 待分析的玩家手牌。
     * @return 包含計算結果的 [ShantenResult]。
     */
    override fun calculate(hand: Hand): ShantenResult {
        // TODO: 實作立直麻將的向聽數計算邏輯
        // 1. 標準型 (4面子 + 1雀頭) 的向聽數計算
        // 2. 七對子 (Seven Pairs) 的向聽數計算
        // 3. 國士無雙 (Thirteen Orphans) 的向聽數計算
        // 4. 回傳上述計算結果中的最小值
        return ShantenResult(shanten = 8) // 暫時回傳一個預設值
    }
}
