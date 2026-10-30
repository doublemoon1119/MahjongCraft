package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 列出各區域的當前牌張，檢查位置重複與副露來源參照。
 *
 * @return 依區域及規則原有牌序排列的當前牌張。
 */
fun TableState.resolveTileAreas(): List<TileAreaEntry> {
    val tableState = this
    val result = buildList {
        tableState.tileWall.getAllTiles().forEach { add(TileAreaEntry(it, TileRegion.WALL, null)) }
        tableState.reservedWallTiles.forEach { add(TileAreaEntry(it, TileRegion.RESERVED, null)) }
        tableState.players.forEach { player ->
            player.hand.tiles.forEach { add(TileAreaEntry(it, TileRegion.HAND, player.id)) }
            player.hand.lastDrawn?.let { add(TileAreaEntry(it, TileRegion.DRAWN, player.id)) }
            player.hand.melds.forEach { meld ->
                require(meld.sourceTile == null || meld.tiles.any { it == meld.sourceTile }) { "Meld source does not match its current tile" }
                meld.tiles.forEach { add(TileAreaEntry(it, TileRegion.MELD, player.id)) }
            }
            player.discardPile.entries.filterNot { it.isTaken }.forEach { add(TileAreaEntry(it.tile, TileRegion.DISCARD, player.id)) }
            player.tileAreas.areas.forEach { area ->
                area.tiles.forEach { add(TileAreaEntry(it, TileRegion.SET_ASIDE, player.id, area.id)) }
            }
        }
    }
    require(result.map { it.tile.id }.distinct().size == result.size) { "A current tile occupies more than one area" }
    val current = result.associateBy { it.tile.id }
    tableState.players.flatMap { it.discardPile.entries }.filter { it.isTaken }.forEach { entry ->
        require(current[entry.tile.id]?.tile?.let { it.tile == entry.tile.tile } != false) { "Historical tile face conflicts with the current tile" }
    }
    return result
}
