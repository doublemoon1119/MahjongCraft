package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import kotlinx.serialization.Serializable

/** [TaiwanGameLength] 的網路 DTO。 */
@Serializable
sealed interface TaiwanGameLengthDto : GameLengthDto {
    /** 一局，總計一局。 */
    @Serializable data object OneGame : TaiwanGameLengthDto

    /** 東風戰，總計四局。 */
    @Serializable data object East : TaiwanGameLengthDto

    /** 東南風，總計八局。 */
    @Serializable data object TwoWinds : TaiwanGameLengthDto

    /** 東南西北風，總計十六局。 */
    @Serializable data object FourWinds : TaiwanGameLengthDto
}
