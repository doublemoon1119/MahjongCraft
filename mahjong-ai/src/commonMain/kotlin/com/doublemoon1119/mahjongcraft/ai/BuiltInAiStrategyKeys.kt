package com.doublemoon1119.mahjongcraft.ai

/** `:mahjong-ai` 內建期望值策略的登記 key；隨機出牌策略的 key 為 [RandomAiStrategy.KEY]。 */
object BuiltInAiStrategyKeys {
    /** 初級：只看自己的手牌與向聽。 */
    const val BEGINNER: String = "mahjongcraft:beginner"

    /** 中級：數場上的牌、對高威脅的對手防守，打點只粗略區分。 */
    const val INTERMEDIATE: String = "mahjongcraft:intermediate"

    /** 高級：使用完整打點，對所有對手各自防守、考慮名次，並以進階深度讀牌。 */
    const val ADVANCED: String = "mahjongcraft:advanced"
}
