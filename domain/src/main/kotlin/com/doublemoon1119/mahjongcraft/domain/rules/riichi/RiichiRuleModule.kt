package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule

/**
 * 日本麻將規則模組實作。
 *
 * 負責串接日本麻將特有的組件，包含 [RiichiWallFactory] 與 [RiichiDiscardPile]...等等。
 */
class RiichiRuleModule(
    override val id: String,
    override val config: RiichiRuleConfig
) : MahjongRuleModule<RiichiRuleConfig> {
    /**
     * 建立日本麻將牌山工廠。
     *
     * @return [RiichiWallFactory] 實體。
     */
    override fun createWallFactory(): RiichiWallFactory {
        return RiichiWallFactory(config)
    }

    /**
     * 建立日本麻將專用的牌河。
     *
     * @return [RiichiDiscardPile] 實體。
     */
    override fun createDiscardPile(): RiichiDiscardPile {
        return RiichiDiscardPile()
    }

    /**
     * 建立日本麻將的向聽數計算器。
     *
     * @return [RiichiShantenCalculator] 實體。
     */
    override fun createShantenCalculator(): RiichiShantenCalculator {
        return RiichiShantenCalculator()
    }

    /**
     * 建立日本麻將的合法動作判定器。
     *
     * @return [RiichiLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(): RiichiLegalActionValidator {
        return RiichiLegalActionValidator(
            shantenCalculator = createShantenCalculator(),
            handValueCalculator = createHandValueCalculator(),
            contextCalculator = createHandValueContextCalculator()
        )
    }

    /**
     * 建立日本麻將的手牌價值計算機。
     *
     * @return [RiichiHandValueCalculator] 實體。
     */
    override fun createHandValueCalculator(): RiichiHandValueCalculator {
        return RiichiHandValueCalculator(useLocalYaku = config.useLocalYaku)
    }

    /**
     * 建立日本麻將的手牌價值上下文計算機。
     *
     * @return [RiichiHandValueContextCalculator] 實體。
     */
    override fun createHandValueContextCalculator(): RiichiHandValueContextCalculator {
        return RiichiHandValueContextCalculator(config)
    }
}
