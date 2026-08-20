package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.base.MeldSnapshot
import kotlinx.serialization.Serializable

/** [MeldSnapshot] 的網路 DTO。 */
@Serializable
data class MeldSnapshotDto(
    val type: MeldTypeDto,
    val tiles: List<IdentifiedTileSnapshotDto>,
    val sourceTile: IdentifiedTileSnapshotDto?,
    val sourceDirection: RelativeDirectionDto,
)

fun MeldSnapshot.toDto(): MeldSnapshotDto = MeldSnapshotDto(
    type = type.toDto(),
    tiles = tiles.map { it.toDto() },
    sourceTile = sourceTile?.toDto(),
    sourceDirection = sourceDirection.toDto(),
)

fun MeldSnapshotDto.toDomain(): MeldSnapshot = MeldSnapshot(
    type = type.toDomain(),
    tiles = tiles.map { it.toDomain() },
    sourceTile = sourceTile?.toDomain(),
    sourceDirection = sourceDirection.toDomain(),
)
