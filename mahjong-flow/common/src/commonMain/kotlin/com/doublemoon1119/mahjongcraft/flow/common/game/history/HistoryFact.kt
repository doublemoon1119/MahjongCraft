package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 一場對局中已提交的權威事實；不包含被拒絕的命令、通知或呈現事件。 */
sealed interface HistoryFact {
    /**
     * 保存完整開局桌況與流程設定，供後續事件解讀牌 UUID。
     *
     * @property tableState 開局時含完整牌牆與座位的伺服器桌況。
     * @property flowConfig 此場對局採用的流程設定。
     * @property aiPlayerStrategyKeys 開局時由 AI 操控的玩家 Uuid 對應到其 AI 策略 key，見 `Game.aiPlayerStrategyKeys`。
     */
    data class MatchStarted(
        val tableState: TableState,
        val flowConfig: GameFlowConfig,
        val aiPlayerStrategyKeys: Map<Uuid, String>,
    ) : HistoryFact

    /**
     * 保存換局後的新牌牆及起始桌況。
     *
     * @property tableState 新局開始時的完整伺服器桌況。
     */
    data class RoundStarted(val tableState: TableState) : HistoryFact

    /**
     * 建立一個規則提供的開局準備步驟。
     *
     * @property stepId 規則提供的穩定步驟 ID。
     * @property stepIndex 此局內的步驟順序。
     */
    data class RoundPreparationStarted(val stepId: String, val stepIndex: Int) : HistoryFact

    /**
     * 玩家提交開局準備資料；桌況變化由同筆交易的結果事實保存。
     *
     * @property stepId 被提交的步驟 ID。
     * @property stepIndex 此局內的步驟順序。
     * @property submission 已接受的玩家選擇。
     * @property nextStepId 步驟完成後接續的步驟 ID；沒有後續步驟時為 null。
     */
    data class RoundPreparationSubmitted(
        val stepId: String,
        val stepIndex: Int,
        val submission: RoundPreparationSubmission,
        val nextStepId: String?,
    ) : HistoryFact

    /**
     * 規則自動完成一個不需玩家輸入的開局準備步驟。
     *
     * @property stepId 已完成的步驟 ID。
     * @property stepIndex 此局內的步驟順序。
     * @property nextStepId 接續的步驟 ID；準備流程結束時為 null。
     */
    data class RoundPreparationAutomaticallyResolved(
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
    ) : HistoryFact

    /**
     * 已接受的動作及其提交後結果；反應視窗的最終裁定另由 [ReactionResolved] 保存。
     *
     * @property action 已通過規則驗證的動作。
     * @property result 動作所在交易提交後的權威結果。
     */
    data class ActionAccepted(val action: GameAction, val result: HistoryActionResult) : HistoryFact

    /**
     * 多人反應收斂後實際套用的結果；不等同於單一玩家提交的反應。
     *
     * @property resolvedAction 得標或胡牌的動作；全員過牌時為 null。
     * @property actorPlayerId 得標玩家；沒有單一得標者時為 null。
     */
    data class ReactionResolved(
        val resolvedAction: GameAction?,
        val actorPlayerId: Uuid?,
    ) : HistoryFact

    /**
     * 一局完成後的結算事實。
     *
     * @property summary 結算原因、受益者及已結算分數的權威摘要。
     */
    data class RoundCompleted(
        val summary: RoundCompletionSummary,
    ) : HistoryFact

    /**
     * 胡牌結算交易內保存的規則詳情；本局稍後是否繼續不影響此事實。
     *
     * @property outcomeId 胡牌結算原因識別碼。
     * @property winDetails 各贏家的已解析公開詳情。
     * @property responsiblePlayerIds 放銃或其他責任玩家 UUID；自摸時為空清單。
     */
    data class WinSettled(
        val outcomeId: String,
        val winDetails: List<HistoryWinDetails>,
        val responsiblePlayerIds: List<Uuid> = emptyList(),
    ) : HistoryFact

    /**
     * 整場結束時的原因與最終分數；排名仍由該規則決定。
     *
     * @property reasonId 完整的終局原因 ID。
     * @property finalScoresByPlayerId 以玩家 UUID 索引的最終分數。
     */
    data class MatchCompleted(val reasonId: String, val finalScoresByPlayerId: Map<Uuid, Int>) : HistoryFact

    /**
     * 尚未正常結束的對局被移除時提交的終止事實；不代表完成結算或可封存的完整對局。
     *
     * @property reasonId 呼叫端定義的終止原因 ID；流程層僅原樣轉交，不解釋原因內容。
     */
    data class MatchAborted(val reasonId: String) : HistoryFact

    /**
     * 胡牌結算後，規則決定本局繼續或結束的權威結果。
     *
     * @property directive 本局後續的規則決策。
     */
    data class WinContinuationResolved(
        val directive: WinRoundDirective,
    ) : HistoryFact

    /**
     * 其他規則中立的後續效果；桌況變化由同筆交易的結果事實保存。
     *
     * @property reasonId 識別這次規則效果的穩定 ID。
     * @property roundCompletion 效果直接完成本局時的結算摘要；否則為 null。
     * @property winDetails 效果產生的胡牌公開詳情；沒有可保存詳情時為空清單。
     */
    data class RuleEffectResolved(
        val reasonId: String,
        val roundCompletion: RoundCompletionSummary?,
        val winDetails: List<HistoryWinDetails> = emptyList(),
    ) : HistoryFact

    /**
     * 同一權威交易中所有語意事實後的唯一桌況結果。
     *
     * @property result 可重建的差異，或標示原因的完整檢查點。
     */
    data class TableChanged(
        val result: HistoryTableResult,
    ) : HistoryFact

    /** 已結束的 Game 被移出權威狀態並返回 Room。 */
    data object ReturnedToRoom : HistoryFact
}
