package com.doublemoon1119.mahjongcraft.domain.riichi

import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory

/**
 * 日本麻將規則模組實作。
 *
 * 負責串接日本麻將特有的組件，包含 [RiichiWallFactory] 與 [RiichiDiscardPile]。
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
}
