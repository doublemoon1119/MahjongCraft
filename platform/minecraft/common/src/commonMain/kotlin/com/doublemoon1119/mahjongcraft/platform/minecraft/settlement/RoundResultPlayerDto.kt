package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import kotlinx.serialization.Serializable

/**
 * 單一玩家結算前後的分數與名次。
 *
 * @property playerId 玩家 UUID 字串。
 * @property seatIndex 固定座位 index。
 * @property isAi 是否由 AI 操控。
 * @property previousScore 結算前總分。
 * @property currentScore 結算後總分。
 * @property previousRank 結算前名次，從 1 開始。
 * @property currentRank 結算後名次，從 1 開始。
 */
@Serializable
data class RoundResultPlayerDto(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
)
