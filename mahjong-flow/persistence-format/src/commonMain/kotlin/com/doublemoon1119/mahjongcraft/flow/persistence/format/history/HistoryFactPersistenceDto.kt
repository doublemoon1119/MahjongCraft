package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.GameFlowConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.GameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.OpeningDiceRollPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RoundCompletionSummaryPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RoundPreparationSubmissionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 帶明確序列化種類的歷史事實 DTO。 */
@Serializable
sealed interface HistoryFactPersistenceDto {
    /**
     * 對局建立時的完整桌況與流程設定。
     *
     * @property state 對局開始時的桌況。
     * @property flowConfig 對局使用的流程設定。
     * @property aiPlayerStrategyKeys 以玩家 UUID 字串索引的 AI 策略 key；真人玩家不列入。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.MATCH_STARTED)
    data class MatchStarted(
        val state: TableStatePersistenceDto,
        val flowConfig: GameFlowConfigPersistenceDto,
        val aiPlayerStrategyKeys: Map<String, String>,
    ) : HistoryFactPersistenceDto

    /**
     * 新局開始時的完整桌況。
     *
     * @property state 新局開始時的桌況。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.ROUND_STARTED)
    data class RoundStarted(
        val state: TableStatePersistenceDto,
    ) : HistoryFactPersistenceDto

    /**
     * 開局程序一輪擲骰的保存資料。
     *
     * @property roll 本輪的順序、實際擲骰者與逐顆點數。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.DICE_ROLLED)
    data class DiceRolled(
        val roll: OpeningDiceRollPersistenceDto,
    ) : HistoryFactPersistenceDto

    /**
     * 開局準備流程開始執行一個步驟。
     *
     * @property stepId 準備步驟的穩定識別碼。
     * @property stepIndex 準備步驟在流程中的索引。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.ROUND_PREPARATION_STARTED)
    data class RoundPreparationStarted(
        val stepId: String,
        val stepIndex: Int,
    ) : HistoryFactPersistenceDto

    /**
     * 玩家提交開局準備內容後的結果。
     *
     * @property stepId 被提交的準備步驟識別碼。
     * @property stepIndex 被提交的準備步驟索引。
     * @property submission 玩家提交的準備內容。
     * @property nextStepId 下一個準備步驟識別碼；沒有下一步時為 null。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.ROUND_PREPARATION_SUBMITTED)
    data class RoundPreparationSubmitted(
        val stepId: String,
        val stepIndex: Int,
        val submission: RoundPreparationSubmissionPersistenceDto,
        val nextStepId: String?,
    ) : HistoryFactPersistenceDto

    /**
     * 系統自動完成開局準備步驟後的結果。
     *
     * @property stepId 被自動完成的準備步驟識別碼。
     * @property stepIndex 被自動完成的準備步驟索引。
     * @property nextStepId 下一個準備步驟識別碼；沒有下一步時為 null。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.ROUND_PREPARATION_AUTOMATIC_RESOLVED)
    data class RoundPreparationAutomaticallyResolved(
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
    ) : HistoryFactPersistenceDto

    /**
     * 規則接受一個玩家動作後的結果。
     *
     * @property action 被接受的動作。
     * @property result 動作套用後的權威結果。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.ACTION_ACCEPTED)
    data class ActionAccepted(
        val action: GameActionPersistenceDto,
        val result: HistoryActionResultPersistenceDto,
    ) : HistoryFactPersistenceDto

    /**
     * 動作成立當下的點數收付。
     *
     * @property action 觸發收付的動作。
     * @property scoreDeltasByPlayerId 各玩家的點數增減，鍵為玩家 UUID 字串。
     * @property potDeposit 放進規則點數池的點數，正數為放入、負數為取出。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.ACTION_SCORED)
    data class ActionScored(
        val action: GameActionPersistenceDto,
        val scoreDeltasByPlayerId: Map<String, Int>,
        val potDeposit: Int,
    ) : HistoryFactPersistenceDto

    /**
     * 多方反應結束後的實際結算結果。
     *
     * @property resolvedAction 反應結算後實際成立的動作；沒有動作成立時為 null。
     * @property actorPlayerId 單一得標玩家的 UUID 字串；全員過牌或多家和牌時為 null。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.REACTION_RESOLVED)
    data class ReactionResolved(
        val resolvedAction: GameActionPersistenceDto?,
        val actorPlayerId: String?,
    ) : HistoryFactPersistenceDto

    /**
     * 本局結束時的結算摘要。
     *
     * @property summary 本局結算摘要。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.ROUND_COMPLETED)
    data class RoundCompleted(
        val summary: RoundCompletionSummaryPersistenceDto,
    ) : HistoryFactPersistenceDto

    /**
     * 和牌結算交易內的規則詳情。
     *
     * @property outcomeId 和牌結算原因識別碼。
     * @property winDetails 各贏家在結算時已確定的明細。
     * @property responsiblePlayerIds 放銃或其他責任玩家的 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.WIN_SETTLED)
    data class WinSettled(
        val outcomeId: String,
        val winDetails: List<HistoryWinDetailsPersistenceDto>,
        val responsiblePlayerIds: List<String>,
    ) : HistoryFactPersistenceDto

    /**
     * 整場對局結束時的原因與最終分數。
     *
     * @property reasonId 對局結束原因的 namespaced key。
     * @property finalScoresByPlayerId 各玩家最終分數，鍵為玩家 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.MATCH_COMPLETED)
    data class MatchCompleted(
        val reasonId: String,
        val finalScoresByPlayerId: Map<String, Int>,
    ) : HistoryFactPersistenceDto

    /**
     * 對局在正常完成前被移除時的終止原因。
     *
     * @property reasonId 呼叫端提供、原樣保存的中止原因識別碼。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.MATCH_ABORTED)
    data class MatchAborted(
        val reasonId: String,
    ) : HistoryFactPersistenceDto

    /**
     * 胡牌後續流程完成決策後的結果。
     *
     * @property directive 胡牌後決定的本局後續。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.WIN_CONTINUATION_RESOLVED)
    data class WinContinuationResolved(
        val directive: WinRoundDirectivePersistenceDto,
    ) : HistoryFactPersistenceDto

    /**
     * 已完成的規則效果與可能的本局結算。
     *
     * @property reasonId 規則效果的識別碼。
     * @property roundCompletion 規則效果同時完成本局時的結算摘要；否則為 null。
     * @property winDetails 規則效果產生的胡牌公開詳情；沒有時為空清單。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.RULE_EFFECT_RESOLVED)
    data class RuleEffectResolved(
        val reasonId: String,
        val roundCompletion: RoundCompletionSummaryPersistenceDto?,
        val winDetails: List<HistoryWinDetailsPersistenceDto>,
    ) : HistoryFactPersistenceDto

    /**
     * 同一權威交易內所有語意事實之後唯一的桌況結果。
     *
     * @property result 可重建的結構差異，或標示原因的完整檢查點。
     */
    @Serializable
    @SerialName(HistoryFactTypeKeys.TABLE_CHANGED)
    data class TableChanged(
        val result: HistoryTableResultPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 對局移除並返回房間。 */
    @Serializable
    @SerialName(HistoryFactTypeKeys.RETURNED_TO_ROOM)
    data object ReturnedToRoom : HistoryFactPersistenceDto
}
