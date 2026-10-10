package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 歷史局事件序列。
 *
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
