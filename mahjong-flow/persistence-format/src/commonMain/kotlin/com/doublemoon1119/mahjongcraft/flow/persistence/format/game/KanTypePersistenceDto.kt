package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlinx.serialization.Serializable

/** [GameAction.KanType] 的 persistence DTO。 */
@Serializable
enum class KanTypePersistenceDto { OPEN_KAN, CLOSED_KAN, ADDED_KAN }
