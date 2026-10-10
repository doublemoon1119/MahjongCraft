package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 指定對局保存狀態的 S2C 回覆。
 *
 *
 * @property requestId 對應要求的識別碼。
 * @property status 伺服器依權威證據判定的保存狀態。
 * @property errorCode 查詢流程失敗時的穩定錯誤碼；狀態判定成功時為 null。
 */
@Serializable
data class HistoryArchiveStatusResponseDto(
    val requestId: String,
    val status: HistoryArchiveStatusDto,
    val errorCode: HistoryQueryErrorCodeDto?,
)
