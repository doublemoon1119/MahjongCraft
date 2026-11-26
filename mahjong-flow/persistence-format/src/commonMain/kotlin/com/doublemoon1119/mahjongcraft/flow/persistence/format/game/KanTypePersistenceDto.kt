package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlinx.serialization.Serializable

/** [GameAction.KanType] 的 persistence DTO。 */
@Serializable
enum class KanTypePersistenceDto {
    /** 明槓。 */
    OPEN_KAN,

    /** 暗槓。 */
    CLOSED_KAN,

    /** 加槓。 */
    ADDED_KAN,
}
