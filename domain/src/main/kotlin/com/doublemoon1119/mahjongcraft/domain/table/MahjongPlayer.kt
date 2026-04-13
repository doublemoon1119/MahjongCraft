package com.doublemoon1119.mahjongcraft.domain.table

import com.doublemoon1119.mahjongcraft.domain.base.GameAction
import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed
import java.util.*

/**
 * 代表一名麻將玩家及其持有的資源與基本狀態。
 *
 * 本類別作為玩家數據的載體，封裝了手牌、牌河、分數及方位。
 *
 * @property id 玩家的唯一識別碼（通常對應 Minecraft 玩家的 UUID）。
 * @property name 玩家顯示名稱。
 * @property initialSeat 初始座位方位。
 * @property hand 該玩家的手牌實體。
 * @property discardPile 該玩家的牌河實體，其具體類型由遊戲規則決定。
 * @property playerRuleState 用於儲存規則特有的玩家狀態（如立直、振聽等）。
 *                          具體類型由各規則決定，例如 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiPlayerState]。
 */
class MahjongPlayer(
    val id: UUID,
    val name: String,
    val initialSeat: Wind,
    val hand: Hand = Hand(),
    val discardPile: DiscardPile<*>,
    val playerRuleState: PlayerRuleState? = null
) {
    /**
     * 玩家目前的總分（持點）。
     *
     * 其初始值通常由 [TableState] 根據規則配置進行初始化。
     */
    var score: Int = 0

    /**
     * 玩家目前的方位。
     *
     * 隨連莊或過莊改變，用於判定當前局數中的親家/子家關係。
     */
    var currentWind: Wind = initialSeat

    /**
     * 當前巡迴中玩家放過的牌（用於過水碰及同巡振聽判定）。
     *
     * 記錄玩家在當前巡迴中放過的牌：
     * - 放過碰牌機會 → 過水碰（之後不能碰）
     * - 放過榮和機會 → 同巡振聽（之後不能榮和）
     * 當玩家摸牌時（新的巡迴開始）應清除此集合。
     */
    private val _passedTilesInRound: MutableSet<Tile> = mutableSetOf()

    /**
     * 當前巡迴中玩家放過的牌集合（唯讀）。
     */
    val passedTilesInRound: Set<Tile> = _passedTilesInRound

    /**
     * 記錄玩家放過的牌（用於過水碰及同巡振聽判定）。
     *
     * @param tile 放過的牌（應為基礎類型，忽略赤寶牌屬性）。
     */
    fun addPassedTile(tile: Tile) {
        _passedTilesInRound.add(tile.withoutRed)
    }

    /**
     * 清除當前巡迴中放過的牌。
     *
     * 通常在玩家摸牌（新的巡迴開始）時呼叫。
     */
    fun clearPassedTiles() {
        _passedTilesInRound.clear()
    }

    /**
     * 記錄玩家執行的動作歷史。
     *
     * 用於記錄玩家在遊戲過程中執行的各項動作，如摸牌、捨牌、槓牌、胡牌等。
     * 此歷史記錄可用於判斷特定的胡牌役（如嶺上开花需要「槓牌 → 摸牌」的动作序列）。
     *
     * @see recordAction
     * @see clearActionHistory
     */
    private val _actionHistory: MutableList<GameAction> = mutableListOf()

    /**
     * 玩家動作歷史的唯讀列表。
     *
     * @see recordAction
     * @see clearActionHistory
     */
    val actionHistory: List<GameAction> = _actionHistory

    /**
     * 記錄玩家執行的動作。
     *
     * 將 [action] 加入動作歷史記錄中，用於後續判斷特定役種或其他遊戲邏輯。
     *
     * @param action 玩家執行的動作。
     * @see actionHistory
     * @see clearActionHistory
     */
    fun recordAction(action: GameAction) {
        _actionHistory.add(action)
    }

    /**
     * 清除動作歷史記錄。
     *
     * 通常在一局結束時呼叫，以重置記錄避免與下一局資料混淆。
     *
     * @see actionHistory
     * @see recordAction
     */
    fun clearActionHistory() {
        _actionHistory.clear()
    }
}
