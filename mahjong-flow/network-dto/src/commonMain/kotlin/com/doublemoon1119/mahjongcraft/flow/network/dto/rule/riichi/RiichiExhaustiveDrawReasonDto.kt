package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import kotlinx.serialization.Serializable

/** [RiichiExhaustiveDrawReason] 的網路 DTO。 */
@Serializable
sealed interface RiichiExhaustiveDrawReasonDto : ExhaustiveDrawReasonDto {
    @Serializable data object Normal : RiichiExhaustiveDrawReasonDto

    @Serializable data object KyuushuKyuuhai : RiichiExhaustiveDrawReasonDto

    @Serializable data object SuufonRenda : RiichiExhaustiveDrawReasonDto

    @Serializable data object SuukanNagare : RiichiExhaustiveDrawReasonDto

    @Serializable data object SuuchaRiichi : RiichiExhaustiveDrawReasonDto

    @Serializable data object SanchaHou : RiichiExhaustiveDrawReasonDto
}
