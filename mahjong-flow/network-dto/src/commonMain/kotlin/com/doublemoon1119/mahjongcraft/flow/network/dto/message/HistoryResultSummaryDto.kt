package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史對局中可公開的玩家終局結果。
 *
 *
 * @property playerId 玩家 UUID 字串。
 * @property finalScore 終局分數；資料不足時為 null。
 * @property finalRank 依規則模組比較器計算的終局名次；無法計算時為 null。
 */
@Serializable
data class HistoryResultSummaryDto(
    val playerId: String,
    val finalScore: Int?,
    val finalRank: Int?,
)
