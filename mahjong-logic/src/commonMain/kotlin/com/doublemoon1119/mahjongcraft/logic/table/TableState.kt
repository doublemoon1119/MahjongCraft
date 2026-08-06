package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * 代表一場麻將遊戲的通用全局狀態。
 *
 * 負責管理所有參與玩家、牌山、規則配置以及跨規則通用的局數資訊。
 *
 * @property id 當前遊戲的唯一識別碼
 * @property players 參與遊戲的玩家列表。
 * @property config 當前遊戲的規則配置，包含物理參數與計分規則。
 * @property tileWall 當前遊戲使用的牌山。
 * @property prevalentWind 當前的場風（圈風）。
 * @property roundNumber 當前的局數。
 * @property comboCount 連莊次數（日麻：本場數；台麻：連幾）。
 * @property currentPlayerIndex 目前輪到執行動作的玩家索引。
 * @property dynamicRuleState 規則特有的動態狀態實體（如日麻的立直棒、供託）。
 * @property pendingReaction 目前尚待其他玩家回應（吃/碰/槓/過）的捨牌反應視窗，若無則為 null。
 */
data class TableState(
    val id: Uuid,
    val players: List<MahjongPlayer>,
    val config: MahjongRuleConfig,
    val tileWall: TileWall,
    val prevalentWind: Wind = Wind.EAST,
    val roundNumber: Int = 1,
    val comboCount: Int = 0,
    val currentPlayerIndex: Int = 0,
    val dynamicRuleState: DynamicRuleState? = null,
    val pendingReaction: PendingReaction? = null
) {
    /** 獲取參與遊戲的總人數。 */
    val playerCount: Int get() = players.size

    /** 獲取目前輪到執行動作的玩家。 */
    val currentPlayer: MahjongPlayer get() = players[currentPlayerIndex]

    /**
     * 初始化對局。
     * 將所有玩家的分數根據 [config] 設定為初始分數，回傳套用後的新 [TableState] 實例。
     *
     * @return 所有玩家分數皆已初始化的新 [TableState] 實例。
     */
    fun init(): TableState {
        val initialScore = config.scoreConfig.initialScore
        return copy(players = players.map { it.copy(score = initialScore) })
    }

    /**
     * 根據指定玩家獲取其下家（逆時針下一位玩家）。
     *
     * @param player 指定的玩家。
     * @return 該玩家的下家。
     * @throws IllegalArgumentException 當玩家不在該桌子上時拋出。
     */
    fun getNextPlayer(player: MahjongPlayer): MahjongPlayer {
        val index = players.indexOf(player)
        require(index != -1) { "Player not found in this table" }
        return players[(index + 1) % playerCount]
    }

    /**
     * 依 [players] 目前的座位順序（即回合順序，與 [getNextPlayer] 使用同一套順序）計算
     * [toPlayerId] 相對於 [fromPlayerId] 的方位。
     *
     * 座位順序中的下一位玩家（[getNextPlayer]）即為 [fromPlayerId] 的下家（[RelativeDirection.Right]）；
     * 反之，順序中排在 [fromPlayerId] 前一位的玩家即為其上家（[RelativeDirection.Left]，
     * 也是唯一合法的吃牌來源）。
     *
     * 判斷順序：先判斷是否為自己、上家、下家，其餘（僅四人桌可能出現）才是對家。這個順序在三人桌
     * 這類 [playerCount] 較小的情境下格外重要——例如三人桌中「下一位」與「上一位」以外已經沒有
     * 第三種座位關係，此時「差值 2」同時等於「playerCount - 1」，必須被判定為上家而非對家。
     *
     * @param fromPlayerId 作為方位判斷基準的玩家 Uuid。
     * @param toPlayerId 欲判斷相對方位的玩家 Uuid。
     * @return [toPlayerId] 相對於 [fromPlayerId] 的方位。若兩者相同則為 [RelativeDirection.Self]。
     * @throws IllegalArgumentException 當任一玩家不在該桌子上時拋出。
     */
    fun relativeDirectionOf(fromPlayerId: Uuid, toPlayerId: Uuid): RelativeDirection {
        val fromIndex = players.indexOfFirst { it.id == fromPlayerId }
        require(fromIndex != -1) { "Player not found in this table" }
        val toIndex = players.indexOfFirst { it.id == toPlayerId }
        require(toIndex != -1) { "Player not found in this table" }

        val diff = (toIndex - fromIndex).mod(playerCount)
        return when {
            diff == 0 -> RelativeDirection.Self
            diff == playerCount - 1 -> RelativeDirection.Left
            diff == 1 -> RelativeDirection.Right
            else -> RelativeDirection.Across
        }
    }
}