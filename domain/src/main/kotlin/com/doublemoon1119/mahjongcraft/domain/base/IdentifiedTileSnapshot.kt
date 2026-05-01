package com.doublemoon1119.mahjongcraft.domain.base

import java.util.*

/**
 * [IdentifiedTile] 的對稱快照，用於 Client 端渲染
 */
data class IdentifiedTileSnapshot(
    val id: UUID,
    val tile: Tile?
)

/**
 * 產生一個 [IdentifiedTile] 的快照
 */
fun IdentifiedTile.toSnapshot(isVisible: Boolean): IdentifiedTileSnapshot {
    return IdentifiedTileSnapshot(
        id = this.id,
        tile = this.tile.takeIf { isVisible }
    )
}