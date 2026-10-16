package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 歷史局桌況。
 *
 * @property identity 對局識別資料。
 * @property roundNumber 這一局在整場對局中的順序號，從 1 開始；連莊或續局也會遞增。
 * @property position 局內位置。
 * @property tileCatalog 牌種目錄。
 * @property players 玩家桌況。
 * @property wallTiles 活牌牆牌索引。
 * @property reservedTiles 保留牌索引。
 * @property currentPlayerSeat 行動玩家座位。
 * @property dealerSeat 莊家座位。
 * @property prevalentWind 場風。
 * @property roundPosition 保存的賽程局位；連莊時不變。
 * @property comboCount 連莊次數。
 * @property finishedPlayerSeats 已完成玩家座位。
 * @property hasPendingReaction 是否仍有待處理的捨牌反應，不包含可執行命令。
 * @property hasPendingRobbingReaction 是否仍有待處理的搶和反應，不包含可執行命令。
 * @property outcome 結算結果。
 */
data class HistoryRoundState(
    val identity: HistoryReplayIdentity,
    val roundNumber: Int,
    val position: HistoryRoundPosition,
    val tileCatalog: HistoryRoundTileCatalog,
    val players: List<HistoryReplayPlayerState>,
    val wallTiles: List<HistoryTileReference>,
    val reservedTiles: List<HistoryTileReference>,
    val currentPlayerSeat: Int,
    val dealerSeat: Int,
    val prevalentWind: Wind,
    val roundPosition: MatchRoundPosition,
    val comboCount: Int,
    val finishedPlayerSeats: Set<Int>,
    val hasPendingReaction: Boolean,
    val hasPendingRobbingReaction: Boolean,
    val outcome: HistoryRoundOutcome?,
)
