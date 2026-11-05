package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

/** [TurnPhasePersistenceDto] 各階段的穩定保存名稱，保存格式與牌譜投影共用。 */
internal object TurnPhaseSerialNames {
    /** 等摸牌。 */
    const val AWAITING_DRAW = "awaiting_draw"

    /** 等行動。 */
    const val AWAITING_ACTION = "awaiting_action"

    /** 等打牌。 */
    const val AWAITING_DISCARD = "awaiting_discard"

    /** 等捨牌反應。 */
    const val AWAITING_DISCARD_REACTION = "awaiting_discard_reaction"

    /** 等搶和反應。 */
    const val AWAITING_ROBBING_REACTION = "awaiting_robbing_reaction"

    /** 本局已結束。 */
    const val ROUND_OVER = "round_over"
}
