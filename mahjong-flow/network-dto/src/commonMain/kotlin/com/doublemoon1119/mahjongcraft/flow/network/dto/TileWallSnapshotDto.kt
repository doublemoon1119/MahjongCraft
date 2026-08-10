package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.table.TileWallSnapshot
import kotlinx.serialization.Serializable

/** [TileWallSnapshot] 的 observer-specific 網路 DTO。 */
@Serializable
data class TileWallSnapshotDto(val tiles: List<IdentifiedTileSnapshotDto>)

fun TileWallSnapshot.toDto(): TileWallSnapshotDto = TileWallSnapshotDto(tiles.map { it.toDto() })
fun TileWallSnapshotDto.toDomain(): TileWallSnapshot = TileWallSnapshot(tiles.map { it.toDomain() })
