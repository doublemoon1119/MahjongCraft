package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TileWallFactory

/**
 * 麻將規則模組介面。
 *
 * 本介面定義了特定麻將規則（如日麻、台麻）必須提供的核心組件。
 * UseCase 層透過此介面獲取具體組件，而不直接依賴特定的規則實作。
 *
 * @param T 該模組所支援的規則配置型別，必須繼承自 [MahjongRuleConfig]。
 */
interface MahjongRuleModule<T : MahjongRuleConfig> {
    /**
     * 規則模組的唯一識別碼。
     * 此 ID 由註冊中心於實例化時注入，代表該模組在系統中的身份。
     */
    val id: String

    /**
     * 該模組實例所持有的規則配置。
     */
    val config: T

    /**
     * 建立適用於該規則的牌山生成工廠。
     *
     * @return 實作了 [TileWallFactory] 的工廠物件。
     */
    fun createWallFactory(): TileWallFactory

    /**
     * 建立適用於該規則的捨牌堆（牌河）實作。
     *
     * @return 實作了 [DiscardPile] 的物件。
     */
    fun createDiscardPile(): DiscardPile<*>

    /**
     * 建立適用於該規則的向聽數計算器 (Shanten Calculator)。
     *
     * 負責分析手牌距離聽牌狀態的最小進張數。
     *
     * @return 實作了 [ShantenCalculator] 的物件。
     */
    fun createShantenCalculator(): ShantenCalculator

    /**
     * 建立適用於該規則的合法動作判定器 (Legal Action Validator)。
     *
     * 負責根據當前遊戲狀態判斷玩家可以執行的所有合法動作。
     *
     * @return 實作了 [LegalActionValidator] 的物件。
     */
    fun createLegalActionValidator(): LegalActionValidator

    /**
     * 建立適用於該規則的手牌役種計算機 (Hand Value Calculator)。
     *
     * 負責計算手牌的役種、番數（或台數），用於胡牌結算與役種顯示。
     *
     * @return 實作了 [HandValueCalculator] 的物件。
     */
    fun createHandValueCalculator(): HandValueCalculator<*, *>

    /**
     * 建立適用於該規則的手牌役種上下文計算機 (Hand Value Context Calculator)。
     *
     * 負責根據當前遊戲狀態計算役種結算所需的上下文資訊，
     * 例如：寶牌指示牌、裏寶牌、海底撈月、河底撈魚等。
     *
     * @return 實作了 [HandValueContextCalculator] 的物件。
     */
    fun createHandValueContextCalculator(): HandValueContextCalculator<*, *>
}
