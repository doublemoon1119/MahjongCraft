package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 單一玩家在終局排行榜中的可持久化權威快照。
 *
 * @property playerId 玩家識別碼。
 * @property seatIndex 終局座位索引。
 * @property isAi 是否由 AI 操控。
 * @property initialSeatIndex 開局座位索引。
 * @property finalScore 最終分數。
 * @property finalRank 最終名次。
 */
data class MatchSettlementPlayerSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val initialSeatIndex: Int,
    val finalScore: Int,
    val finalRank: Int,
)
