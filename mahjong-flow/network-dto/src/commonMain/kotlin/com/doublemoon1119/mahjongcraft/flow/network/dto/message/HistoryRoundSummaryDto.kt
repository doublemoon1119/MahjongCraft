package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史對局中可公開的單局索引。
 *
 *
 * @property roundNumber 局序號。
 * @property startedAtEpochMillis 該局開始 UTC epoch milliseconds。
 * @property endedAtEpochMillis 該局結束 UTC epoch milliseconds。
 */
@Serializable
data class HistoryRoundSummaryDto(
    val roundNumber: Int,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
)
