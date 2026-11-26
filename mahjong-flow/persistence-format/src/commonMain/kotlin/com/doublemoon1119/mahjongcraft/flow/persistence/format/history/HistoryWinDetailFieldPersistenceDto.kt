package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 單一胡牌詳情欄位的持久化表示。
 *
 * @property id 欄位識別碼。
 * @property value 欄位值。
 */
@Serializable
data class HistoryWinDetailFieldPersistenceDto(
    val id: String,
    val value: HistoryWinDetailValuePersistenceDto,
)
