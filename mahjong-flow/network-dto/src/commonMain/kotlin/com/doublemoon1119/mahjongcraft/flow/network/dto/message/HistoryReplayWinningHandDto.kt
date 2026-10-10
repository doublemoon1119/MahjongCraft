package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史胡牌手牌 DTO。
 *
 * @property standingTiles 不含和牌張的有序立牌索引。
 * @property winningTile 獨立和牌索引；特殊結算時為 null。
 */
@Serializable
data class HistoryReplayWinningHandDto(
    val standingTiles: List<Int>,
    val winningTile: Int?,
)
