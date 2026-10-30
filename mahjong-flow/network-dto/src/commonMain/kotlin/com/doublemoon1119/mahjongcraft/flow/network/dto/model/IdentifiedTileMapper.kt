package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun IdentifiedTile.toDto(): IdentifiedTileDto = IdentifiedTileDto(id.toString(), tile.toDto())

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun IdentifiedTileDto.toDomain(): IdentifiedTile = IdentifiedTile(Uuid.parse(id), tile.toDomain())

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 轉換後的資料。
 */
fun IdentifiedTileSnapshot.toDto(registries: NetworkDtoRegistries): IdentifiedTileSnapshotDto = IdentifiedTileSnapshotDto(id.toString(), face?.toDto(registries))

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 轉換後的資料。
 */
fun IdentifiedTileSnapshotDto.toDomain(registries: NetworkDtoRegistries): IdentifiedTileSnapshot = IdentifiedTileSnapshot(Uuid.parse(id), face?.toDomain(registries))
