package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** [RoomSnapshot] 的 observer-specific 網路 DTO。 */
@Serializable
data class RoomSnapshotDto(
    val id: String,
    val hostId: String,
    val gameConfig: GameConfigDto,
    val playerIds: List<String>,
    val readyPlayerIds: List<String>,
    val aiPlayerIds: List<String>,
    val canStart: Boolean,
    val isHost: Boolean,
    val isInRoom: Boolean,
)

fun RoomSnapshot.toDto(registries: NetworkDtoRegistries): RoomSnapshotDto = RoomSnapshotDto(
    id = id.toString(),
    hostId = hostId.toString(),
    gameConfig = gameConfig.toDto(registries),
    playerIds = playerIds.map { it.toString() },
    readyPlayerIds = readyPlayerIds.map { it.toString() },
    aiPlayerIds = aiPlayerIds.map { it.toString() },
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)

fun RoomSnapshotDto.toDomain(registries: NetworkDtoRegistries): RoomSnapshot = RoomSnapshot(
    id = Uuid.parse(id),
    hostId = Uuid.parse(hostId),
    gameConfig = gameConfig.toDomain(registries),
    playerIds = playerIds.map { Uuid.parse(it) },
    readyPlayerIds = readyPlayerIds.map { Uuid.parse(it) },
    aiPlayerIds = aiPlayerIds.map { Uuid.parse(it) },
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)
