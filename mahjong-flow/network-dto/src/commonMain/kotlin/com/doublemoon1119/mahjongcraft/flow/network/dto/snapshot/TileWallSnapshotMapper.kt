package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.NetworkDtoConverter
import com.doublemoon1119.mahjongcraft.logic.table.TileWallSnapshot

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 轉換後的資料。
 */
fun TileWallSnapshot.toDto(registries: NetworkDtoConverter): TileWallSnapshotDto = TileWallSnapshotDto(tiles.map { it.toDto(registries) })

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 轉換後的資料。
 */
fun TileWallSnapshotDto.toDomain(registries: NetworkDtoConverter): TileWallSnapshot = TileWallSnapshot(tiles.map { it.toDomain(registries) })
