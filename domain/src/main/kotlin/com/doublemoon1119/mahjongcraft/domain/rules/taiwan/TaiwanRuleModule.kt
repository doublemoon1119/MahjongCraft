package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule

/**
 * 台灣麻將規則模組實作。
 *
 * 負責串接台灣麻將特有的組件，包含 [TaiwanWallFactory] 與 [TaiwanDiscardPile]。
 *
 * 每一個 [create] 方法都會根據傳入的 [config] 返回新的實例，
 * 以確保每個麻將桌可以擁有獨立的組件，實現規則配置的獨立性。
 */
class TaiwanRuleModule : MahjongRuleModule<TaiwanRuleConfig> {

    /**
     * 規則模組 ID：`mahjongcraft:taiwan`。
     */
    override val id: String = "mahjongcraft:taiwan"

    /**
     * 建立台灣麻將牌山工廠。
     *
     * @param config 台灣麻將規則配置。
     * @return [TaiwanWallFactory] 實體。
     */
    override fun createWallFactory(config: TaiwanRuleConfig): TaiwanWallFactory {
        return TaiwanWallFactory(config)
    }

    /**
     * 建立台灣麻將專用的牌河。
     *
     * @param config 台灣麻將規則配置。
     * @return [TaiwanDiscardPile] 實體。
     */
    override fun createDiscardPile(config: TaiwanRuleConfig): TaiwanDiscardPile {
        return TaiwanDiscardPile()
    }

    /**
     * 建立台灣麻將的向聽數計算器。
     *
     * @param config 台灣麻將規則配置。
     * @return [TaiwanShantenCalculator] 實體。
     */
    override fun createShantenCalculator(config: TaiwanRuleConfig): TaiwanShantenCalculator {
        return TaiwanShantenCalculator()
    }

    /**
     * 建立台灣麻將的合法動作判定器。
     *
     * @param config 台灣麻將規則配置。
     * @return [TaiwanLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(config: TaiwanRuleConfig): TaiwanLegalActionValidator {
        return TaiwanLegalActionValidator()
    }

    /**
     * 建立台灣麻將的手牌役種計算機。
     *
     * @param config 台灣麻將規則配置。
     * @return [HandValueCalculator] 實體。
     * @throws NotImplementedError 目前尚未實作 TaiwanHandValueCalculator。
     */
    override fun createHandValueCalculator(config: TaiwanRuleConfig): HandValueCalculator<*, *> {
        TODO("TaiwanHandValueCalculator is not yet implemented")
    }

    /**
     * 建立台灣麻將的手牌役種上下文計算機。
     *
     * @param config 台灣麻將規則配置。
     * @return [HandValueContextCalculator] 實體。
     * @throws NotImplementedError 目前尚未實作 TaiwanHandValueContextCalculator。
     */
    override fun createHandValueContextCalculator(config: TaiwanRuleConfig): HandValueContextCalculator<*, *> {
        TODO("TaiwanHandValueContextCalculator is not yet implemented")
    }
}
