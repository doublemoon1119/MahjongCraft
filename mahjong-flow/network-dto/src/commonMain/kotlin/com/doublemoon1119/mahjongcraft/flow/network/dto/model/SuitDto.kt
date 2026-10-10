package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.Serializable

/** [Tile.Suit] 的網路 DTO。 */
@Serializable
enum class SuitDto { CHARACTER, DOT, BAMBOO }
