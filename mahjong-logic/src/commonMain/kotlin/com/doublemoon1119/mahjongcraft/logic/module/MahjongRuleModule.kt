package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
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

    /**
     * 建立該規則的初始動態桌況狀態。
     *
     * 由 [com.doublemoon1119.mahjongcraft.logic.table.GameInitializer] 在開局時寫入 `TableState.dynamicRuleState`。
     * 沒有動態狀態需求的規則（如目前的台灣麻將）可回傳 null。
     *
     * @return 該規則的初始 [DynamicRuleState]，若無則為 null。
     */
    fun createInitialDynamicState(): DynamicRuleState?

    /**
     * 建立該規則的初始玩家規則狀態。
     *
     * 由 [com.doublemoon1119.mahjongcraft.logic.table.GameInitializer] 在開局時寫入每位 `MahjongPlayer.playerRuleState`。
     * 沒有玩家規則狀態需求的規則（如目前的台灣麻將）可回傳 null。
     *
     * @return 該規則的初始 [PlayerRuleState]，若無則為 null。
     */
    fun createInitialPlayerRuleState(): PlayerRuleState?

    /**
     * 套用一次立直宣告（[GameAction.Riichi]）的規則特有狀態變化：把捨牌紀錄標記為立直宣告牌、
     * 更新玩家的規則狀態（立直/雙立直/一發資格）、更新桌況的動態規則狀態（如立直棒數量）。
     *
     * 呼叫前應已確認 [GameAction.Riichi] 目前合法（例如透過 [createLegalActionValidator]），
     * 且 [discardResult] 對應的捨牌後手牌仍聽牌——這兩者是規則無關的驗證，由呼叫端負責；
     * 這裡只處理套用規則特有狀態的部分，因此呼叫端（如 `:mahjong-flow` 的 use case）永遠
     * 不需要知道、也不需要轉型成任何規則專屬的具體型別。
     *
     * 不支援立直宣告的規則（如目前的台灣麻將）應回傳 null。
     *
     * @param tableState 目前的桌況（尚未套用本次宣告）。
     * @param player 宣告立直的玩家（尚未套用本次宣告）。
     * @param discardResult 打出宣告牌的捨牌結果。
     * @return 套用宣告後的新玩家實例與新的動態規則狀態，若此規則不支援立直宣告則為 null。
     */
    fun declareRiichi(tableState: TableState, player: MahjongPlayer, discardResult: Hand.DiscardResult): RiichiDeclarationResult?
}
