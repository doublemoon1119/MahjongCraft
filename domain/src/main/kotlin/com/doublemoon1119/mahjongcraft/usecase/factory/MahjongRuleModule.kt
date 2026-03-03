package com.doublemoon1119.mahjongcraft.usecase.factory

import com.doublemoon1119.mahjongcraft.model.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.table.DiscardPile
import com.doublemoon1119.mahjongcraft.model.table.TileWallFactory

/**
 * 麻將規則模組介面。
 *
 * 本介面定義了特定麻將規則（如日麻、台麻）必須提供的核心組件工廠。
 * UseCase 層透過此介面獲取具體組件，而不直接依賴特定的規則實作。
 *
 * @param T 該模組所支援的規則配置型別，必須繼承自 [MahjongRuleConfig]。
 */
interface MahjongRuleModule<T : MahjongRuleConfig> {

    /**
     * 建立適用於該規則的牌山生成工廠。
     *
     * @param config 規則配置實例。
     * @return 實作了 [TileWallFactory] 的工廠物件。
     */
    fun createWallFactory(config: T): TileWallFactory

    /**
     * 建立適用於該規則的捨牌堆（牌河）實作。
     *
     * @param config 規則配置實例。
     * @return 實作了 [DiscardPile] 的物件。
     */
    fun createDiscardPile(config: T): DiscardPile<*>
}