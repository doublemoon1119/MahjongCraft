package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import kotlinx.serialization.Serializable

/** [RiichiGameLength] 的 persistence DTO。 */
@Serializable
enum class RiichiGameLengthPersistenceDto {
    /** 一局。 */
    ONE_GAME,

    /** 東風戰。 */
    EAST,

    /** 東南戰。 */
    TWO_WINDS,
}
