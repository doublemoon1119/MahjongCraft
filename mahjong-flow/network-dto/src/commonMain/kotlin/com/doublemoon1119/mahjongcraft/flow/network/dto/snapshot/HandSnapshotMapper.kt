package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 已登記的公開組件格式。
 * @return 轉換後的資料。
 */
fun HandSnapshot.toDto(registries: NetworkDtoRegistries): HandSnapshotDto = HandSnapshotDto(
    standingTiles = standingTiles.map { it.toDto(registries) },
    lastDrawn = lastDrawn?.toDto(registries),
    melds = melds.map { it.toDto(registries) },
)

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @param registries 已登記的公開組件格式。
 * @return 轉換後的資料。
 */
fun HandSnapshotDto.toDomain(registries: NetworkDtoRegistries): HandSnapshot = HandSnapshot(
    standingTiles = standingTiles.map { it.toDomain(registries) },
    lastDrawn = lastDrawn?.toDomain(registries),
    melds = melds.map { it.toDomain(registries) },
)
