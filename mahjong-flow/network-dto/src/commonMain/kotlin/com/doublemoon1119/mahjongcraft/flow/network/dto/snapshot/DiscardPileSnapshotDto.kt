package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/**
 * 規則中立的牌河網路快照。
 *
 * @property entries 依捨牌順序排列的紀錄。
 * @property sidewaysTileId 需要側放的牌 UUID；沒有時為 null。
 */
@Serializable
data class DiscardPileSnapshotDto(
    val entries: List<DiscardEntrySnapshotDto>,
    val sidewaysTileId: String?,
)
