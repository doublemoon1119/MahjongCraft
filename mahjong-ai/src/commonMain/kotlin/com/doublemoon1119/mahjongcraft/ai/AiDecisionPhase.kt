package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/**
 * AI 玩家目前所處的決策情境，鏡射
 * `com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase` 內部已經在做的三種區分。
 * [MahjongAiStrategy] 需要知道自己正處於哪種情境，才能把選出的 [GameAction]
 * 包成正確的 [GameCommand] 變體（[RespondingToDiscard] 對應 `GameCommand.RespondToDiscard`、
 * [RespondingToKan] 對應 `GameCommand.RespondToKan`，兩者的 `action` 形狀高度重疊，光看合法動作清單本身無法區分）。
 */
sealed interface AiDecisionPhase {
    /** 輪到自己回合，可能已摸牌、可能尚未摸牌。 */
    data object OwnTurn : AiDecisionPhase

    /** 有資格回應他家的捨牌（吃/碰/明槓/榮和/過），且尚未回應。 */
    data object RespondingToDiscard : AiDecisionPhase

    /** 有資格回應他家的暗槓/加槓宣告（搶槓榮和/過），且尚未回應。 */
    data object RespondingToKan : AiDecisionPhase
}
