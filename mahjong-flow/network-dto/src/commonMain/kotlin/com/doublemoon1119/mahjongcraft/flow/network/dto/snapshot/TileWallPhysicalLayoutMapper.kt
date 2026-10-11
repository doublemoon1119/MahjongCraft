package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun TileWallPhysicalLayout.toDto(): TileWallPhysicalLayoutDto = TileWallPhysicalLayoutDto(
    placements = placements.mapKeys { (tileId, _) -> tileId.toString() }.mapValues { (_, placement) ->
        placement.toDto()
    },
)

/**
 * 將實體牌牆布局網路 DTO 還原為領域模型。
 *
 * @return 轉換後的資料。
 */
fun TileWallPhysicalLayoutDto.toDomain(): TileWallPhysicalLayout = TileWallPhysicalLayout(
    placements = placements.mapKeys { (tileId, _) -> Uuid.parse(tileId) }.mapValues { (_, placement) ->
        placement.toDomain()
    },
)

/**
 * 將單張牌的位置轉為網路 DTO。
 *
 * @return 轉換後的資料。
 */
private fun TileWallPlacement.toDto(): TileWallPlacementDto = TileWallPlacementDto(
    side = position.side,
    stack = position.stack,
    layer = position.layer,
    alongWallStacks = offset.alongWallStacks,
    towardTableCenterTiles = offset.towardTableCenterTiles,
    upwardLayers = offset.upwardLayers,
    orientation = TileWallPlacementOrientationDto.valueOf(orientation.name),
)

/**
 * 將單張牌的位置網路 DTO 還原為領域模型。
 *
 * @return 轉換後的資料。
 */
private fun TileWallPlacementDto.toDomain(): TileWallPlacement = TileWallPlacement(
    position = TileWallPosition(side, stack, layer),
    offset = TileWallPlacementOffset(alongWallStacks, towardTableCenterTiles, upwardLayers),
    orientation = TileWallPlacementOrientation.valueOf(orientation.name),
)
