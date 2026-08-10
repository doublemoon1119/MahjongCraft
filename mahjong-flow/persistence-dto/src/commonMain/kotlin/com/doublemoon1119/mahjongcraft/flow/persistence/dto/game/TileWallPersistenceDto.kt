package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import kotlinx.serialization.Serializable

/** [TileWall] 的完整 persistence DTO，依原順序保存所有尚未摸取的牌。 */
@Serializable
data class TileWallPersistenceDto(val tiles: List<IdentifiedTilePersistenceDto>)

/** 將 [TileWall] 轉換成 persistence DTO。 */
fun TileWall.toPersistenceDto(): TileWallPersistenceDto = TileWallPersistenceDto(getAllTiles().map { it.toPersistenceDto() })

/** 將 [TileWallPersistenceDto] 還原成 [TileWall]。 */
fun TileWallPersistenceDto.toDomain(): TileWall = TileWall(tiles.map { it.toDomain() })
