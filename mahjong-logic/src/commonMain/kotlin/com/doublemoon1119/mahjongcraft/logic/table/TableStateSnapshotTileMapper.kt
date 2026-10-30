package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot

/**
 * 轉換快照中所有區域的牌張，包含副露來源與被鳴走捨牌的參照。
 *
 * 同一張牌可能出現在多個參照中；[transform] 必須對相同輸入回傳相同結果。
 *
 * @param transform 依目前牌張快照產生新快照的純函式。
 * @return 套用轉換後的新桌況，不修改來源。
 */
fun TableStateSnapshot.mapTiles(transform: (IdentifiedTileSnapshot) -> IdentifiedTileSnapshot): TableStateSnapshot = copy(
    tileWall = tileWall.copy(tiles = tileWall.tiles.map(transform)),
    players = players.map { player ->
        player.copy(
            tileAreas = player.tileAreas.map { area -> area.copy(tiles = area.tiles.map(transform)) },
            discardPile = player.discardPile.copy(entries = player.discardPile.entries.map { entry -> entry.copy(tile = transform(entry.tile)) }),
            hand = player.hand.copy(
                melds = player.hand.melds.map { meld ->
                    meld.copy(
                        tiles = meld.tiles.map(transform),
                        sourceTile = meld.sourceTile?.let(transform),
                    )
                },
                standingTiles = player.hand.standingTiles.map(transform),
                lastDrawn = player.hand.lastDrawn?.let(transform),
            ),
        )
    },
)
