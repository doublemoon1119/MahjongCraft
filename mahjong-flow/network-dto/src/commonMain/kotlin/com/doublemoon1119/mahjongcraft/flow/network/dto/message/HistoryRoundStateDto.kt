package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.TilePoseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.TileWallPlacementDto
import kotlinx.serialization.Serializable

/**
 * Replay 桌況 DTO。
 *
 * @property identity 對局與玩家識別資料。
 * @property roundNumber 局序號。
 * @property position 局內位置。
 * @property tileCatalog 牌目錄。
 * @property players 玩家桌況。
 * @property wallTiles 活牌牆牌索引。
 * @property reservedTiles 保留牌索引。
 * @property currentPlayerSeat 行動玩家座位。
 * @property dealerSeat 莊家座位。
 * @property prevalentWind 場風。
 * @property roundPosition 保存的局位。
 * @property comboCount 連莊次數。
 * @property finishedPlayerSeats 已完成玩家座位。
 * @property hasPendingReaction 是否有待處理反應。
 * @property hasPendingRobbingReaction 是否有待處理搶和反應。
 * @property outcome 結算結果。
 * @property wallPlacements 依牌索引的實體牌牆格位；規則沒有實體牌牆時為 null。
 * @property discardOrder 全桌依打出先後排列的捨牌索引，包含已被鳴走的牌。
 * @property tilePoses 依牌索引保存的共同姿態；沒有列出的牌維持所在區域的預設姿態。
 * @property presentationNotes 保存時由外層附上的呈現附註原文（JSON 物件）；沒有附註時為 null。
 */
@Serializable
data class HistoryRoundStateDto(
    val identity: HistoryReplayIdentityDto,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto,
    val tileCatalog: List<TileFaceViewDto>,
    val players: List<HistoryReplayPlayerStateDto>,
    val wallTiles: List<Int>,
    val reservedTiles: List<Int>,
    val currentPlayerSeat: Int,
    val dealerSeat: Int,
    val prevalentWind: WindDto,
    val roundPosition: MatchRoundPositionDto,
    val comboCount: Int,
    val finishedPlayerSeats: Set<Int>,
    val hasPendingReaction: Boolean,
    val hasPendingRobbingReaction: Boolean,
    val outcome: HistoryRoundOutcomeDto?,
    val wallPlacements: Map<Int, TileWallPlacementDto>?,
    val discardOrder: List<Int>,
    val tilePoses: Map<Int, TilePoseDto>,
    val presentationNotes: String?,
)
