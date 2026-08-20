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
 * [deadWallTiles]（一般是 [TableState.initialDeadWall]）也會併入快照——[TileWall] 本身只代表活牌堆，
 * 不含王牌（見 [TableState.tileWall] KDoc），若不把王牌一起帶進來，[visibleTileIds] 裡王牌指示牌的
 * id 就永遠不會出現在快照裡，client 端就無從得知目前公開的指示牌是什麼牌面。
 *
 * @return [TileWallSnapshot] 只會包含活牌堆的所有牌張，以及王牌區已經翻開的牌
 */
fun TileWall.toSnapshot(visibleTileIds: Set<Uuid>, deadWallTiles: List<IdentifiedTile> = emptyList()): TileWallSnapshot = TileWallSnapshot(
    tiles = (this.getAllTiles() + deadWallTiles).map {
        it.toSnapshot(isVisible = it.id in visibleTileIds)
    },
)
