package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose

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
 * @property wallPlacements 牌牆與保留區的牌在實體牌牆中的格位；規則沒有實體牌牆時為 null。
 * @property discardOrder 全桌依打出先後排列的捨牌，包含已被鳴走的牌。
 * @property tilePoses 保存了共同姿態的牌，例如正面朝上的牌牆牌或攤開的手牌；沒有列出的牌維持所在區域的預設姿態。
 * @property presentationNotes 保存時由外層附上的呈現附註原文（JSON 物件）；沒有附註時為 null。內容由附上它的外層解讀。
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
    val wallPlacements: Map<HistoryTileReference, TileWallPlacement>?,
    val discardOrder: List<HistoryTileReference>,
    val tilePoses: Map<HistoryTileReference, TilePose>,
    val presentationNotes: String?,
)
