package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import kotlinx.serialization.Serializable

/**
 * Replay 玩家桌況 DTO。
 *
 * @property initialSeatIndex 初始座位索引。
 * @property handTiles 手牌牌索引。
 * @property melds 副露。
 * @property lastDrawn 最近摸牌索引。
 * @property discards 捨牌。
 * @property score 分數。
 * @property seatWind 座風。
 * @property playerRuleState 規則公開資訊。
 * @property setAsideTiles 移出手牌、公開擺在桌上的牌索引（例如三人日麻拔出的北），依移出順序排列。
 */
@Serializable
data class HistoryReplayPlayerStateDto(
    val initialSeatIndex: Int,
    val handTiles: List<Int>,
    val melds: List<HistoryReplayMeldDto>,
    val lastDrawn: Int?,
    val discards: List<HistoryReplayDiscardDto>,
    val score: Int,
    val seatWind: WindDto,
    val playerRuleState: HistoryReplayRuleInformationDto?,
    val setAsideTiles: List<Int>,
)
