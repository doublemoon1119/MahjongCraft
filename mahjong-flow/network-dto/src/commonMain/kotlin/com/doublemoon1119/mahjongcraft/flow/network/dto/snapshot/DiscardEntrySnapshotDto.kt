package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import kotlinx.serialization.Serializable

/**
 * 已裁切的捨牌網路資料，所有規則共用同一種格式。
 *
 * @property tile 依觀察者權限裁切的牌張。
 * @property isTaken 是否已被鳴走。
 * @property publicMarkerIds 明確公開的紀錄標記，不攜帶組件原值。
 */
@Serializable
data class DiscardEntrySnapshotDto(
    val tile: IdentifiedTileSnapshotDto,
    val isTaken: Boolean,
    val publicMarkerIds: Set<String>,
)
