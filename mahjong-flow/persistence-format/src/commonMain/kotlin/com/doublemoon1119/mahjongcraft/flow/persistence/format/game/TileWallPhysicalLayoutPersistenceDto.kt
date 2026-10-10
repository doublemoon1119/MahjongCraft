package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 目前仍位於牌牆中的牌張位置 persistence DTO。
 *
 * @property placements 以牌張 UUID 字串索引的牌牆位置。
 */
@Serializable
data class TileWallPhysicalLayoutPersistenceDto(
    val placements: Map<String, TileWallPlacementPersistenceDto>,
)
