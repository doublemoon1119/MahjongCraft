package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.base.MeldSnapshot

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun MeldSnapshot.toDto(): MeldSnapshotDto = MeldSnapshotDto(
    type = type.toDto(),
    tiles = tiles.map { it.toDto() },
    sourceTile = sourceTile?.toDto(),
    sourceDirection = sourceDirection.toDto(),
)

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun MeldSnapshotDto.toDomain(): MeldSnapshot = MeldSnapshot(
    type = type.toDomain(),
    tiles = tiles.map { it.toDomain() },
    sourceTile = sourceTile?.toDomain(),
    sourceDirection = sourceDirection.toDomain(),
)
