package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable

/** [Wind] 的網路 DTO。 */
@Serializable
enum class WindDto { EAST, SOUTH, WEST, NORTH }
