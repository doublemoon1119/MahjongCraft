package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileWallPlacementPersistenceDto
import kotlinx.serialization.Serializable

/**
 * 實體牌牆格位的局部變化。
 *
 * @property removedTileIds 離開牌牆的牌 UUID 字串。
 * @property updatedPlacements 位置或方向改變的牌 UUID 與新格位。
 */
@Serializable
data class HistoryWallLayoutChangePersistenceDto(
    val removedTileIds: Set<String>,
    val updatedPlacements: Map<String, TileWallPlacementPersistenceDto>,
)
