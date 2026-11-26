package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/**
 * 已授權公開的來源身分，不附位置或查詢能力。
 *
 * @property sourceId 完整來源識別碼。
 * @property subjectId 來源內的穩定主體 UUID。
 */
@Serializable
data class RoomParticipantIdentityDto(
    val sourceId: String,
    val subjectId: String,
)
