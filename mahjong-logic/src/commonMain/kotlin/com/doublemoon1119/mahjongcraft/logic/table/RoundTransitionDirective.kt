package com.doublemoon1119.mahjongcraft.logic.table

import kotlin.uuid.Uuid

/**
 * 本局結算後由規則決定的下一任莊家。
 *
 * 在本局結束時決定一次，隨本局摘要保存；之後的局位推進與讀檔都沿用，不重新判斷。
 */
sealed interface RoundTransitionDirective {
    /** 莊家連莊，下一局仍由同一位玩家坐莊。 */
    data object RepeatDealer : RoundTransitionDirective

    /** 莊家過莊，交給本局座位順序中莊家的下一位玩家。 */
    data object PassToNextSeat : RoundTransitionDirective

    /**
     * 莊家過莊，交給規則指定的玩家，例如由本局和牌的玩家坐莊。
     *
     * @property nextDealerPlayerId 下一任莊家；必須在本桌，且不是現任莊家，留任請用 [RepeatDealer]。
     */
    data class PassTo(val nextDealerPlayerId: Uuid) : RoundTransitionDirective
}
