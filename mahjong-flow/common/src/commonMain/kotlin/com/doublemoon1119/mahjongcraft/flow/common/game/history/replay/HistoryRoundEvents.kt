package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/** 歷史交易。
 * @property index 交易索引。
 * @property occurredAtEpochMillis 發生時間。
 * @property isOpening 是否為開局交易。
 * @property facts 有序語意事實。
 */
data class HistoryReplayTransaction(
    val index: Int,
    val occurredAtEpochMillis: Long,
    val isOpening: Boolean,
    val facts: List<HistoryReplayFact>,
)

/** 歷史局事件序列。
 * @property identity 對局識別資料。
 * @property roundNumber 局數。
 * @property transactions 交易序列。
 * @property nextTransactionIndex 下一個交易索引。
 * @property tileCatalog 截至回傳序列末端已宣告的局內牌目錄；不含後續交易的新牌。
 */
data class HistoryRoundEvents(
    val identity: HistoryReplayIdentity,
    val roundNumber: Int,
    val transactions: List<HistoryReplayTransaction>,
    val nextTransactionIndex: Int?,
    val tileCatalog: HistoryRoundTileCatalog,
)
