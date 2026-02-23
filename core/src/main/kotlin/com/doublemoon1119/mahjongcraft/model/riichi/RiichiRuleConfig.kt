package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.MahjongRuleConfig

/**
 * 日本麻將特有的規則配置介面。
 */
interface RiichiRuleConfig : MahjongRuleConfig {
    /** 是否使用赤寶牌（Aka Dora）。 */
    val useRedTiles: Boolean
}