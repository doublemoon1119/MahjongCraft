package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/**
 * AI 玩家的決策策略：給定目前的情境，決定要執行哪個操作。
 *
 * 只負責「決定要做什麼」，不負責「怎麼把決定套用到桌況」——那是呼叫端的事，這裡不依賴任何套用決定的流程。
 *
 * [decideGameCommand] 回傳 [GameCommand] 而非 [GameAction]：兩者形狀不是一對一
 * （例如某些擴充動作還需要由規則提供的 AI mapper 補齊命令參數；捨牌本身也不在
 * [AiDecisionContext.legalActions] 裡），AI 選完合法動作後還要自己決定「打哪張牌」，這個轉換
 * 本身就是策略的一部分，不能交給呼叫端做。
 *
 * 介面方法刻意標成 `suspend`：是為未來可能引入的 LLM 型策略（例如透過 Koog 呼叫語言模型）預留
 * 的空間——真正呼叫遠端模型本來就需要非同步等待，現在就把介面定成 `suspend`，未來新增那樣的實作
 * 不需要更動這個介面。
 *
 * 執行緒契約：
 * - 方法可能在伺服器主執行緒以外的背景執行緒上呼叫，不得假設在主執行緒上，也不得存取只能在主執行緒使用的物件。
 * - 不同對局可能同時呼叫同一個策略實例；同一局同一時間只會有一次呼叫在執行。策略不得修改跨呼叫共用的可變狀態；
 *   需要的暫存資料應只活在一次呼叫內，或自行保證可跨執行緒使用。
 * - 呼叫端只等待有限的時間，超過後改用伺服器的固定命令並要求取消這次呼叫。取消是合作式的，長時間的計算應定期容許取消；
 *   不理會取消的呼叫會占用 AI 的執行資源直到結束，且在它結束前，這一局的決策都使用固定命令。
 */
interface MahjongAiStrategy {
    /**
     * 依 [context] 決定這位 AI 玩家接下來要執行的操作。
     */
    suspend fun decideGameCommand(context: AiDecisionContext): GameCommand

    /** 依 [context] 決定開局準備步驟的提交內容。 */
    suspend fun decideRoundPreparation(context: RoundPreparationAiContext): RoundPreparationSubmission = context.defaultSubmission()
}
