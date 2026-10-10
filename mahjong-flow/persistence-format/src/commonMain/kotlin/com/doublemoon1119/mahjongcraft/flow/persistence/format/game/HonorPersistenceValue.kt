package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.Serializable

/** [Tile.Honor] 種類的 persistence 值。 */
@Serializable
enum class HonorPersistenceValue { EAST, SOUTH, WEST, NORTH, RED, GREEN, WHITE }
