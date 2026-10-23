package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/** 牌譜保存的下一任莊家，玩家以牌譜的座位索引表示。 */
sealed interface HistoryTransitionDirective {
    /** 保存的類型 ID，與存檔格式使用的名稱相同。 */
    val typeId: String

    /** 莊家連莊。 */
    data object RepeatDealer : HistoryTransitionDirective {
        override val typeId: String = REPEAT_DEALER_TYPE_ID
    }

    /** 交給座位順序中莊家的下一位。 */
    data object PassToNextSeat : HistoryTransitionDirective {
        override val typeId: String = PASS_TO_NEXT_SEAT_TYPE_ID
    }

    /**
     * 交給規則指定的玩家。
     *
     * @property nextDealerSeat 下一任莊家的牌譜座位索引。
     */
    data class PassTo(val nextDealerSeat: Int) : HistoryTransitionDirective {
        override val typeId: String = PASS_TO_TYPE_ID
    }

    /** 各類型保存時使用的 ID。 */
    companion object {
        /** [RepeatDealer] 的類型 ID。 */
        const val REPEAT_DEALER_TYPE_ID: String = "repeat_dealer"

        /** [PassToNextSeat] 的類型 ID。 */
        const val PASS_TO_NEXT_SEAT_TYPE_ID: String = "pass_to_next_seat"

        /** [PassTo] 的類型 ID。 */
        const val PASS_TO_TYPE_ID: String = "pass_to"
    }
}
