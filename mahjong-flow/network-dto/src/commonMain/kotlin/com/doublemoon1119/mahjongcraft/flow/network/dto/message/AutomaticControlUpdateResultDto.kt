package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 本局自動操作更新請求的權威回覆。
 *
 *
 * @property requestId 對應 [AutomaticControlUpdateRequestDto.requestId]。
 * @property gameId 對應請求中的對局識別碼。
 * @property result 權威處理結果。
 * @property snapshot 可安全提供時的最新個人權威快照。
 */
@Serializable
data class AutomaticControlUpdateResultDto(
    val requestId: String,
    val gameId: String,
    val result: AutomaticControlUpdateResultKindDto,
    val snapshot: AutomaticControlSnapshotDto?,
)
