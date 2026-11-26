package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * Replay 交易 DTO。
 *
 * @property index 交易索引。
 * @property occurredAtEpochMillis 發生時間。
 * @property isOpening 是否為開局交易。
 * @property declaredTileCountAfter 交易後已宣告牌數量。
 * @property facts 有序語意事實。
 */
@Serializable
data class HistoryReplayTransactionDto(
    val index: Int,
    val occurredAtEpochMillis: Long,
    val isOpening: Boolean,
    val declaredTileCountAfter: Int,
    val facts: List<HistoryReplayFactDto>,
)
