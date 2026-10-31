package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.NetworkDtoConverter
import com.doublemoon1119.mahjongcraft.logic.base.MeldSnapshot
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 已登記的公開組件格式。
 * @return 轉換後的資料。
 */
fun MeldSnapshot.toDto(registries: NetworkDtoConverter): MeldSnapshotDto = MeldSnapshotDto(
    id = id.toString(),
    componentViews = registries.componentViews.encode(componentViews),
    type = type.toDto(),
    tiles = tiles.map { it.toDto(registries) },
    sourceTile = sourceTile?.toDto(registries),
    sourceDirection = sourceDirection.toDto(),
    faceDownTileIds = faceDownTileIds.map { it.toString() }.toSet(),
)

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @param registries 已登記的公開組件格式。
 * @return 轉換後的資料。
 */
fun MeldSnapshotDto.toDomain(registries: NetworkDtoConverter): MeldSnapshot = MeldSnapshot(
    id = Uuid.parse(id),
    componentViews = registries.componentViews.decode(componentViews),
    type = type.toDomain(),
    tiles = tiles.map { it.toDomain(registries) },
    sourceTile = sourceTile?.toDomain(registries),
    sourceDirection = sourceDirection.toDomain(),
    faceDownTileIds = faceDownTileIds.map(Uuid::parse).toSet(),
)
