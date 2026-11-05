package com.doublemoon1119.mahjongcraft.logic.table

/**
 * 一局目前所處的階段，回答「現在牌局在等什麼」；輪到哪位玩家由 [TableState.currentPlayerIndex] 記錄。
 *
 * 流程依階段判斷玩家可以做什麼，不從手牌內容或動作紀錄推算。
 */
sealed interface TurnPhase {
    /** 目前玩家接下來要摸牌。 */
    data object AwaitingDraw : TurnPhase

    /** 目前玩家已摸到或補到牌，可以自摸、槓、移出、宣告或打牌。 */
    data object AwaitingAction : TurnPhase

    /** 目前玩家剛鳴牌，只能打牌。 */
    data object AwaitingDiscard : TurnPhase

    /**
     * 其他玩家正在決定是否回應一張捨牌，例如吃、碰、槓或榮和。
     *
     * @property reaction 捨牌反應的資格與已收到的回應。
     */
    data class AwaitingDiscardReaction(val reaction: PendingReaction) : TurnPhase

    /**
     * 其他玩家正在決定是否搶和一張宣告中的牌，例如搶槓。
     *
     * @property reaction 搶和反應的資格與已收到的回應。
     */
    data class AwaitingRobbingReaction(val reaction: PendingRobbingReaction) : TurnPhase

    /**
     * 本局已胡牌或流局，等待結算與換局。
     *
     * @property conclusion 本局結束的方式。
     */
    data class RoundOver(val conclusion: RoundConclusion) : TurnPhase
}
