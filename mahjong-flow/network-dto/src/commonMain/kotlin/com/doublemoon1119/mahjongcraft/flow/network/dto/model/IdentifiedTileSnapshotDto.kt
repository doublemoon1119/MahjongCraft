package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import kotlinx.serialization.Serializable

/**
 * [IdentifiedTileSnapshot] 的網路 DTO，允許依 observer 權限隱藏牌面。
 *
 * @property id 牌的穩定 UUID 字串。
 * @property tile 依 observer 權限公開或隱藏的牌面 DTO。
 */
@Serializable
data class IdentifiedTileSnapshotDto(
    val id: String,
    val tile: TileDto?,
)
