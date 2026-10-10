package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史贏家詳情 DTO。
 *
 * @property seatIndex 贏家座位。
 * @property detailFields 規則專屬詳情欄位。
 * @property hand 保存結算順序的立牌與獨立和牌張；未記錄時為 null。
 */
@Serializable
data class HistoryWinnerDetailsDto(
    val seatIndex: Int,
    val detailFields: List<HistoryWinDetailFieldDto>,
    val hand: HistoryReplayWinningHandDto?,
)
