package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 一張牌之權威實體牌牆位置 persistence DTO。 */
@Serializable
data class TileWallPlacementPersistenceDto(
    val side: Int,
    val stack: Int,
    val layer: Int,
    val alongWallStacks: Double,
    val towardTableCenterTiles: Double,
    val upwardLayers: Double,
    val orientation: TileWallPlacementOrientationPersistenceDto,
)

/** 實體牌牆位置的離散朝向 persistence DTO。 */
@Serializable
enum class TileWallPlacementOrientationPersistenceDto {
    /** 保持基本牌牆格位朝向。 */
    DEFAULT,

    /** 順時針旋轉九十度。 */
    CLOCKWISE_90,

    /** 旋轉一百八十度。 */
    HALF_TURN,

    /** 逆時針旋轉九十度。 */
    COUNTERCLOCKWISE_90,
}

/** 目前仍位於牌牆中的牌張位置 persistence DTO。 */
@Serializable
data class TileWallPhysicalLayoutPersistenceDto(
    val placements: Map<String, TileWallPlacementPersistenceDto>,
)

/** 將權威實體牌牆布局轉為 persistence DTO。 */
fun TileWallPhysicalLayout.toPersistenceDto(): TileWallPhysicalLayoutPersistenceDto = TileWallPhysicalLayoutPersistenceDto(
    placements = placements.mapKeys { (tileId, _) -> tileId.toString() }.mapValues { (_, placement) ->
        placement.toPersistenceDto()
    },
)

/** 將實體牌牆布局 persistence DTO 還原為領域模型。 */
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
