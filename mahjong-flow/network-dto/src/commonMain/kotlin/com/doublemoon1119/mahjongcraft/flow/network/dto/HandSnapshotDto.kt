package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import kotlinx.serialization.Serializable

/** [HandSnapshot] 的網路 DTO。 */
@Serializable
data class HandSnapshotDto(
    val standingTiles: List<IdentifiedTileSnapshotDto>,
    val lastDrawn: IdentifiedTileSnapshotDto? = null,
)

fun HandSnapshot.toDto(): HandSnapshotDto = HandSnapshotDto(
    standingTiles = standingTiles.map { it.toDto() },
    lastDrawn = lastDrawn?.toDto(),
)

fun HandSnapshotDto.toDomain(): HandSnapshot = HandSnapshot(
    standingTiles = standingTiles.map { it.toDomain() },
    lastDrawn = lastDrawn?.toDomain(),
)
