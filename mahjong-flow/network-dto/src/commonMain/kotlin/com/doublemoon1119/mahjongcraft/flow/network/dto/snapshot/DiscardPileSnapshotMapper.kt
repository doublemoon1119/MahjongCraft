package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.table.DiscardEntrySnapshot
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPileSnapshot
import kotlin.uuid.Uuid

/**
 * 將已完成可見性裁切的牌河轉成網路資料。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 不含權威組件的網路快照。
 */
fun DiscardPileSnapshot.toDto(registries: NetworkDtoRegistries): DiscardPileSnapshotDto = DiscardPileSnapshotDto(
    entries = entries.map { DiscardEntrySnapshotDto(it.tile.toDto(registries), it.isTaken, it.publicMarkerIds.toSet()) },
    sidewaysTileId = sidewaysTileId?.toString(),
)

/**
 * 還原可供呈現的牌河，不建立權威組件或規則牌河。
 *
 * @param registries 已登記的公開特徵格式。
 * @return 已裁切的牌河快照。
 */
fun DiscardPileSnapshotDto.toDomain(registries: NetworkDtoRegistries): DiscardPileSnapshot = DiscardPileSnapshot(
    entries = entries.map { DiscardEntrySnapshot(it.tile.toDomain(registries), it.isTaken, it.publicMarkerIds.toSet()) },
    sidewaysTileId = sidewaysTileId?.let(Uuid::parse),
)
