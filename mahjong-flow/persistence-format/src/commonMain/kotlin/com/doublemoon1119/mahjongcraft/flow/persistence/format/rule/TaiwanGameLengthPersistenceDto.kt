package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import kotlinx.serialization.Serializable

/** [TaiwanGameLength] 的 persistence DTO。 */
@Serializable
enum class TaiwanGameLengthPersistenceDto {
    /** 一局。 */
    ONE_GAME,

    /** 東風戰。 */
    EAST,

    /** 東南戰。 */
    TWO_WINDS,

    /** 東南西北戰。 */
    FOUR_WINDS,
}
