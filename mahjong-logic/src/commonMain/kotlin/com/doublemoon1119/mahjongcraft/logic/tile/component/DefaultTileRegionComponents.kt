package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 提供一般牌區的姿態與公開預設，規則可以明確替換某區預設。
 *
 * @param state 目前的權威桌況。
 * @param area 一張牌的目前區域。
 * @return 這個區域的標準組件。
 */
fun defaultTileRegionComponents(state: TableState, area: TileAreaEntry): ComponentSet {
    var public = area.region == TileRegion.DISCARD || area.region == TileRegion.SET_ASIDE
    val pose = when (area.region) {
        TileRegion.WALL, TileRegion.RESERVED -> TilePose.FACE_DOWN
        TileRegion.HAND, TileRegion.DRAWN -> TilePose.UPRIGHT
        TileRegion.DISCARD, TileRegion.SET_ASIDE -> TilePose.FACE_UP
        TileRegion.MELD -> {
            val meld = state.players.first { it.id == area.ownerId }.hand.melds.single { meld -> meld.tiles.any { it.id == area.tile.id } }
            val concealed = meld.type == MeldType.CLOSED_KAN
            public = if (concealed) state.config.revealsClosedKanTiles else area.tile.id in meld.publiclyRevealedTileIds
            if (concealed && !state.config.revealsClosedKanTiles || area.tile.id in meld.faceDownTileIds) TilePose.FACE_DOWN else TilePose.FACE_UP
        }
    }
    return ComponentSet.EMPTY
        .with(BuiltInTileComponents.POSE, pose)
        .with(BuiltInTileComponents.FACE_ACCESS, if (public) TileFaceAccess.Public else TileFaceAccess.None)
}
