package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.uuid.Uuid

/**
 * [IdentifiedTile] 的對稱快照，用於 Client 端渲染
 */
data class IdentifiedTileSnapshot(
    val id: Uuid,
    val tile: Tile?,
)

/**
 * 產生一個 [IdentifiedTile] 的快照
 */
fun IdentifiedTile.toSnapshot(isVisible: Boolean): IdentifiedTileSnapshot = IdentifiedTileSnapshot(
    id = this.id,
    tile = this.tile.takeIf { isVisible },
)
