package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import com.doublemoon1119.mahjongcraft.logic.table.TileWallSnapshot
import kotlinx.serialization.Serializable

/**
 * [TileWallSnapshot] 的 observer-specific 網路 DTO。
 *
 * @property tiles 按牌牆順序排列的牌快照。
 */
@Serializable
data class TileWallSnapshotDto(
    val tiles: List<IdentifiedTileSnapshotDto>,
)
