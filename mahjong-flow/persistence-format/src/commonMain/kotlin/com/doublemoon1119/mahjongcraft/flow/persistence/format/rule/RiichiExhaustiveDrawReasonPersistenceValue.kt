package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import kotlinx.serialization.Serializable

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
