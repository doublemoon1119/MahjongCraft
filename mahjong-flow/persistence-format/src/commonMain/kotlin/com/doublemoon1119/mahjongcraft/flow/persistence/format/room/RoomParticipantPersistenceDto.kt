package com.doublemoon1119.mahjongcraft.flow.persistence.format.room

import kotlinx.serialization.Serializable

/**
 * 成員編號與來源身分的保存對應。
 *
 * @property playerId 房間使用的成員編號。
 * @property sourceId 身分來源。
 * @property subjectId 來源內穩定的身分。
 */
@Serializable
data class RoomParticipantPersistenceDto(
    val playerId: String,
    val sourceId: String,
    val subjectId: String,
)
