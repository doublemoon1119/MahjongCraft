package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import kotlin.uuid.Uuid

/**
 * [TileWall] 的對稱快照，用於 Client 端渲染
 */
data class TileWallSnapshot(
    val tiles: List<IdentifiedTileSnapshot>
)

/**
 * 產生一個 [TileWall] 的快照
 *
 * @return [TileWallSnapshot] 只會包含王牌區已經翻開的牌
 */
fun TileWall.toSnapshot(visibleTileIds: Set<Uuid>): TileWallSnapshot {
    return TileWallSnapshot(
        tiles = this.getAllTiles().map {
            it.toSnapshot(isVisible = it.id in visibleTileIds)
        }
    )
}