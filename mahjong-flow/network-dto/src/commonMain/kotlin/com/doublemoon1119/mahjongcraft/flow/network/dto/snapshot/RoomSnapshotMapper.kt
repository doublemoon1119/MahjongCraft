package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun RoomSnapshot.toDto(registries: NetworkDtoRegistries): RoomSnapshotDto = RoomSnapshotDto(
    id = id.toString(),
    hostId = hostId.toString(),
    gameConfig = gameConfig.toDto(registries),
    playerIds = playerIds.map { it.toString() },
    readyPlayerIds = readyPlayerIds.map { it.toString() },
    aiPlayerIds = aiPlayerIds.map { it.toString() },
    aiPlayerStrategyKeys = aiPlayerStrategyKeys.mapKeys { it.key.toString() },
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)

/**
 * 將網路 DTO 還原為領域資料。
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun RoomSnapshotDto.toDomain(registries: NetworkDtoRegistries): RoomSnapshot = RoomSnapshot(
    id = Uuid.parse(id),
    hostId = Uuid.parse(hostId),
    gameConfig = gameConfig.toDomain(registries),
    playerIds = playerIds.map { Uuid.parse(it) },
    readyPlayerIds = readyPlayerIds.map { Uuid.parse(it) },
    aiPlayerIds = aiPlayerIds.map { Uuid.parse(it) },
    aiPlayerStrategyKeys = aiPlayerStrategyKeys.mapKeys { Uuid.parse(it.key) },
    canStart = canStart,
    isHost = isHost,
    isInRoom = isInRoom,
)
