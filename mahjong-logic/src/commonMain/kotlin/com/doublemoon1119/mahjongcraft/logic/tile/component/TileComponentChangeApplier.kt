package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.applyPatch
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall

/**
 * 在當前持有位置套用一張牌的組件變更，舊桌況與歷史參照保持不變。
 *
 * 呼叫端須在正式權威交易內驗證動作與執行者；本函式不提供外部請求授權。
 *
 * @param change 目標牌張與帶預期舊值的組件變更。
 * @param registry 本次執行的組件定義。
 * @return 已驗證的新桌況。
 */
fun TableState.applyTileComponentChange(
    change: TileComponentChange,
    registry: ComponentRegistry,
): TableState {
    val state = this
    val before = state.resolveTileAreas()
    val area = requireNotNull(before.singleOrNull { it.tile.id == change.tileId }) { "Tile is not in a current area" }
    val replacement = area.tile.copy(components = registry.applyPatch(area.tile.components, change.patch, BuiltInComponentOwners.TILE))
    registry.validate(replacement.components)
    val result = state.copy(
        tileWall = TileWall(state.tileWall.getAllTiles().map { if (it.id == change.tileId) replacement else it }),
        reservedWallTiles = state.reservedWallTiles.map { if (it.id == change.tileId) replacement else it },
        players = state.players.map { player ->
            val hand = player.hand.copy(
                tiles = player.hand.tiles.map { if (it.id == change.tileId) replacement else it },
                lastDrawn = player.hand.lastDrawn?.let { if (it.id == change.tileId) replacement else it },
                melds = player.hand.melds.map { meld ->
                    meld.copy(
                        tiles = meld.tiles.map { if (it.id == change.tileId) replacement else it },
                        sourceTile = meld.sourceTile?.let { if (it.id == change.tileId) replacement else it },
                    )
                },
            )
            val updated = player.copy(
                hand = hand,
                discardPile = if (area.region == TileRegion.DISCARD && area.ownerId == player.id) player.discardPile.replaceTile(replacement) else player.discardPile,
                tileAreas = if (area.region == TileRegion.SET_ASIDE && area.ownerId == player.id) player.tileAreas.replaceTile(change.tileId, replacement) else player.tileAreas,
            )
            updated
        },
    )
    val expected = before.map { if (it.tile.id == change.tileId) it.copy(tile = replacement) else it }
    require(result.resolveTileAreas() == expected) { "Tile component replacement changed another tile or area" }
    return result
}
