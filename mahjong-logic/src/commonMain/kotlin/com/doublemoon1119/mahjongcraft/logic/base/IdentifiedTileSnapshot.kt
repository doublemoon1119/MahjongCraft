package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.uuid.Uuid

/**
 * [IdentifiedTile] 對指定觀察者的可見快照。
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
