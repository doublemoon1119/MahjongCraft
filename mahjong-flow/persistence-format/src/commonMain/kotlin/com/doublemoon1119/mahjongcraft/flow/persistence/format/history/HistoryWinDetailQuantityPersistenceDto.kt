package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 有單位數值的持久化表示。
 *
 * @property unitId 規則定義的單位 ID。
 * @property amount 數值。
 */
@Serializable
data class HistoryWinDetailQuantityPersistenceDto(
    val unitId: String,
    val amount: Int,
)
