package com.doublemoon1119.mahjongcraft.domain.taiwan.factory

import com.doublemoon1119.mahjongcraft.domain.factory.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.domain.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.domain.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.domain.taiwan.TaiwanShantenCalculator

/**
 * 台灣麻將規則模組實作。
 *
 * 負責串接台灣麻將特有的組件，包含 [TaiwanWallFactory] 與 [TaiwanDiscardPile]。
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
    override fun createWallFactory(config: TaiwanRuleConfig): TileWallFactory {
        return TaiwanWallFactory(config)
    }

    /**
     * 建立台灣麻將專用的牌河。
     *
     * @param config 台灣麻將規則配置。
     * @return [TaiwanDiscardPile] 實體。
     */
    override fun createDiscardPile(config: TaiwanRuleConfig): DiscardPile<*> {
        return TaiwanDiscardPile()
    }

    /**
     * 建立台灣麻將的向聽數計算器。
     *
     * @param config 台灣麻將規則配置。
     * @return [TaiwanShantenCalculator] 實體。
     */
    override fun createShantenCalculator(config: TaiwanRuleConfig): ShantenCalculator {
        return TaiwanShantenCalculator()
    }
}
