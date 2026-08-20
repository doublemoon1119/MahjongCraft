package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import kotlinx.serialization.Serializable

/** [HandSnapshot] 的網路 DTO。 */
@Serializable
data class HandSnapshotDto(
    val standingTiles: List<IdentifiedTileSnapshotDto>,
    val lastDrawn: IdentifiedTileSnapshotDto? = null,
    val melds: List<MeldSnapshotDto> = emptyList(),
)

fun HandSnapshot.toDto(): HandSnapshotDto = HandSnapshotDto(
    standingTiles = standingTiles.map { it.toDto() },
    lastDrawn = lastDrawn?.toDto(),
    melds = melds.map { it.toDto() },
)

fun HandSnapshotDto.toDomain(): HandSnapshot = HandSnapshot(
    standingTiles = standingTiles.map { it.toDomain() },
    lastDrawn = lastDrawn?.toDomain(),
    melds = melds.map { it.toDomain() },
)
