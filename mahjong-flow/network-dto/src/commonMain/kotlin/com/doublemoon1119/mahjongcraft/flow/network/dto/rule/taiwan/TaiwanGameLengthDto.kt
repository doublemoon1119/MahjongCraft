package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import kotlinx.serialization.Serializable

/** [TaiwanGameLength] 的網路 DTO。 */
@Serializable
sealed interface TaiwanGameLengthDto : GameLengthDto {
    @Serializable data object OneGame : TaiwanGameLengthDto

    @Serializable data object East : TaiwanGameLengthDto

    @Serializable data object TwoWinds : TaiwanGameLengthDto

    @Serializable data object FourWinds : TaiwanGameLengthDto
}
