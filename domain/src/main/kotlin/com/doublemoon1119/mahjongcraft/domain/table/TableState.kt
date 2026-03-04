package com.doublemoon1119.mahjongcraft.domain.table

import com.doublemoon1119.mahjongcraft.domain.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig

/**
 * 代表一場麻將遊戲的通用全局狀態。
 *
 * 負責管理所有參與玩家、牌山、規則配置以及跨規則通用的局數資訊。
 *
 * @property players 參與遊戲的玩家列表。
 * @property tileWall 當前遊戲使用的牌山。
 * @property config 當前遊戲的規則配置，包含物理參數與計分規則。
 * @property prevalentWind 當前的場風（圈風）。
 * @property roundNumber 當前的局數。
 * @property comboCount 連莊次數（日麻：本場數；台麻：連幾）。
 * @property currentPlayerIndex 目前輪到執行動作的玩家索引。
 * @property dynamicRuleState 規則特有的動態狀態實體（如日麻的立直棒、供託）。
 */
data class TableState(
    val players: List<MahjongPlayer>,
    val tileWall: TileWall,
    val config: MahjongRuleConfig,
    var prevalentWind: Wind = Wind.EAST,
    var roundNumber: Int = 1,
    var comboCount: Int = 0,
    var currentPlayerIndex: Int = 0,
    val dynamicRuleState: DynamicRuleState? = null
) {
    /** 獲取參與遊戲的總人數。 */
    val playerCount: Int get() = players.size

    /** 獲取目前輪到執行動作的玩家。 */
    val currentPlayer: MahjongPlayer get() = players[currentPlayerIndex]

    /**
     * 初始化對局。
     * 將所有玩家的分數根據 [config] 設定為初始分數。
     */
    init {
        val initialScore = config.scoreConfig.initialScore
        players.forEach { it.score = initialScore }
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
}