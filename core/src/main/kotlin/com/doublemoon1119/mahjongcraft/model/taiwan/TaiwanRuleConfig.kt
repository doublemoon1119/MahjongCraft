package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.MahjongRuleConfig

/**
 * 台灣麻將特有的規則配置。
 */
interface TaiwanRuleConfig : MahjongRuleConfig {
    /** 是否使用花牌。 */
    val useFlowerTiles: Boolean
}