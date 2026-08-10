package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** [IdentifiedTile] 的網路 DTO，包含不向 observer 隱藏的牌面。 */
@Serializable
data class IdentifiedTileDto(val id: String, val tile: TileDto)

fun IdentifiedTile.toDto(): IdentifiedTileDto = IdentifiedTileDto(id.toString(), tile.toDto())
fun IdentifiedTileDto.toDomain(): IdentifiedTile = IdentifiedTile(Uuid.parse(id), tile.toDomain())

/** [IdentifiedTileSnapshot] 的網路 DTO，允許依 observer 權限隱藏牌面。 */
@Serializable
data class IdentifiedTileSnapshotDto(val id: String, val tile: TileDto?)

fun IdentifiedTileSnapshot.toDto(): IdentifiedTileSnapshotDto = IdentifiedTileSnapshotDto(id.toString(), tile?.toDto())
fun IdentifiedTileSnapshotDto.toDomain(): IdentifiedTileSnapshot = IdentifiedTileSnapshot(Uuid.parse(id), tile?.toDomain())
