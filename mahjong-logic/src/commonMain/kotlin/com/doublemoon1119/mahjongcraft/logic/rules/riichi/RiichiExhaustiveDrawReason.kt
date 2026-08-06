package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason

/**
 * 立直麻將和局原因。
 */
sealed class RiichiExhaustiveDrawReason : ExhaustiveDrawReason {

    /**
     * 一般流局。
     * 牌山摸盡且無勝負。
     */
    data object Normal : RiichiExhaustiveDrawReason()

    /**
     * 九種九牌。
     * 玩家在第一巡摸牌後，手牌中擁有 9 種（含）以上不同的「么九牌」（1、9 數牌與字牌）
     */
    data object KyuushuKyuuhai : RiichiExhaustiveDrawReason()

    /**
     * 四風連打。
     * 在第一巡中，四名玩家連續打出同一種風牌（東、南、西、北）
     *
     * 例外情況：如果 4 個槓子都由同一個人達成，則不流局（因為該玩家可能正在做「四槓子」役滿）。
     */
    data object SuufonRenda : RiichiExhaustiveDrawReason()

    /**
     * 四槓散了。
     * 全場玩家合計進行了 4 次槓牌（明槓、暗槓、加槓皆算）。
     */
    data object SuukanNagare : RiichiExhaustiveDrawReason()

    /**
     * 四家立直。
     * 四位玩家全部宣告立直。
     */
    data object SuuchaRiichi : RiichiExhaustiveDrawReason()
}
