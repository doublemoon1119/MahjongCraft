package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
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
     * 沒有動態狀態需求的規則可回傳 null。
     *
     * @return 該規則的初始 [DynamicRuleState]，若無則為 null。
     */
    fun createInitialDynamicState(): DynamicRuleState?

    /**
     * 建立該規則的初始玩家規則狀態。
     *
     * 由 [com.doublemoon1119.mahjongcraft.logic.table.GameInitializer] 在開局時寫入每位 `MahjongPlayer.playerRuleState`。
     * 沒有玩家規則狀態需求的規則可回傳 null。
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
     * 不支援立直宣告的規則應回傳 null。
     *
     * @param tableState 目前的桌況（尚未套用本次宣告）。
     * @param player 宣告立直的玩家（尚未套用本次宣告）。
     * @param discardResult 打出宣告牌的捨牌結果。
     * @return 套用宣告後的新玩家實例與新的動態規則狀態，若此規則不支援立直宣告則為 null。
     */
    fun declareRiichi(tableState: TableState, player: MahjongPlayer, discardResult: Hand.DiscardResult): RiichiDeclarationResult?

    /**
     * 因應「玩家摸牌」事件，套用規則特有的狀態清除。
     *
     * 例如日麻：摸牌代表一發窗口已經結束（本巡未能胡牌），需清除玩家的一發資格。
     * 沒有對應狀態需求的規則應直接回傳 [player] 本身，不做任何事。
     *
     * @param player 剛完成摸牌的玩家（已套用摸牌本身造成的變化）。
     * @return 套用規則特有狀態清除後的新玩家實例。
     */
    fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer

    /**
     * 因應「有玩家完成一次鳴牌（吃/碰/槓）」事件，套用規則特有的狀態清除。
     *
     * 例如日麻：任何一次鳴牌都會讓場上所有玩家的一發資格失效（不只鳴牌的當事人）。
     * 沒有對應狀態需求的規則應直接回傳 [players] 本身，不做任何事。
     *
     * @param players 鳴牌動作套用之後的完整玩家列表。
     * @return 套用規則特有狀態清除後的新玩家列表。
     */
    fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer>

    /**
     * 檢查本次碰／明槓是否觸發包牌責任，若觸發則寫入 [claimingPlayer] 的規則狀態。
     *
     * 必須在鳴牌動作實際套用到 [claimingPlayer] 手牌「之前」呼叫，以取得鳴牌當下、
     * 尚未加入新副露的手牌狀態。不支援包牌概念的規則應直接回傳
     * [claimingPlayer] 本身，不做任何事。
     *
     * @param claimingPlayer 執行碰／明槓的玩家（尚未套用本次鳴牌）。
     * @param calledTile 本次鳴取的他家捨牌。
     * @param sourceDirection 本次鳴取的來源相對方位。
     * @return 套用包牌責任（若觸發）後的新玩家實例。
     */
    fun applyPaoLiabilityIfTriggered(
        claimingPlayer: MahjongPlayer,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): MahjongPlayer

    /**
     * 計算一次自摸胡牌（[GameAction.Tsumo]）的點數結算：贏家實際獲得的點數，以及各應付款玩家
     * 應支付的金額（依莊家/閒家身分、包牌責任等區分）。
     *
     * 呼叫前應已確認 [GameAction.Tsumo] 目前合法（例如透過 [createLegalActionValidator]）——這是
     * 規則無關的驗證，由呼叫端負責；這裡只處理規則特有的點數計算與分攤方式，因此呼叫端（如
     * `:mahjong-flow` 的 use case）永遠不需要知道、也不需要轉型成任何規則專屬的具體型別。
     *
     * [player] 應為胡牌當下、尚未套用本次自摸任何變化的玩家實例（即 [tableState] 中對應的
     * `TableState.currentPlayer`），其 `Hand.lastDrawn` 即為胡牌張。實作內部需自行處理「胡牌張
     * 同時存在於 `hand.lastDrawn`、又要餵給役種計算所需的胡牌張參數」這個潛在的重複計數問題，
     * 呼叫端不需要、也不應該自行處理這個細節。
     *
     * 不支援自摸結算的規則應回傳 null。
     *
     * @param tableState 目前的桌況（尚未套用本次自摸結算）。
     * @param player 宣告自摸的玩家（尚未套用本次自摸結算），其 `hand.lastDrawn` 即為胡牌張。
     * @return 本次自摸的點數結算結果，若此規則不支援自摸結算、或 [player] 尚未摸牌則為 null。
     */
    fun declareTsumo(tableState: TableState, player: MahjongPlayer): TsumoResult?
}
