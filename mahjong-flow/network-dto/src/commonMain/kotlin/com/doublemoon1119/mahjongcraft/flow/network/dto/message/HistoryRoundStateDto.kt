package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
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
 * @property dynamicRuleState 規則公開資訊。
 * @property hasPendingReaction 是否有待處理反應。
 * @property hasPendingRobbingReaction 是否有待處理搶和反應。
 * @property outcome 結算結果。
 */
@Serializable
data class HistoryRoundStateDto(
    val identity: HistoryReplayIdentityDto,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto,
    val tileCatalog: List<TileDto>,
    val players: List<HistoryReplayPlayerStateDto>,
    val wallTiles: List<Int>,
    val reservedTiles: List<Int>,
    val currentPlayerSeat: Int,
    val dealerSeat: Int,
    val prevalentWind: WindDto,
    val roundPosition: MatchRoundPositionDto,
    val comboCount: Int,
    val finishedPlayerSeats: Set<Int>,
    val dynamicRuleState: HistoryReplayRuleInformationDto?,
    val hasPendingReaction: Boolean,
    val hasPendingRobbingReaction: Boolean,
    val outcome: HistoryRoundOutcomeDto?,
)
