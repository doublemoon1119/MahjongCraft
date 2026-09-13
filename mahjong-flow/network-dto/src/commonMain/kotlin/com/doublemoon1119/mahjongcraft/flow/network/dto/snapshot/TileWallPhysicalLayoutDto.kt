package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 一張牌之權威實體牌牆位置網路 DTO。 */
@Serializable
data class TileWallPlacementDto(
    val side: Int,
    val stack: Int,
    val layer: Int,
    val alongWallStacks: Double,
    val towardTableCenterTiles: Double,
    val upwardLayers: Double,
    val orientation: TileWallPlacementOrientationDto,
)

/** 實體牌牆位置的離散朝向網路 DTO。 */
@Serializable
enum class TileWallPlacementOrientationDto {
    /** 保持基本牌牆格位朝向。 */
    DEFAULT,

    /** 順時針旋轉九十度。 */
    CLOCKWISE_90,

    /** 旋轉一百八十度。 */
    HALF_TURN,

    /** 逆時針旋轉九十度。 */
    COUNTERCLOCKWISE_90,
}

/** 目前仍位於牌牆中的牌張實體位置網路 DTO。 */
@Serializable
data class TileWallPhysicalLayoutDto(val placements: Map<String, TileWallPlacementDto>)

/** 將權威實體牌牆布局轉為網路 DTO。 */
fun TileWallPhysicalLayout.toDto(): TileWallPhysicalLayoutDto = TileWallPhysicalLayoutDto(
    placements = placements.mapKeys { (tileId, _) -> tileId.toString() }.mapValues { (_, placement) ->
        placement.toDto()
    },
)

/** 將實體牌牆布局網路 DTO 還原為領域模型。 */
fun TileWallPhysicalLayoutDto.toDomain(): TileWallPhysicalLayout = TileWallPhysicalLayout(
    placements = placements.mapKeys { (tileId, _) -> Uuid.parse(tileId) }.mapValues { (_, placement) ->
        placement.toDomain()
    },
)

/** 將單張牌的位置轉為網路 DTO。 */
private fun TileWallPlacement.toDto(): TileWallPlacementDto = TileWallPlacementDto(
    side = position.side,
    stack = position.stack,
    layer = position.layer,
    alongWallStacks = offset.alongWallStacks,
    towardTableCenterTiles = offset.towardTableCenterTiles,
    upwardLayers = offset.upwardLayers,
    orientation = TileWallPlacementOrientationDto.valueOf(orientation.name),
)

/** 將單張牌的位置網路 DTO 還原為領域模型。 */
private fun TileWallPlacementDto.toDomain(): TileWallPlacement = TileWallPlacement(
    position = TileWallPosition(side, stack, layer),
    offset = TileWallPlacementOffset(alongWallStacks, towardTableCenterTiles, upwardLayers),
    orientation = TileWallPlacementOrientation.valueOf(orientation.name),
)
