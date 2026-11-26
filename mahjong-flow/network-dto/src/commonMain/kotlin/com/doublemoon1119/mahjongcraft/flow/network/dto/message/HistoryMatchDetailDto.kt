package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單場歷史摘要及其局級索引。
 *
 *
 * @property summary 對局摘要。
 * @property rounds 已知的局級摘要。
 */
@Serializable
data class HistoryMatchDetailDto(
    val summary: HistoryMatchSummaryDto,
    val rounds: List<HistoryRoundSummaryDto>,
)
