package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史胡牌詳情欄位 DTO。
 *
 * @property id 規則專屬欄位識別碼。
 * @property value 欄位值。
 */
@Serializable
data class HistoryWinDetailFieldDto(val id: String, val value: HistoryWinDetailValueDto)
