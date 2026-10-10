package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable

/** [Wind] 的 persistence DTO。 */
@Serializable
enum class WindPersistenceDto { EAST, SOUTH, WEST, NORTH }
