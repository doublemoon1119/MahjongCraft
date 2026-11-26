package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/** 權威局位階段的 persistence DTO。 */
@Serializable
enum class MatchRoundPhasePersistenceDto {
    /** 原定賽程。 */
    REGULAR,

    /** 規則追加的延長賽程。 */
    EXTRA,
}
