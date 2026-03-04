package com.doublemoon1119.mahjongcraft.domain.riichi.factory

import com.doublemoon1119.mahjongcraft.domain.factory.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.domain.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory

/**
 * 日本麻將規則模組實作。
 *
 * 負責串接日本麻將特有的組件，包含 [RiichiWallFactory] 與 [RiichiDiscardPile]。
 */
class RiichiRuleModule : MahjongRuleModule<RiichiRuleConfig> {

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
}