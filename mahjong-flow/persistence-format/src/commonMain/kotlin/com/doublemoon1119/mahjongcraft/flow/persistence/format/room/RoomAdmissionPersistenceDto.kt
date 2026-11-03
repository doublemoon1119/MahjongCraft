package com.doublemoon1119.mahjongcraft.flow.persistence.format.room

import kotlinx.serialization.Serializable

/**
 * 房間生命週期與加入限制的完整保存資料。
 *
 * @property instanceId 這次房間的識別碼。
 * @property members 成員與可信任來源的關聯。
 * @property excluded 被排除的來源與上次成員編號。
 */
@Serializable
data class RoomAdmissionPersistenceDto(
    val instanceId: String,
    val members: List<RoomParticipantPersistenceDto>,
    val excluded: List<RoomParticipantPersistenceDto>,
)
