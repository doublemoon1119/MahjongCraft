package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun HandSnapshot.toDto(): HandSnapshotDto = HandSnapshotDto(
    standingTiles = standingTiles.map { it.toDto() },
    lastDrawn = lastDrawn?.toDto(),
    melds = melds.map { it.toDto() },
)

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun HandSnapshotDto.toDomain(): HandSnapshot = HandSnapshot(
    standingTiles = standingTiles.map { it.toDomain() },
    lastDrawn = lastDrawn?.toDomain(),
    melds = melds.map { it.toDomain() },
)
