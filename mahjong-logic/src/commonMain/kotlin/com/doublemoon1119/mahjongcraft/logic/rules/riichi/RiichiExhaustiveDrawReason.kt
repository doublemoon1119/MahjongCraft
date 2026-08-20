package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.table.PendingChankanReaction

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
     */
    data object SuufonRenda : RiichiExhaustiveDrawReason()

    /**
     * 四槓散了。
     * 全場玩家合計進行了 4 次槓牌（明槓、暗槓、加槓皆算）。
     *
     * 例外情況：如果 4 個槓子都由同一個人達成，則不流局（因為該玩家可能正在做「四槓子」役滿）。
     */
    data object SuukanNagare : RiichiExhaustiveDrawReason()

    /**
     * 四家立直。
     * 四位玩家全部宣告立直。
     */
    data object SuuchaRiichi : RiichiExhaustiveDrawReason()

    /**
     * 三家和了（トリロン）。
     * 同一張牌同時有三家（或依 [MultiRonPolicy]
     * 設定、二家）可榮和，且規則設定為流局（[RonResolution.ABORTIVE_DRAW]）時觸發——
     * 這張牌可能是一般捨牌，也可能是搶槓（[PendingChankanReaction]）
     * 的來源，兩者共用同一套多響判定，沿用同一個原因值，不另外區分。
     * 真實規則中「三家和了」專指三家同時可榮和的情境；本專案的 `MultiRonPolicy` 額外允許把雙響也
     * 設定為 `ABORTIVE_DRAW`（見 [RiichiRuleConfig.multiRonPolicy] 的既有說明，本身就是刻意貼近
     * 多數玩家熟悉體驗、不完全依循單一規則基準的設計），二響觸發時的遊戲行為（流局、莊家連莊、
     * 不結算點數）與三響完全相同，同樣沿用同一個原因值。
     */
    data object SanchaHou : RiichiExhaustiveDrawReason()
}
