package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class RoomSnapshotDto(
    val id: String,
    val hostId: String,
    val config: MahjongRuleConfigDto,
    val playerIds: Set<String>,
    val readyPlayerIds: Set<String>,
    val aiPlayerIds: Set<String>,
    val canStart: Boolean,
    val isHost: Boolean,
    val isInRoom: Boolean,
)

fun RoomSnapshot.toDto(): RoomSnapshotDto = RoomSnapshotDto(
    id = id.toString(),
    hostId = hostId.toString(),
    config = config.toDto(),
    playerIds = playerIds.map { it.toString() }.toSet(),
    readyPlayerIds = readyPlayerIds.map { it.toString() }.toSet(),
    aiPlayerIds = aiPlayerIds.map { it.toString() }.toSet(),
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)

fun RoomSnapshotDto.toDomain(): RoomSnapshot = RoomSnapshot(
    id = Uuid.parse(id),
    hostId = Uuid.parse(hostId),
    config = config.toDomain(),
    playerIds = playerIds.map { Uuid.parse(it) }.toSet(),
    readyPlayerIds = readyPlayerIds.map { Uuid.parse(it) }.toSet(),
    aiPlayerIds = aiPlayerIds.map { Uuid.parse(it) }.toSet(),
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)
