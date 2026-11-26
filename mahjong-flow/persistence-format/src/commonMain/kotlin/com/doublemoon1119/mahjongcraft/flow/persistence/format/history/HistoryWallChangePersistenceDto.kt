package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import kotlinx.serialization.Serializable

/**
 * 活牌牆兩端移除與牌張組件的新版本。
 *
 * @property updatedTiles 留下牌張的完整新版本，不含被移除牌張。
 * @property removedFront 從摸牌端移除的張數。
 * @property removedBack 從另一端移除的張數。
 */
@Serializable
data class HistoryWallChangePersistenceDto(
    val removedFront: Int,
    val removedBack: Int,
    val updatedTiles: List<IdentifiedTilePersistenceDto>,
)
