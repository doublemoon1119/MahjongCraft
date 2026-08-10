package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class IdentifiedTileDto(val id: String, val tile: TileDto)

fun IdentifiedTile.toDto(): IdentifiedTileDto = IdentifiedTileDto(id.toString(), tile.toDto())
fun IdentifiedTileDto.toDomain(): IdentifiedTile = IdentifiedTile(Uuid.parse(id), tile.toDomain())

@Serializable
data class IdentifiedTileSnapshotDto(val id: String, val tile: TileDto?)

fun IdentifiedTileSnapshot.toDto(): IdentifiedTileSnapshotDto = IdentifiedTileSnapshotDto(id.toString(), tile?.toDto())
fun IdentifiedTileSnapshotDto.toDomain(): IdentifiedTileSnapshot = IdentifiedTileSnapshot(Uuid.parse(id), tile?.toDomain())
