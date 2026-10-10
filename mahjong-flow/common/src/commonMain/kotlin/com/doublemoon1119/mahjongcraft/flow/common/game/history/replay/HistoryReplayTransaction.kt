package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 歷史交易。
 *
 * @property index 交易索引。
 * @property occurredAtEpochMillis 發生時間。
 * @property isOpening 是否為開局交易。
 * @property facts 有序語意事實。
 * @property declaredTileCountAfter 交易後已宣告的實體牌數量；同種牌的不同實體分別計數。
 */
data class HistoryReplayTransaction(
    val index: Int,
    val occurredAtEpochMillis: Long,
    val isOpening: Boolean,
    val facts: List<HistoryReplayFact>,
    val declaredTileCountAfter: Int,
)
