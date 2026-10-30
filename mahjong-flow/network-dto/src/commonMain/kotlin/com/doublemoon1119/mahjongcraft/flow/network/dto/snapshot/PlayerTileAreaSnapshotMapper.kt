package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreaSnapshot

/**
 * 將已裁切牌區轉成網路資料。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 保留區域身分與牌張順序的網路資料。
 */
fun PlayerTileAreaSnapshot.toDto(registries: NetworkDtoRegistries): PlayerTileAreaSnapshotDto = PlayerTileAreaSnapshotDto(
    id = id,
    tiles = tiles.map { it.toDto(registries) },
)

/**
 * 還原已裁切牌區，不建立完整權威牌張。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 保留區域身分與牌張順序的快照。
 */
fun PlayerTileAreaSnapshotDto.toDomain(registries: NetworkDtoRegistries): PlayerTileAreaSnapshot = PlayerTileAreaSnapshot(
    id = id,
    tiles = tiles.map { it.toDomain(registries) },
)
