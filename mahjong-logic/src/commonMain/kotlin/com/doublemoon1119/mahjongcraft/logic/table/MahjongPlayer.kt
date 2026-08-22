package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.uuid.Uuid

/**
 * 代表一名麻將玩家及其持有的資源與基本狀態。
 *
 * 本類別作為玩家數據的載體，封裝了手牌、牌河、分數及方位。
 *
 * 本類別為不可變值物件：所有會改變玩家狀態的操作皆不會修改原實例，
 * 而是回傳一個反映變更後狀態的新 [MahjongPlayer] 實例。
 *
 * @property id 玩家的唯一識別碼（通常對應 Minecraft 玩家的 Uuid）。
 * @property initialSeat 初始座位方位。
 * @property hand 該玩家的手牌實體。
 * @property discardPile 該玩家的牌河實體，其具體類型由遊戲規則決定。
 * @property playerRuleState 用於儲存規則特有的玩家狀態（如立直、振聽等）。
 *                          具體類型由各規則決定，例如 [RiichiPlayerState]。
 * @property score 玩家目前的總分（持點）。其初始值通常由 [TableState] 根據規則配置進行初始化。
 * @property aiStrategyKey 若該玩家由電腦（AI）操控，這裡存放其 AI 策略的登記 key（例如
 *                `"random"`）；人類玩家維持 null。由 [GameInitializer.initialize]
 *                依開局時的 AI 玩家名單標記，此後隨玩家實例透過既有的 `.copy()` 機制自然延續。實際
 *                策略的解析（key → `MahjongAiStrategy` 實例）不在這一層，見 `:mahjong-ai` 的
 *                `MahjongAiStrategyRegistry`。
 * @property currentWind 玩家目前的方位。隨連莊或過莊改變，用於判定當前局數中的親家/子家關係。
 * @property passedTilesInRound 當前巡迴中玩家放過的牌（用於過水碰及同巡振聽判定）：
 *                              放過碰牌機會 → 過水碰（之後不能碰）；放過榮和機會 → 同巡振聽（之後不能榮和）。
 *                              當玩家摸牌時（新的巡迴開始）應清除此集合。
 * @property actionHistory 記錄玩家執行的動作歷史，用於判斷特定的胡牌役（如嶺上開花需要「槓牌 → 摸牌」的動作序列）。
 */
data class MahjongPlayer(
    val id: Uuid,
    override val initialSeat: Wind,
    val hand: Hand = Hand(),
    val discardPile: DiscardPile<*>,
    val playerRuleState: PlayerRuleState? = null,
    override val score: Int = 0,
    val aiStrategyKey: String? = null,
    override val currentWind: Wind = initialSeat,
    val passedTilesInRound: Set<Tile> = emptySet(),
    val actionHistory: List<GameAction> = emptyList(),
) : RankablePlayer {
    /** 是否由 AI 操控——[aiStrategyKey] 非 null 即代表是 AI，不需要另外存一個 Boolean。 */
    val isAi: Boolean get() = aiStrategyKey != null

    /**
     * 是否剛吃／碰成立，尚未捨牌。
     *
     * 吃／碰不像摸牌／槓會補牌，[Hand.lastDrawn] 因此維持 `null`；用 [actionHistory] 的最後一筆動作
     * 區分「回合剛開始，還沒摸牌」與「剛吃／碰，該直接捨牌」這兩種同樣 `lastDrawn == null` 的情境。
     */
    val justClaimedMeld: Boolean
        get() = actionHistory.lastOrNull().let { it is GameAction.Chi || it is GameAction.Pon }

    /**
     * 記錄玩家放過的牌（用於過水碰及同巡振聽判定）。
     *
     * @param tile 放過的原始牌；規則特有的等價轉換由規則層處理。
     * @return 記錄後的新 [MahjongPlayer] 實例。
     */
    fun addPassedTile(tile: Tile): MahjongPlayer = copy(passedTilesInRound = passedTilesInRound + tile)

    /**
     * 清除當前巡迴中放過的牌。
     *
     * 通常在玩家摸牌（新的巡迴開始）時呼叫。
     *
     * @return 清除後的新 [MahjongPlayer] 實例。
     */
    fun clearPassedTiles(): MahjongPlayer = copy(passedTilesInRound = emptySet())

    /**
     * 記錄玩家執行的動作。
     *
     * 將 [action] 加入動作歷史記錄中，用於後續判斷特定役種或其他遊戲邏輯。
     *
     * @param action 玩家執行的動作。
     * @return 記錄後的新 [MahjongPlayer] 實例。
     * @see actionHistory
     */
    fun recordAction(action: GameAction): MahjongPlayer = copy(actionHistory = actionHistory + action)

    /**
     * 清除動作歷史記錄。
     *
     * 通常在一局結束時呼叫，以重置記錄避免與下一局資料混淆。
     *
     * @return 清除後的新 [MahjongPlayer] 實例。
     * @see actionHistory
     */
    fun clearActionHistory(): MahjongPlayer = copy(actionHistory = emptyList())
}
