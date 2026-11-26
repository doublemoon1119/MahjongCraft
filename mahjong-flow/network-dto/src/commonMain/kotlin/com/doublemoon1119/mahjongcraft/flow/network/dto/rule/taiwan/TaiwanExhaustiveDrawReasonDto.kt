package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import kotlinx.serialization.Serializable

/** [TaiwanExhaustiveDrawReason] 的網路 DTO。 */
@Serializable
sealed interface TaiwanExhaustiveDrawReasonDto : ExhaustiveDrawReasonDto {
    /** 牌牆摸盡且無人和牌的一般流局。 */
    @Serializable data object Normal : TaiwanExhaustiveDrawReasonDto

    /** 公開花牌後沒有合法補牌可取的流局。 */
    @Serializable data object NoSupplementalTile : TaiwanExhaustiveDrawReasonDto

    /** 全桌合計完成四個槓後發生的四槓流局。 */
    @Serializable data object FourKans : TaiwanExhaustiveDrawReasonDto

    /** 第一巡四位玩家依序打出相同風牌的四風連打。 */
    @Serializable data object FourWinds : TaiwanExhaustiveDrawReasonDto
}
