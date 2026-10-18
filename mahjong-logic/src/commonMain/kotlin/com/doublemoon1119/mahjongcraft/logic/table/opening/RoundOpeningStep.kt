package com.doublemoon1119.mahjongcraft.logic.table.opening

/** 開局程序的下一步。 */
sealed interface RoundOpeningStep {
    /**
     * 還需要由指定玩家擲骰。
     *
     * @property request 擲骰玩家與骰子數量要求。
     */
    data class Roll(val request: OpeningDiceRollRequest) : RoundOpeningStep

    /**
     * 已足以建立本局牌山與莊家。
     *
     * @property result 已選定的莊家與牌牆開門位置。
     */
    data class Complete(val result: RoundOpeningCompletion) : RoundOpeningStep
}
