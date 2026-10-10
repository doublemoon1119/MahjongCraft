package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/** 本局莊家推進決策的持久化類型。 */
@Serializable
enum class RoundTransitionDirectivePersistenceDto {
    /** 莊家連莊。 */
    REPEAT_DEALER,

    /** 莊家過莊。 */
    ADVANCE_DEALER,
}
