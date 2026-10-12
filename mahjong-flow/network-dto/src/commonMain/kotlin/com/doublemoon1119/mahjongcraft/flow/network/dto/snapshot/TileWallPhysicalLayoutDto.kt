package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/**
 * 目前仍位於牌牆中的牌張實體位置網路 DTO。
 *
 * @property placements 以牌張 UUID 字串索引的實體位置。
 */
@Serializable
data class TileWallPhysicalLayoutDto(val placements: Map<String, TileWallPlacementDto>)
