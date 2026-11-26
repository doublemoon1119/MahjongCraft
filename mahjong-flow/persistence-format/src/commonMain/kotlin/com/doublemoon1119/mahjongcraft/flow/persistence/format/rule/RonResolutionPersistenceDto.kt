package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import kotlinx.serialization.Serializable

/** [RonResolution] 的 persistence DTO。 */
@Serializable
enum class RonResolutionPersistenceDto {
    /** 僅最近的玩家榮和。 */
    NEAREST_WINNER,

    /** 所有符合資格的玩家榮和。 */
    ALL_WINNERS,

    /** 判定途中流局。 */
    ABORTIVE_DRAW,
}
