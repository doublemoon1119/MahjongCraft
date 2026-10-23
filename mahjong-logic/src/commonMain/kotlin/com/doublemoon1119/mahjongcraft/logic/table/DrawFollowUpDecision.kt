package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/** 規則對一次摸牌後自動處理的決策。 */
sealed interface DrawFollowUpDecision {
    /** 不做任何處理，玩家照常進入自己的回合。 */
    data object None : DrawFollowUpDecision

    /**
     * 自動執行規則的移出手牌動作，移出這次詢問的牌，之後依規則補牌；補到的牌會再詢問一次。
     *
     * 規則的 `tileSetAsideBy` 必須選出這次詢問、位於摸牌位的牌，否則視為規則錯誤；補牌來源由規則的補牌 policy 決定。
     * 其他玩家能否搶這張牌，與玩家自行宣告這個動作時相同，由規則的合法動作判定器決定。
     *
     * @property action 要自動執行的移出手牌動作。
     */
    data class SetAside(val action: GameAction.Extension) : DrawFollowUpDecision

    /**
     * 以流局結束本局，例如需要移出手牌卻已經沒有牌可以補。
     *
     * @property reason 規則提供的流局原因。
     */
    data class EndRound(val reason: ExhaustiveDrawReason) : DrawFollowUpDecision
}
