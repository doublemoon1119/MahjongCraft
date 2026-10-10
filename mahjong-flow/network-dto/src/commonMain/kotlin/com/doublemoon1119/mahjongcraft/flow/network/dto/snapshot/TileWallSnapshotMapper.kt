package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.table.TileWallSnapshot

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun TileWallSnapshot.toDto(): TileWallSnapshotDto = TileWallSnapshotDto(tiles.map { it.toDto() })

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun TileWallSnapshotDto.toDomain(): TileWallSnapshot = TileWallSnapshot(tiles.map { it.toDomain() })
