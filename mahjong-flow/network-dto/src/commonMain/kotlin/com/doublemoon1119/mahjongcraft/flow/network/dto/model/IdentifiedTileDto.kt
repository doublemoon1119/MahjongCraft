package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import kotlinx.serialization.Serializable

/**
 * [IdentifiedTile] 的網路 DTO，包含不向 observer 隱藏的牌面。
 *
 * @property id 牌的穩定 UUID 字串。
 * @property tile 對 observer 公開的牌面 DTO。
 */
@Serializable
data class IdentifiedTileDto(
    val id: String,
    val tile: TileDto,
)
