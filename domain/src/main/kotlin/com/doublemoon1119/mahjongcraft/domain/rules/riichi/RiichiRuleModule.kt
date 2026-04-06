package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.HandYakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory

/**
 * 日本麻將規則模組實作。
 *
 * 負責串接日本麻將特有的組件，包含 [RiichiWallFactory] 與 [RiichiDiscardPile]。
 *
 * 每一個 `create` 方法都會根據傳入的 `config` 返回新的實例，
 * 以確保每個麻將桌可以擁有獨立的組件，實現規則配置的獨立性。
 */
class RiichiRuleModule : MahjongRuleModule<RiichiRuleConfig> {

    /**
     * 規則模組 ID：`mahjongcraft:riichi`。
     */
    override val id: String = "mahjongcraft:riichi"

    /**
     * 建立日本麻將牌山工廠。
     *
     * @param config 日本麻將規則配置。
     * @return [RiichiWallFactory] 實體。
     */
    override fun createWallFactory(config: RiichiRuleConfig): TileWallFactory {
        return RiichiWallFactory(config)
    }

    /**
     * 建立日本麻將專用的牌河。
     *
     * @param config 日本麻將規則配置。
     * @return [RiichiDiscardPile] 實體。
     */
    override fun createDiscardPile(config: RiichiRuleConfig): DiscardPile<*> {
        return RiichiDiscardPile()
    }

    /**
     * 建立日本麻將的向聽數計算器。
     *
     * @param config 日本麻將規則配置。
     * @return [RiichiShantenCalculator] 實體。
     */
    override fun createShantenCalculator(config: RiichiRuleConfig): ShantenCalculator {
        return RiichiShantenCalculator()
    }

    /**
     * 建立日本麻將的合法動作判定器。
     *
     * @param config 日本麻將規則配置。
     * @return [RiichiLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(config: RiichiRuleConfig): LegalActionValidator {
        val shantenCalculator = createShantenCalculator(config)

        @Suppress("UNCHECKED_CAST")
        val handValueCalculator = createHandValueCalculator(config) as HandValueCalculator<RiichiYakuContext, *>
        return RiichiLegalActionValidator(
            shantenCalculator = shantenCalculator,
            handValueCalculator = handValueCalculator,
        )
    }

    /**
     * 建立日本麻將的手牌役種計算機。
     *
     * @param config 日本麻將規則配置。
     * @return [RiichiHandValueCalculator] 實體。
     */
    override fun createHandValueCalculator(config: RiichiRuleConfig): HandValueCalculator<RiichiYakuContext, HandYakuResult> {
        return RiichiHandValueCalculator(useLocalYaku = config.useLocalYaku)
    }
}
