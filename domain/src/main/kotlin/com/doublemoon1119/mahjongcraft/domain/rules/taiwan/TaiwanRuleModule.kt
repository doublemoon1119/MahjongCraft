package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule

/**
 * 台灣麻將規則模組實作。
 *
 * 負責串接台灣麻將特有的組件，包含 [TaiwanWallFactory] 與 [TaiwanDiscardPile]...等等。
 */
class TaiwanRuleModule(
    override val id: String,
    override val config: TaiwanRuleConfig
) : MahjongRuleModule<TaiwanRuleConfig> {
    /**
     * 建立台灣麻將牌山工廠。
     *
     * @return [TaiwanWallFactory] 實體。
     */
    override fun createWallFactory(): TaiwanWallFactory {
        return TaiwanWallFactory(config)
    }

    /**
     * 建立台灣麻將專用的牌河。
     *
     * @return [TaiwanDiscardPile] 實體。
     */
    override fun createDiscardPile(): TaiwanDiscardPile {
        return TaiwanDiscardPile()
    }

    /**
     * 建立台灣麻將的向聽數計算器。
     *
     * @return [TaiwanShantenCalculator] 實體。
     */
    override fun createShantenCalculator(): TaiwanShantenCalculator {
        return TaiwanShantenCalculator()
    }

    /**
     * 建立台灣麻將的合法動作判定器。
     *
     * @return [TaiwanLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(): TaiwanLegalActionValidator {
        return TaiwanLegalActionValidator()
    }

    /**
     * 建立台灣麻將的手牌役種計算機。
     *
     * @return [HandValueCalculator] 實體。
     * @throws NotImplementedError 目前尚未實作 TaiwanHandValueCalculator。
     */
    override fun createHandValueCalculator(): HandValueCalculator<*, *> {
        TODO("TaiwanHandValueCalculator is not yet implemented")
    }

    /**
     * 建立台灣麻將的手牌役種上下文計算機。
     *
     * @return [HandValueContextCalculator] 實體。
     * @throws NotImplementedError 目前尚未實作 TaiwanHandValueContextCalculator。
     */
    override fun createHandValueContextCalculator(): HandValueContextCalculator<*, *> {
        TODO("TaiwanHandValueContextCalculator is not yet implemented")
    }
}
