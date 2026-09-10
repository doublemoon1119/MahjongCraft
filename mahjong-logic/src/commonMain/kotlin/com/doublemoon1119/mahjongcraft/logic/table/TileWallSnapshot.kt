package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import kotlin.uuid.Uuid

/**
 * [TileWall] 的對稱快照，用於 Client 端渲染
 */
data class TileWallSnapshot(
    val tiles: List<IdentifiedTileSnapshot>,
)

/**
 * 產生一個 [TileWall] 的快照。
 *
 * [reservedWallTiles]（一般是 [TableState.reservedWallTiles]）也會併入快照——[TileWall] 本身只代表
 * 一般摸牌序列；若不把規則保留牌一起帶入，保留牌中的公開資訊便不會出現在 client 快照。
 *
 * @return 包含一般摸牌序列與規則保留牌的 observer-specific [TileWallSnapshot]。
 */
fun TileWall.toSnapshot(visibleTileIds: Set<Uuid>, reservedWallTiles: List<IdentifiedTile> = emptyList()): TileWallSnapshot = TileWallSnapshot(
    tiles = (this.getAllTiles() + reservedWallTiles).map {
        it.toSnapshot(isVisible = it.id in visibleTileIds)
    },
)
