package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.MahjongRuleConfig

/**
 * 日本麻將（Riichi Mahjong）特有的規則配置介面。
 *
 * 繼承自 [MahjongRuleConfig] 並增加與日麻計分和道具相關的參數。
 */
interface RiichiRuleConfig : MahjongRuleConfig {
    /**
     * 遊戲中使用的赤寶牌（Aka Dora）總數。
     *
     * 常見配置為 3 張（五萬、五筒、五條各一）或 4 張。
     */
    val redDoraCount: Int
}