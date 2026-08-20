package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/**
 * AI 玩家的決策策略：給定目前的情境，決定要執行哪個操作。
 *
 * 只負責「決定要做什麼」，不負責「怎麼把決定套用到桌況」——那是呼叫端（伺服器端的協調邏輯）的事，
 * 這裡完全不依賴 `:mahjong-flow-server` 的 use case 層。
 *
 * `decide` 回傳 [GameCommand] 而非 [GameAction]：兩者形狀不是一對一
 * （例如 `GameAction.Riichi` 不帶欲宣告的捨牌張，但 `GameCommand.Riichi` 需要；捨牌本身也不在
 * [AiDecisionContext.legalActions] 裡），AI 選完合法動作後還要自己決定「打哪張牌」，這個轉換
 * 本身就是策略的一部分，不能交給呼叫端做。
 *
 * 介面方法刻意標成 `suspend`：是為未來可能引入的 LLM 型策略（例如透過 Koog 呼叫語言模型）預留
 * 的空間——真正呼叫遠端模型本來就需要非同步等待，現在就把介面定成 `suspend`，未來新增那樣的實作
 * 不需要更動這個介面。
 */
interface MahjongAiStrategy {
    /**
     * 依 [context] 決定這位 AI 玩家接下來要執行的操作。
     */
    suspend fun decide(context: AiDecisionContext): GameCommand
}
