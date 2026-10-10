package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史胡牌詳情的有單位數值 DTO。
 *
 * @property unitId 規則定義的單位 ID。
 * @property amount 數值。
 */
@Serializable
data class HistoryWinDetailQuantityDto(val unitId: String, val amount: Int)
