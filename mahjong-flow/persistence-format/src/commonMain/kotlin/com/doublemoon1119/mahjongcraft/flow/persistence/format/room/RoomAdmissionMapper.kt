package com.doublemoon1119.mahjongcraft.flow.persistence.format.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import kotlin.uuid.Uuid

/**
 * 保存房間加入限制，不使用目前登記內容重新推算來源。
 *
 * @return 完整保存資料。
 */
fun RoomAdmissionState.toPersistenceDto(): RoomAdmissionPersistenceDto = RoomAdmissionPersistenceDto(
    instanceId = instanceId.toString(),
    members = memberIdentities.map { (player, identity) -> RoomParticipantPersistenceDto(player.toString(), identity.sourceId, identity.subjectId.toString()) },
    excluded = excludedParticipants.map { (identity, player) -> RoomParticipantPersistenceDto(player.toString(), identity.sourceId, identity.subjectId.toString()) },
)

/**
 * 還原房間加入限制，拒絕重複的成員與來源身分。
 *
 * @return 已驗證的加入限制。
 */
fun RoomAdmissionPersistenceDto.toDomain(): RoomAdmissionState {
    val memberEntries = members.map { Uuid.parse(it.playerId) to RoomParticipantIdentity(it.sourceId, Uuid.parse(it.subjectId)) }
    val excludedEntries = excluded.map { RoomParticipantIdentity(it.sourceId, Uuid.parse(it.subjectId)) to Uuid.parse(it.playerId) }
    require(memberEntries.distinctBy { it.first }.size == memberEntries.size) { "Duplicate admission member" }
    require(memberEntries.distinctBy { it.second }.size == memberEntries.size) { "Duplicate admission identity" }
    require(excludedEntries.distinctBy { it.first }.size == excludedEntries.size) { "Duplicate excluded identity" }
    return RoomAdmissionState(
        instanceId = Uuid.parse(instanceId),
        memberIdentities = memberEntries.toMap(),
        excludedParticipants = excludedEntries.toMap(),
    )
}
