package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 單局的公開摘要。
 *
 * @property roundNumber 局序號。
 * @property startedAtEpochMillis 該局開始時間。
 * @property endedAtEpochMillis 該局結束時間。
 */
data class HistoryRoundSummary(
    val roundNumber: Int,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
)
