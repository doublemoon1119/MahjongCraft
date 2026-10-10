package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import kotlinx.serialization.Serializable

/** [RiichiGameLength] 的網路 DTO。 */
@Serializable
sealed interface RiichiGameLengthDto : GameLengthDto {
    @Serializable data object OneGame : RiichiGameLengthDto

    @Serializable data object East : RiichiGameLengthDto

    @Serializable data object TwoWinds : RiichiGameLengthDto
}
