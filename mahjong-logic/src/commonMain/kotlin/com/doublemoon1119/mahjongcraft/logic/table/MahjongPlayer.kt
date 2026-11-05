package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import kotlin.uuid.Uuid

/**
 * 代表一名麻將玩家及其持有的資源與基本狀態。
 *
 * 本類別作為玩家數據的載體，封裝了手牌、牌河、分數及方位。
 *
 * 本類別為不可變值物件：所有會改變玩家狀態的操作皆不會修改原實例，
 * 而是回傳一個反映變更後狀態的新 [MahjongPlayer] 實例。
 *
 * @property tileAreas 規則建立的移出牌區；牌張仍由玩家持有，但不屬於手牌或牌河。
 * @property components 此對象攜帶的規則組件。
 * @property id 玩家的唯一識別碼。
 * @property initialSeatIndex 整場固定的起家座位順位，從 `0` 起算。
 * @property hand 該玩家的手牌實體。
 * @property discardPile 該玩家的牌河實體，其具體類型由遊戲規則決定。
 * @property score 玩家目前的總分（持點）。其初始值通常由 [TableState] 根據規則配置進行初始化。
 * @property seatWind 玩家本局由規則指派的自風／門風；不得用來反推莊家。
 * @property passedTilesInRound 玩家上次取牌（摸牌或鳴牌）後，交給玩家、但玩家沒有榮和的牌：他家每一張沒有被
 *                              榮和的捨牌，以及玩家有資格搶槓卻放過的槓牌。規則以此判斷下次取牌前的限制（例如
 *                              日麻的同巡振聽）；玩家摸牌或鳴牌時清除。
 * @property actionHistory 記錄玩家執行的動作歷史，用於判斷特定的胡牌役（如嶺上開花需要「槓牌 → 摸牌」的動作序列）。
 */
data class MahjongPlayer(
    val id: Uuid,
    override val initialSeatIndex: Int,
    val hand: Hand = Hand(),
    val discardPile: DiscardPile<*>,
    override val score: Int = 0,
    override val seatWind: Wind,
    val passedTilesInRound: Set<Tile> = emptySet(),
    val actionHistory: List<GameAction> = emptyList(),
    val components: ComponentSet = ComponentSet.EMPTY,
    val tileAreas: PlayerTileAreas = PlayerTileAreas.EMPTY,
) : RankablePlayer {
    init {
        components.requireTarget(BuiltInComponentOwners.PLAYER)
        require(initialSeatIndex >= 0) { "initialSeatIndex must not be negative" }
    }

    /** 所有移出牌區的當前牌張，依區域與區域內順序排列。 */
    val setAsideTiles: List<IdentifiedTile> get() = tileAreas.allTiles

    /**
     * 記錄一張交給玩家、但玩家沒有榮和的牌，見 [passedTilesInRound]。
     *
     * @param tile 放過的牌，由呼叫端先依規則的牌面解讀正規化。
     * @return 記錄後的新 [MahjongPlayer] 實例。
     */
    fun addPassedTile(tile: Tile): MahjongPlayer = copy(passedTilesInRound = passedTilesInRound + tile)

    /**
     * 清除當前巡迴中放過的牌。
     *
     * 玩家摸牌或鳴牌時呼叫。
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
