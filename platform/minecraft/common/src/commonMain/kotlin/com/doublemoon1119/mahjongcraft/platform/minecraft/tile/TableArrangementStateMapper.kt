package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 從對局中的權威桌況取出擺牌需要的部分。
 *
 * @return 擺牌用桌況；散放牌河的位置表種子依桌子與本場局序決定。
 */
fun TableState.toArrangementState(): TableArrangementState = TableArrangementState(
    players = players.map { player ->
        TableArrangementState.Player(
            id = player.id,
            standingTileIds = player.hand.tiles.map { it.id },
            drawnTileId = player.hand.lastDrawn?.id,
            melds = player.hand.melds.map { it.toPresentation(config.revealsClosedKanTiles).toTileGroup() },
            setAsideTiles = player.tileAreas.areas.flatMap { area -> area.tiles.map { TableArrangementState.SetAsideTile(it.id, area.id) } },
            discards = player.discardPile.entries.map { TableArrangementState.Discard(it.tile.id, it.isTaken) },
            sidewaysMarkedTileId = (player.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
        )
    },
    wallTileIds = tileWall.getAllTiles().map { it.id },
    reservedTileIds = reservedWallTiles.map { it.id },
    wallPlacements = physicalWallLayout?.placements,
    dealerIndex = dealerIndex,
    discardOrder = discardOrder,
    scatterPatternSeed = ScatteredDiscardLayout.roundPatternSeed(id, roundSerial),
)
