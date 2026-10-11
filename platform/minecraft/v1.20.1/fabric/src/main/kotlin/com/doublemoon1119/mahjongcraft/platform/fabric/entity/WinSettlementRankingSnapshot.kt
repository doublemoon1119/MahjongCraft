package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 和牌結算前後的分數與排名快照。
 *
 * @property playerId 玩家識別碼。
 * @property seatIndex 座位索引。
 * @property isAi 是否由 AI 操控。
 * @property previousScore 結算前分數。
 * @property currentScore 結算後分數。
 * @property previousRank 結算前名次。
 * @property currentRank 結算後名次。
 * @property paymentReasonId 點數支付原因識別碼；沒有指定時為 null。
 */
data class WinSettlementRankingSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
    val paymentReasonId: String? = null,
)
