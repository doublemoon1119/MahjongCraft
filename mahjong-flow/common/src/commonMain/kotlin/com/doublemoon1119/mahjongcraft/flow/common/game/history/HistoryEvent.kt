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
     */
    data class MatchStarted(val tableState: TableState, val flowConfig: GameFlowConfig) : HistoryFact

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
    data class RoundCompleted(val summary: RoundCompletionSummary) : HistoryFact

    /**
     * 整場結束時的原因與最終分數；排名仍由該規則決定。
     *
     * @property reasonId 完整的終局原因 ID。
     * @property finalScoresByPlayerId 以玩家 UUID 索引的最終分數。
     */
    data class MatchCompleted(val reasonId: String, val finalScoresByPlayerId: Map<Uuid, Int>) : HistoryFact

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
     */
    data class RuleEffectResolved(
        val reasonId: String,
        val roundCompletion: RoundCompletionSummary?,
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

/**
 * 動作提交後的權威結果，不從玩家通知或各人本局 action history 回推。
 *
 * @property affectedTileIds 動作直接涉及的牌 UUID。
 * @property newlyRevealedTileIds 本次交易新增公開的牌 UUID。
 * @property remainingWallTileCount 提交後活牌牆的剩餘張數。
 * @property reservedWallTileIds 提交後保留牌區的牌 UUID，依規則順序排列。
 * @property scoresByPlayerId 提交後各玩家分數。
 * @property nextPlayerId 提交後輪到的玩家 UUID。
 */
data class HistoryActionResult(
    val affectedTileIds: List<Uuid>,
    val newlyRevealedTileIds: List<Uuid>,
    val remainingWallTileCount: Int,
    val reservedWallTileIds: List<Uuid>,
    val scoresByPlayerId: Map<Uuid, Int>,
    val nextPlayerId: Uuid,
)

/**
 * 在權威交易內產生、尚未指派序號與時間戳的歷史事件。
 *
 * @property actorPlayerId 發起動作的玩家；系統或規則事件為 null。
 * @property fact 已提交的權威事實。
 */
data class HistoryEventDraft(
    val actorPlayerId: Uuid?,
    val fact: HistoryFact,
)

/**
 * 保存於權威 outbox、供後續 writer 依場次與序號組成的穩定鍵重試的事件。
 *
 * @property matchId 此次開局的獨立場次 UUID，同桌重開時不同。
 * @property tableId 牌桌 UUID，同桌重開時保持不變。
 * @property roundNumber 此場次內的局數。
 * @property sequence 此場次內單調遞增的事件序號，從 1 開始。
 * @property transactionFirstSequence 同一權威交易內第一筆事件的序號。
 * @property occurredAtEpochMillis 事件提交時的 UTC 毫秒時間戳，不用於排序。
 * @property actorPlayerId 發起動作的玩家；系統或規則事件為 null。
 * @property fact 已提交的權威事實。
 */
data class HistoryOutboxEvent(
    val matchId: Uuid,
    val tableId: Uuid,
    val roundNumber: Int,
    val sequence: Long,
    val transactionFirstSequence: Long = sequence,
    val occurredAtEpochMillis: Long,
    val actorPlayerId: Uuid?,
    val fact: HistoryFact,
) {
    init {
        require(sequence > 0L) { "History sequence must be positive" }
        require(transactionFirstSequence in 1L..sequence) { "History transaction sequence must not exceed event sequence" }
        require(roundNumber > 0) { "History round number must be positive" }
    }
}
