package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import kotlinx.serialization.Serializable

/** [RonResolution] 的網路 DTO。 */
@Serializable
enum class RonResolutionDto {
    /** 頭跳：僅由摸牌順位離放銃者最近的玩家胡牌。 */
    NEAREST_WINNER,

    /** 多家和：所有符合資格的玩家皆各自胡牌。 */
    ALL_WINNERS,

    /** 途中流局：本局流局且不結算這些榮和。 */
    ABORTIVE_DRAW,
}
