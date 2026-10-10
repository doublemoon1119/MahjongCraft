package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlinx.serialization.Serializable

/** [RelativeDirection] 的 persistence DTO。 */
@Serializable
enum class RelativeDirectionPersistenceDto { LEFT, ACROSS, RIGHT, SELF }
