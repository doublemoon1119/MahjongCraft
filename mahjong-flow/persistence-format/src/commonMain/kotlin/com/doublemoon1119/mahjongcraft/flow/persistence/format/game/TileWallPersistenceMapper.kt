package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.TileWall

/**
 * 將 [TileWall] 轉換成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun TileWall.toPersistenceDto(): TileWallPersistenceDto = TileWallPersistenceDto(getAllTiles().map { it.toPersistenceDto() })

/**
 * 將 [TileWallPersistenceDto] 還原成 [TileWall]。
 *
 * @return 轉換後的結果。
 */
fun TileWallPersistenceDto.toDomain(): TileWall = TileWall(tiles.map { it.toDomain() })
