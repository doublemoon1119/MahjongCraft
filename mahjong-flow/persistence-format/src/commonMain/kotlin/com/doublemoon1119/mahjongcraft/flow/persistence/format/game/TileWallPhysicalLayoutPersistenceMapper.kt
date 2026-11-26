package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/**
 * 將權威實體牌牆布局轉為 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun TileWallPhysicalLayout.toPersistenceDto(): TileWallPhysicalLayoutPersistenceDto = TileWallPhysicalLayoutPersistenceDto(
    placements = placements.mapKeys { (tileId, _) -> tileId.toString() }.mapValues { (_, placement) ->
        placement.toPersistenceDto()
    },
)

/**
 * 將實體牌牆布局 persistence DTO 還原為領域模型。
 *
 * @return 轉換後的結果。
 */
fun TileWallPhysicalLayoutPersistenceDto.toDomain(): TileWallPhysicalLayout = TileWallPhysicalLayout(
    placements = placements.mapKeys { (tileId, _) -> Uuid.parse(tileId) }.mapValues { (_, placement) ->
        placement.toDomain()
    },
)

/** 將單張牌的位置轉為 persistence DTO。 */
private fun TileWallPlacement.toPersistenceDto(): TileWallPlacementPersistenceDto = TileWallPlacementPersistenceDto(
    side = position.side,
    stack = position.stack,
    layer = position.layer,
    alongWallStacks = offset.alongWallStacks,
    towardTableCenterTiles = offset.towardTableCenterTiles,
    upwardLayers = offset.upwardLayers,
    orientation = TileWallPlacementOrientationPersistenceDto.valueOf(orientation.name),
)

/** 將單張牌的位置 persistence DTO 還原為領域模型。 */
private fun TileWallPlacementPersistenceDto.toDomain(): TileWallPlacement = TileWallPlacement(
    position = TileWallPosition(side, stack, layer),
    offset = TileWallPlacementOffset(alongWallStacks, towardTableCenterTiles, upwardLayers),
    orientation = TileWallPlacementOrientation.valueOf(orientation.name),
)
