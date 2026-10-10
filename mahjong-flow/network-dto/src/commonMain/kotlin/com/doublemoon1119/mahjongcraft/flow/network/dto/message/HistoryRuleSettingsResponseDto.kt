package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import kotlinx.serialization.Serializable

/**
 * 單場歷史規則設定查詢的 S2C 回覆。
 *
 *
 * @property requestId 對應請求的識別碼。
 * @property config 對局開局時採用的完整遊戲設定；不可提供時為 null。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryRuleSettingsResponseDto(
    val requestId: String,
    val config: GameConfigDto?,
    val errorCode: HistoryQueryErrorCodeDto?,
)
