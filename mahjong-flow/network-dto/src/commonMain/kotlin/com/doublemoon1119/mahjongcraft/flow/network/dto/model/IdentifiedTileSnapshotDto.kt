package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import kotlinx.serialization.Serializable

/**
 * [IdentifiedTileSnapshot] 的網路 DTO，允許依 observer 權限隱藏牌面。
 *
 * @property id 牌的穩定 UUID 字串。
 * @property face 已裁切的牌面與特徵；未知牌為 null。
 */
@Serializable
data class IdentifiedTileSnapshotDto(
    val id: String,
    val face: TileFaceViewDto?,
)
