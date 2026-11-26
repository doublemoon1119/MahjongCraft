package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 牌牆的 persistence DTO。
 *
 * @property tiles 依牌牆順序排列的實體牌。
 */
@Serializable
data class TileWallPersistenceDto(val tiles: List<IdentifiedTilePersistenceDto>)
