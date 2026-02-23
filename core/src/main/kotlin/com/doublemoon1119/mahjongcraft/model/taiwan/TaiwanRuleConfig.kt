package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.MahjongRuleConfig

/**
 * 台灣麻將特有的規則配置介面。
 *
 * 繼承自 [MahjongRuleConfig] 並增加與台灣麻將補花機制相關的開關。
 */
interface TaiwanRuleConfig : MahjongRuleConfig {
    /**
     * 是否在遊戲中使用花牌（春夏秋冬、梅蘭竹菊）。
     *
     * 此布林值決定了遊戲流程中是否包含補花動作以及相關的胡牌類型。
     */
    val useFlowerTiles: Boolean
}