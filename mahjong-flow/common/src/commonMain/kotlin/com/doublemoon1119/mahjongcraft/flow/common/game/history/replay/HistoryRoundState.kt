package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/** 歷史局桌況。
 * @property identity 對局識別資料。
 * @property roundNumber 局數。
 * @property position 局內位置。
 * @property tileCatalog 牌種目錄。
 * @property players 玩家桌況。
 * @property wallTiles 活牌牆牌索引。
 * @property reservedTiles 保留牌索引。
 * @property currentPlayerSeat 行動玩家座位。
 * @property dealerSeat 莊家座位。
 * @property prevalentWind 場風。
 * @property roundPosition 保存的局位。
 * @property comboCount 連莊次數。
 * @property finishedPlayerSeats 已完成玩家座位。
 * @property dynamicRuleState 規則公開資訊。
 * @property hasPendingReaction 是否仍有待處理的捨牌反應，不包含可執行命令。
 * @property hasPendingKanReaction 是否仍有待處理的槓牌反應，不包含可執行命令。
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
    val dynamicRuleState: HistoryReplayRuleInformation?,
    val hasPendingReaction: Boolean,
    val hasPendingKanReaction: Boolean,
    val outcome: HistoryRoundOutcome?,
)

/** 歷史玩家桌況。
 * @property initialSeatIndex 初始座位。
 * @property handTiles 立牌。
 * @property melds 副露。
 * @property lastDrawn 最近摸牌。
 * @property discards 捨牌。
 * @property score 分數。
 * @property seatWind 保存的座風。
 * @property playerRuleState 規則公開資訊。
 */
data class HistoryReplayPlayerState(
    val initialSeatIndex: Int,
    val handTiles: List<HistoryTileReference>,
    val melds: List<HistoryReplayMeld>,
    val lastDrawn: HistoryTileReference?,
    val discards: List<HistoryReplayDiscard>,
    val score: Int,
    val seatWind: Wind,
    val playerRuleState: HistoryReplayRuleInformation?,
)

/** 歷史副露。
 * @property type 副露種類。
 * @property tiles 副露牌。
 * @property sourceTile 來源牌。
 * @property sourceDirection 來源方向。
 */
data class HistoryReplayMeld(
    val type: MeldType,
    val tiles: List<HistoryTileReference>,
    val sourceTile: HistoryTileReference?,
    val sourceDirection: RelativeDirection?,
)

/** 歷史捨牌。
 * @property tile 捨出牌。
 * @property isTaken 是否已被副露取走。
 * @property markers 規則提供的 namespaced 捨牌標記。
 */
data class HistoryReplayDiscard(
    val tile: HistoryTileReference,
    val isTaken: Boolean,
    val markers: Set<String>,
)
