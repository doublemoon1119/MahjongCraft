package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
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
 * 決策方法使用 `suspend`，讓策略能在等待外部計算結果時暫停，而不必阻塞呼叫執行緒。
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
     *
     * @param context 本次決策可見的桌況、合法動作與規則限制。
     * @return 要求呼叫端驗證並套用的命令；回傳命令本身不會修改桌況。
     */
    suspend fun decideGameCommand(context: AiDecisionContext): GameCommand

    /**
     * 依 [context] 決定規則選擇步驟的提交內容；未覆寫時使用情境提供的預設提交。
     *
     * @param context 本次選擇步驟的可見資訊與可提交選項。
     * @return 要求呼叫端驗證並套用的規則選擇提交。
     */
    suspend fun decideRuleDecision(context: RuleDecisionAiContext): RuleDecisionSubmission = context.defaultSubmission()
}
