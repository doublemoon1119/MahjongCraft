package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TileWall

/**
 * 將 [TileWall] 轉換成 persistence DTO。
 *
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 轉換後的結果。
 */
fun TileWall.toPersistenceDto(componentRegistry: ComponentPersistenceRegistry): TileWallPersistenceDto = TileWallPersistenceDto(getAllTiles().map { it.toPersistenceDto(componentRegistry) })

/**
 * 將 [TileWallPersistenceDto] 還原成 [TileWall]。
 *
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 轉換後的結果。
 */
fun TileWallPersistenceDto.toDomain(componentRegistry: ComponentPersistenceRegistry): TileWall = TileWall(tiles.map { it.toDomain(componentRegistry) })
