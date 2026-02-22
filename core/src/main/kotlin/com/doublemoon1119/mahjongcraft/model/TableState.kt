package com.doublemoon1119.mahjongcraft.model

/**
 * 代表一場麻將遊戲的通用全局狀態。
 *
 * 負責管理所有參與玩家、牌山、以及跨規則通用的局數資訊。
 * 支援動態人數（如 3 人或 4 人麻將）。
 *
 * @property players 參與遊戲的玩家列表。列表順序即為逆時針出牌順序。
 * @property tileWall 當前遊戲使用的牌山。
 * @property prevalentWind 當前的場風（圈風），例如東場或南場。
 * @property roundNumber 當前的局數（如東一局的 1）。
 * @property comboCount 連莊次數。在日麻中為「本場數」，在台麻中為「連幾」。
 * @property currentPlayerIndex 目前輪到執行動作的玩家在 [players] 列表中的索引。
 * @property dynamicRuleState 規則特有的動態狀態實體，參考 [DynamicRuleState]。
 */
data class TableState(
    val players: List<MahjongPlayer>,
    val tileWall: TileWall,
    var prevalentWind: Wind = Wind.EAST,
    var roundNumber: Int = 1,
    var comboCount: Int = 0,
    var currentPlayerIndex: Int = 0,
    val dynamicRuleState: DynamicRuleState? = null
) {
    /** 獲取參與遊戲的總人數 */
    val playerCount: Int get() = players.size

    /** 獲取目前輪到執行動作的玩家 */
    val currentPlayer: MahjongPlayer get() = players[currentPlayerIndex]

    /**
     * 根據當前玩家獲取其下家（逆時針下一位玩家）。
     * 自動支援 3 人或 4 人模式。
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