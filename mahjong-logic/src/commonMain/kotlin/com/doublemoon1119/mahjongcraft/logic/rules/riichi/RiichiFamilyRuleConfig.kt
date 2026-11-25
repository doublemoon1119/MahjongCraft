package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 四人與三人日本麻將共用的規則配置。共用的日麻計算元件只依賴這裡的欄位。
 *
 * @property allowOpenTanyao 是否允許食斷（斷么九鳴牌有效）。
 * @property useLocalYaku 是否啟用古役。
 * @property redDoraCount 赤寶牌總張數。
 * @property scoreConfig 日麻計分設定。
 * @property gameLength 日麻對局長度。
 * @property usesThreePlayerTiles 是否使用三人麻將的牌組（沒有二～八萬）。此時一萬與九萬互為寶牌指示牌。
 * @property rinshanTileCount 王牌最前方供補牌的嶺上牌張數。寶牌指示牌從這些牌之後開始。
 * @property transparentTiles 是否使用透明牌：同一種牌的四張中，部分實體牌對所有人公開牌面。
 * @property buildsWall 是否砌牌牆。為 false 時牌不排成牌牆，摸牌時才從牌池取出。
 */
interface RiichiFamilyRuleConfig : MahjongRuleConfig {
    val allowOpenTanyao: Boolean
    val useLocalYaku: Boolean
    val redDoraCount: Int
    override val scoreConfig: RiichiScoreConfig
    override val gameLength: RiichiGameLength
    val usesThreePlayerTiles: Boolean
    val rinshanTileCount: Int
    val transparentTiles: Boolean
    val buildsWall: Boolean
}
