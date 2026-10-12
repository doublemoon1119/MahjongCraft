package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import kotlinx.serialization.Serializable

/** [RiichiExhaustiveDrawReason] 的網路 DTO。 */
@Serializable
sealed interface RiichiExhaustiveDrawReasonDto : ExhaustiveDrawReasonDto {
    /** 牌山摸盡且無勝負的一般流局。 */
    @Serializable data object Normal : RiichiExhaustiveDrawReasonDto

    /** 第一巡摸牌後，手牌有九種以上么九牌時可宣告的九種九牌。 */
    @Serializable data object KyuushuKyuuhai : RiichiExhaustiveDrawReasonDto

    /** 第一巡中四位玩家依序打出相同風牌的四風連打。 */
    @Serializable data object SuufonRenda : RiichiExhaustiveDrawReasonDto

    /** 全場累計四次槓牌，且並非由同一玩家全數完成時發生的四槓散了。 */
    @Serializable data object SuukanNagare : RiichiExhaustiveDrawReasonDto

    /** 四位玩家都已宣告立直的四家立直。 */
    @Serializable data object SuuchaRiichi : RiichiExhaustiveDrawReasonDto

    /** 多家同時榮和且多響政策指定以流局處理的三家和了原因。 */
    @Serializable data object SanchaHou : RiichiExhaustiveDrawReasonDto
}
