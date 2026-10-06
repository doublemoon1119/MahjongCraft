package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import kotlinx.serialization.Serializable

/** [RiichiExhaustiveDrawReason] 的 persistence DTO。 */
@Serializable
data class RiichiExhaustiveDrawReasonPersistenceDto(val value: RiichiExhaustiveDrawReasonPersistenceValue)

/** [RiichiExhaustiveDrawReason] 種類的 persistence 值。 */
@Serializable
enum class RiichiExhaustiveDrawReasonPersistenceValue {
    NORMAL,
    KYUUSHU_KYUUHAI,
    SUUFON_RENDA,
    SUUKAN_NAGARE,
    SUUCHA_RIICHI,
    SANCHA_HOU,
}
