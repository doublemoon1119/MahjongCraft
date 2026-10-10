package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import kotlinx.serialization.Serializable

/**
 * [HandSnapshot] 的網路 DTO。
 *
 * @property standingTiles 手牌區的牌張快照。
 * @property lastDrawn 最近摸入的牌；沒有時為 `null`。
 * @property melds 已完成的副露快照。
 */
@Serializable
data class HandSnapshotDto(
    val standingTiles: List<IdentifiedTileSnapshotDto>,
    val lastDrawn: IdentifiedTileSnapshotDto?,
    val melds: List<MeldSnapshotDto>,
)
