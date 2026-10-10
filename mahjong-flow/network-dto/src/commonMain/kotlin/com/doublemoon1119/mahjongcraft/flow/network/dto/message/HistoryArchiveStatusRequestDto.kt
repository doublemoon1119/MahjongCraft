package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 查詢指定對局保存狀態的 C2S 要求。
 *
 *
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property matchId 欲查詢的對局識別碼。
 */
@Serializable
data class HistoryArchiveStatusRequestDto(
    val requestId: String,
    val matchId: String,
)
