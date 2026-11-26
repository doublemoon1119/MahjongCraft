package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import kotlinx.serialization.Serializable

/** [RiichiGameLength] 的網路 DTO。 */
@Serializable
sealed interface RiichiGameLengthDto : GameLengthDto {
    /** 一局，總計一局。 */
    @Serializable data object OneGame : RiichiGameLengthDto

    /** 東風戰，總計四局。 */
    @Serializable data object East : RiichiGameLengthDto

    /** 半莊（東南風），總計八局。 */
    @Serializable data object TwoWinds : RiichiGameLengthDto
}
