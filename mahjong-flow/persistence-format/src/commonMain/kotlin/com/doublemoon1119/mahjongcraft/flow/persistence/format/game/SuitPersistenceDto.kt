package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.Serializable

/** [Tile.Suit] 的 persistence DTO。 */
@Serializable
enum class SuitPersistenceDto { CHARACTER, DOT, BAMBOO }
