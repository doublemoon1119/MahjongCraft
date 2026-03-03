package com.doublemoon1119.mahjongcraft.model.taiwan.factory

import com.doublemoon1119.mahjongcraft.model.table.DiscardPile
import com.doublemoon1119.mahjongcraft.model.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.usecase.factory.MahjongRuleModule

/**
 * 台灣麻將規則模組實作。
 *
 * 負責串接台灣麻將特有的組件，包含 [TaiwanWallFactory] 與 [TaiwanDiscardPile]。
 */
class TaiwanRuleModule : MahjongRuleModule<TaiwanRuleConfig> {

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
}