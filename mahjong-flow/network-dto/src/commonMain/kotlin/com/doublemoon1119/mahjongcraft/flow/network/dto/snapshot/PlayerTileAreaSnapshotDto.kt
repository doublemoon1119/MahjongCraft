package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileSnapshotDto
import kotlinx.serialization.Serializable

/**
 * 已裁切移出牌區的網路格式。
 *
 * @property id 區域完整識別碼。
 * @property tiles 已按讀取者裁切的有序牌張。
 */
@Serializable
data class PlayerTileAreaSnapshotDto(
    val id: String,
    val tiles: List<IdentifiedTileSnapshotDto>,
)
