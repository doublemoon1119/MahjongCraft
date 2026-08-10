package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** [RoomSnapshot] 的 observer-specific 網路 DTO。 */
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

fun RoomSnapshot.toDto(registries: NetworkDtoRegistries): RoomSnapshotDto = RoomSnapshotDto(
    id = id.toString(),
    hostId = hostId.toString(),
    config = config.toDto(registries),
    playerIds = playerIds.map { it.toString() }.toSet(),
    readyPlayerIds = readyPlayerIds.map { it.toString() }.toSet(),
    aiPlayerIds = aiPlayerIds.map { it.toString() }.toSet(),
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)

fun RoomSnapshotDto.toDomain(registries: NetworkDtoRegistries): RoomSnapshot = RoomSnapshot(
    id = Uuid.parse(id),
    hostId = Uuid.parse(hostId),
    config = config.toDomain(registries),
    playerIds = playerIds.map { Uuid.parse(it) }.toSet(),
    readyPlayerIds = readyPlayerIds.map { Uuid.parse(it) }.toSet(),
    aiPlayerIds = aiPlayerIds.map { Uuid.parse(it) }.toSet(),
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)
