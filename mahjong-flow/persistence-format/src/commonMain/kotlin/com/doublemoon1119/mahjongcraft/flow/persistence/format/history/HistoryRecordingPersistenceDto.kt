package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTransfer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementMode
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.GameFlowConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.GameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RoundCompletionSummaryPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RoundPreparationSubmissionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** 權威歷史待寫事件的持久化快照；此格式與 SQLite schema 分開演進。
 *
 * @property formatVersion 此快照格式的版本號。
 * @property nextSequenceByMatchId 各對局下一個可分配的事件序號，鍵為對局 UUID 字串。
 * @property pendingEvents 尚未交給歷史儲存端的事件；各場次的事件依序號排序。
 * @property firstMissingSequenceByMatchId 各對局第一個無法完整還原的事件序號，鍵為對局 UUID 字串。
 * @property decisionsByMatchId 各對局固定的歷史記錄決策，值為列舉名稱。
 * @property terminalByMatchId 已不可接續場次的權威終點證據。
 * @property transfersByMatchId 未完成的隔離權威歷史轉移；載入後保留部分紀錄而不重新執行來源。
 */
@Serializable
data class HistoryRecordingPersistenceDto(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val formatVersion: Int = 1,
    val nextSequenceByMatchId: Map<String, Long> = emptyMap(),
    val pendingEvents: List<HistoryOutboxEventPersistenceDto> = emptyList(),
    val firstMissingSequenceByMatchId: Map<String, Long> = emptyMap(),
    val decisionsByMatchId: Map<String, String> = emptyMap(),
    val terminalByMatchId: Map<String, HistoryRecordingTerminalPersistenceDto> = emptyMap(),
    val transfersByMatchId: Map<String, HistoryRecordingTransferPersistenceDto> = emptyMap(),
) {
    init {
        require(formatVersion == 1) { "Unsupported history outbox format $formatVersion" }
    }
}

/**
 * 未完成歷史轉移的持久化 metadata。
 *
 * @property tableId 來源牌桌 UUID 字串。
 * @property lastAcceptedBatch 最近確認的完整交易批次。
 * @property matchCompleted 是否已有整場完成事實。
 */
@Serializable
data class HistoryRecordingTransferPersistenceDto(
    val tableId: String,
    val lastAcceptedBatch: List<HistoryOutboxEventPersistenceDto>,
    val matchCompleted: Boolean,
)

/** 已結束歷史場次的持久化終點證據。
 *
 * @property endedAtEpochMillis 場次結束的 UTC 毫秒時間戳。
 * @property completed 場次是否正常完成整場對局。
 * @property tableId 原牌桌 UUID 字串。
 */
@Serializable
data class HistoryRecordingTerminalPersistenceDto(
    val endedAtEpochMillis: Long,
    val completed: Boolean,
    val tableId: String,
)

/** 一筆已指派穩定鍵、可重試寫入的歷史事件。
 *
 * @property matchId 事件所屬對局的 UUID 字串。
 * @property tableId 事件所屬牌桌的 UUID 字串。
 * @property roundNumber 事件發生時所在的局數。
 * @property sequence 在同一對局內單調遞增的事件序號。
 * @property occurredAtEpochMillis 事件發生時間的 Unix epoch 毫秒值。
 * @property actorPlayerId 觸發事件的玩家 UUID 字串；系統事件為 null。
 * @property fact 事件的具體歷史事實。
 * @property transactionFirstSequence 同一權威交易第一筆事件的序號。
 */
@Serializable
data class HistoryOutboxEventPersistenceDto(
    val matchId: String,
    val tableId: String,
    val roundNumber: Int,
    val sequence: Long,
    val occurredAtEpochMillis: Long,
    val actorPlayerId: String?,
    val fact: HistoryFactPersistenceDto,
    val transactionFirstSequence: Long = sequence,
)

/** 帶明確序列化種類的歷史事實 DTO。 */
@Serializable
sealed interface HistoryFactPersistenceDto {
    /** 對局建立時的完整桌況與流程設定。
     *
     * @property state 對局開始時的桌況。
     * @property flowConfig 對局使用的流程設定。
     */
    @Serializable
    @SerialName("match_started")
    data class MatchStarted(
        val state: TableStatePersistenceDto,
        val flowConfig: GameFlowConfigPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 新局開始時的完整桌況。
     *
     * @property state 新局開始時的桌況。
     */
    @Serializable
    @SerialName("round_started")
    data class RoundStarted(
        val state: TableStatePersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 開局準備流程開始執行一個步驟。
     *
     * @property stepId 準備步驟的穩定識別碼。
     * @property stepIndex 準備步驟在流程中的索引。
     */
    @Serializable
    @SerialName("round_preparation_started")
    data class RoundPreparationStarted(
        val stepId: String,
        val stepIndex: Int,
    ) : HistoryFactPersistenceDto

    /** 玩家提交開局準備內容後的結果。
     *
     * @property stepId 被提交的準備步驟識別碼。
     * @property stepIndex 被提交的準備步驟索引。
     * @property submission 玩家提交的準備內容。
     * @property nextStepId 下一個準備步驟識別碼；沒有下一步時為 null。
     */
    @Serializable
    @SerialName("round_preparation_submitted")
    data class RoundPreparationSubmitted(
        val stepId: String,
        val stepIndex: Int,
        val submission: RoundPreparationSubmissionPersistenceDto,
        val nextStepId: String?,
    ) : HistoryFactPersistenceDto

    /** 系統自動完成開局準備步驟後的結果。
     *
     * @property stepId 被自動完成的準備步驟識別碼。
     * @property stepIndex 被自動完成的準備步驟索引。
     * @property nextStepId 下一個準備步驟識別碼；沒有下一步時為 null。
     */
    @Serializable
    @SerialName("round_preparation_automatic_resolved")
    data class RoundPreparationAutomaticallyResolved(
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
    ) : HistoryFactPersistenceDto

    /** 規則接受一個玩家動作後的結果。
     *
     * @property action 被接受的動作。
     * @property result 動作套用後的權威結果。
     */
    @Serializable
    @SerialName("action_accepted")
    data class ActionAccepted(
        val action: GameActionPersistenceDto,
        val result: HistoryActionResultPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 多方反應結束後的實際結算結果。
     *
     * @property resolvedAction 反應結算後實際成立的動作；沒有動作成立時為 null。
     * @property actorPlayerId 單一得標玩家的 UUID 字串；全員過牌或多家和牌時為 null。
     */
    @Serializable
    @SerialName("reaction_resolved")
    data class ReactionResolved(
        val resolvedAction: GameActionPersistenceDto?,
        val actorPlayerId: String?,
    ) : HistoryFactPersistenceDto

    /** 本局結束時的結算摘要。
     *
     * @property summary 本局結算摘要。
     */
    @Serializable
    @SerialName("round_completed")
    data class RoundCompleted(
        val summary: RoundCompletionSummaryPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 整場對局結束時的原因與最終分數。
     *
     * @property reasonId 對局結束原因的 namespaced key。
     * @property finalScoresByPlayerId 各玩家最終分數，鍵為玩家 UUID 字串。
     */
    @Serializable
    @SerialName("match_completed")
    data class MatchCompleted(
        val reasonId: String,
        val finalScoresByPlayerId: Map<String, Int>,
    ) : HistoryFactPersistenceDto

    /** 胡牌後續流程完成決策後的結果。
     *
     * @property directive 胡牌後決定的本局後續。
     */
    @Serializable
    @SerialName("win_continuation_resolved")
    data class WinContinuationResolved(
        val directive: WinRoundDirectivePersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 規則效果完成後的桌況與可能的本局結算。
     *
     * @property reasonId 規則效果的識別碼。
     * @property roundCompletion 規則效果同時完成本局時的結算摘要；否則為 null。
     */
    @Serializable
    @SerialName("rule_effect_resolved")
    data class RuleEffectResolved(
        val reasonId: String,
        val roundCompletion: RoundCompletionSummaryPersistenceDto?,
    ) : HistoryFactPersistenceDto

    /** 同一權威交易內所有語意事實之後唯一的桌況結果。
     *
     * @property result 可重建的結構差異，或標示原因的完整檢查點。
     */
    @Serializable
    @SerialName("table_changed")
    data class TableChanged(
        val result: HistoryTableResultPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 對局移除並返回房間。 */
    @Serializable
    @SerialName("returned_to_room")
    data object ReturnedToRoom : HistoryFactPersistenceDto
}

/** 胡牌後續決策的明確持久化表示。 */
@Serializable
sealed interface WinRoundDirectivePersistenceDto {
    /** 結束目前這一局。 */
    @Serializable
    @SerialName("end_round")
    data object EndRound : WinRoundDirectivePersistenceDto

    /** 依指定結算模式繼續本局，並交由下一位玩家行動。
     *
     * @property newlyFinishedPlayerIds 因本次結算而完成的玩家 UUID 字串。
     * @property nextPlayerId 下一個行動玩家的 UUID 字串。
     * @property settlementMode 結算模式的列舉名稱。
     */
    @Serializable
    @SerialName("continue_round")
    data class ContinueRound(
        val newlyFinishedPlayerIds: Set<String>,
        val nextPlayerId: String,
        val settlementMode: String,
    ) : WinRoundDirectivePersistenceDto
}

/** 動作提交後的相關索引資料；桌況另由同筆交易結果保存。
 *
 * @property affectedTileIds 動作直接影響的牌 UUID 字串。
 * @property newlyRevealedTileIds 因本次動作新公開的牌 UUID 字串。
 * @property remainingWallTileCount 動作完成後活牌牆剩餘牌數。
 * @property reservedWallTileIds 動作完成後保留區中的牌 UUID 字串。
 * @property scoresByPlayerId 動作完成後各玩家分數。
 * @property nextPlayerId 下一個行動玩家的 UUID 字串。
 */
@Serializable
data class HistoryActionResultPersistenceDto(
    val affectedTileIds: List<String>,
    val newlyRevealedTileIds: List<String>,
    val remainingWallTileCount: Int,
    val reservedWallTileIds: List<String>,
    val scoresByPlayerId: Map<String, Int>,
    val nextPlayerId: String,
)

/**
 * 使用已凍結 registry 在 domain 事件與持久化 DTO 間轉換完整 outbox。
 *
 * @property registries 解碼規則、動作與桌況所需的持久化 registry。
 * @property json 用於擴充動作內容序列化的 JSON 設定。
 */
class HistoryRecordingPersistenceMapper(
    private val registries: PersistenceRegistries,
    private val json: Json = Json,
) {
    /** 將單筆待寫事件編碼；失敗時交由呼叫端保留並重試，絕不略過後確認。 */
    fun encodePendingEvent(event: HistoryOutboxEvent): HistoryOutboxEventPersistenceDto = encodeEvent(event)

    /** 將單筆資料庫事件解回權威事實；失敗時交由呼叫端標記缺口，絕不略過後封存。 */
    fun decodePendingEvent(event: HistoryOutboxEventPersistenceDto): HistoryOutboxEvent = decodeEvent(event)

    /** 將待寫事件編成權威存檔 DTO；無法編碼的事件會記錄序號缺口並略過。 */
    fun encode(state: HistoryRecordingState): HistoryRecordingPersistenceDto {
        val missing = state.firstMissingSequenceByMatchId.toMutableMap()
        val encoded = state.pendingEvents.mapNotNull { event ->
            runCatching { encodeEvent(event) }.getOrElse {
                missing[event.matchId] = minOf(missing[event.matchId] ?: event.sequence, event.sequence)
                null
            }
        }
        return HistoryRecordingPersistenceDto(
            nextSequenceByMatchId = state.nextSequenceByMatchId.mapKeys { it.key.toString() },
            pendingEvents = encoded,
            firstMissingSequenceByMatchId = missing.mapKeys { it.key.toString() },
            decisionsByMatchId = state.decisionsByMatchId.mapKeys { it.key.toString() }.mapValues { it.value.name },
            terminalByMatchId = state.terminalByMatchId.mapKeys { it.key.toString() }.mapValues { (_, terminal) ->
                HistoryRecordingTerminalPersistenceDto(
                    terminal.endedAtEpochMillis,
                    terminal.completed,
                    terminal.tableId.toString(),
                )
            },
            transfersByMatchId = state.transfersByMatchId.mapKeys { it.key.toString() }.mapValues { (_, transfer) ->
                HistoryRecordingTransferPersistenceDto(transfer.tableId.toString(), transfer.lastAcceptedBatch.map(::encodeEvent), transfer.matchCompleted)
            },
        )
    }

    /** 驗證並還原權威待寫事件；無法解碼的事件會記錄序號缺口並略過。 */
    fun decode(dto: HistoryRecordingPersistenceDto): HistoryRecordingState {
        val missing = dto.firstMissingSequenceByMatchId.mapKeys { Uuid.parse(it.key) }.toMutableMap()
        val decoded = dto.pendingEvents.mapNotNull { event ->
            val matchId = Uuid.parse(event.matchId)
            runCatching { decodeEvent(event) }.getOrElse {
                missing[matchId] = minOf(missing[matchId] ?: event.sequence, event.sequence)
                null
            }
        }
        return HistoryRecordingState(
            nextSequenceByMatchId = dto.nextSequenceByMatchId.mapKeys { Uuid.parse(it.key) },
            pendingEvents = decoded,
            firstMissingSequenceByMatchId = missing,
            decisionsByMatchId = dto.decisionsByMatchId.mapKeys { Uuid.parse(it.key) }
                .mapValues { HistoryRecordingDecision.valueOf(it.value) },
            terminalByMatchId = dto.terminalByMatchId.mapKeys { Uuid.parse(it.key) }.mapValues { (_, terminal) ->
                HistoryRecordingTerminal(
                    terminal.endedAtEpochMillis,
                    terminal.completed,
                    Uuid.parse(terminal.tableId),
                )
            },
            transfersByMatchId = dto.transfersByMatchId.mapKeys { Uuid.parse(it.key) }.mapValues { (_, transfer) ->
                HistoryRecordingTransfer(Uuid.parse(transfer.tableId), transfer.lastAcceptedBatch.map(::decodeEvent), transfer.matchCompleted)
            },
        )
    }

    /** 將單筆 domain 事件轉成持久化 DTO。 */
    private fun encodeEvent(event: HistoryOutboxEvent) = HistoryOutboxEventPersistenceDto(
        matchId = event.matchId.toString(),
        tableId = event.tableId.toString(),
        roundNumber = event.roundNumber,
        sequence = event.sequence,
        transactionFirstSequence = event.transactionFirstSequence,
        occurredAtEpochMillis = event.occurredAtEpochMillis,
        actorPlayerId = event.actorPlayerId?.toString(),
        fact = encodeFact(event.fact),
    )

    /** 將單筆持久化事件還原成 domain 事件。 */
    private fun decodeEvent(dto: HistoryOutboxEventPersistenceDto) = HistoryOutboxEvent(
        matchId = Uuid.parse(dto.matchId),
        tableId = Uuid.parse(dto.tableId),
        roundNumber = dto.roundNumber,
        sequence = dto.sequence,
        transactionFirstSequence = dto.transactionFirstSequence,
        occurredAtEpochMillis = dto.occurredAtEpochMillis,
        actorPlayerId = dto.actorPlayerId?.let(Uuid::parse),
        fact = decodeFact(dto.fact),
    )

    /** 將 domain 歷史事實轉成帶明確種類的持久化 DTO。 */
    private fun encodeFact(fact: HistoryFact): HistoryFactPersistenceDto = when (fact) {
        is HistoryFact.MatchStarted -> HistoryFactPersistenceDto.MatchStarted(encodeTable(fact.tableState), fact.flowConfig.toPersistenceDto())
        is HistoryFact.RoundStarted -> HistoryFactPersistenceDto.RoundStarted(encodeTable(fact.tableState))
        is HistoryFact.RoundPreparationStarted -> HistoryFactPersistenceDto.RoundPreparationStarted(fact.stepId, fact.stepIndex)
        is HistoryFact.RoundPreparationSubmitted -> HistoryFactPersistenceDto.RoundPreparationSubmitted(
            fact.stepId,
            fact.stepIndex,
            fact.submission.toPersistenceDto(),
            fact.nextStepId,
        )
        is HistoryFact.RoundPreparationAutomaticallyResolved -> HistoryFactPersistenceDto.RoundPreparationAutomaticallyResolved(
            fact.stepId,
            fact.stepIndex,
            fact.nextStepId,
        )
        is HistoryFact.ActionAccepted -> HistoryFactPersistenceDto.ActionAccepted(
            fact.action.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            fact.result.toPersistenceDto(),
        )
        is HistoryFact.ReactionResolved -> HistoryFactPersistenceDto.ReactionResolved(
            fact.resolvedAction?.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            fact.actorPlayerId?.toString(),
        )
        is HistoryFact.RoundCompleted -> HistoryFactPersistenceDto.RoundCompleted(fact.summary.toPersistenceDto())
        is HistoryFact.MatchCompleted -> HistoryFactPersistenceDto.MatchCompleted(
            fact.reasonId,
            fact.finalScoresByPlayerId.mapKeys { it.key.toString() },
        )
        is HistoryFact.WinContinuationResolved -> HistoryFactPersistenceDto.WinContinuationResolved(
            fact.directive.toPersistenceDto(),
        )
        is HistoryFact.RuleEffectResolved -> HistoryFactPersistenceDto.RuleEffectResolved(
            fact.reasonId,
            fact.roundCompletion?.toPersistenceDto(),
        )
        HistoryFact.ReturnedToRoom -> HistoryFactPersistenceDto.ReturnedToRoom
        is HistoryFact.TableChanged -> HistoryFactPersistenceDto.TableChanged(
            HistoryTableChangeMapper(registries, json).encode(fact.result),
        )
    }

    /** 將持久化歷史事實還原成 domain 事實。 */
    private fun decodeFact(dto: HistoryFactPersistenceDto): HistoryFact = when (dto) {
        is HistoryFactPersistenceDto.MatchStarted -> HistoryFact.MatchStarted(decodeTable(dto.state), dto.flowConfig.toDomain())
        is HistoryFactPersistenceDto.RoundStarted -> HistoryFact.RoundStarted(decodeTable(dto.state))
        is HistoryFactPersistenceDto.RoundPreparationStarted -> HistoryFact.RoundPreparationStarted(dto.stepId, dto.stepIndex)
        is HistoryFactPersistenceDto.RoundPreparationSubmitted -> HistoryFact.RoundPreparationSubmitted(
            dto.stepId,
            dto.stepIndex,
            dto.submission.toDomain(),
            dto.nextStepId,
        )
        is HistoryFactPersistenceDto.RoundPreparationAutomaticallyResolved -> HistoryFact.RoundPreparationAutomaticallyResolved(
            dto.stepId,
            dto.stepIndex,
            dto.nextStepId,
        )
        is HistoryFactPersistenceDto.ActionAccepted -> HistoryFact.ActionAccepted(
            dto.action.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            dto.result.toDomain(),
        )
        is HistoryFactPersistenceDto.ReactionResolved -> HistoryFact.ReactionResolved(
            dto.resolvedAction?.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            dto.actorPlayerId?.let(Uuid::parse),
        )
        is HistoryFactPersistenceDto.RoundCompleted -> HistoryFact.RoundCompleted(dto.summary.toDomain())
        is HistoryFactPersistenceDto.MatchCompleted -> HistoryFact.MatchCompleted(
            dto.reasonId,
            dto.finalScoresByPlayerId.mapKeys { Uuid.parse(it.key) },
        )
        is HistoryFactPersistenceDto.WinContinuationResolved -> HistoryFact.WinContinuationResolved(
            dto.directive.toDomain(),
        )
        is HistoryFactPersistenceDto.RuleEffectResolved -> HistoryFact.RuleEffectResolved(
            dto.reasonId,
            dto.roundCompletion?.toDomain(),
        )
        HistoryFactPersistenceDto.ReturnedToRoom -> HistoryFact.ReturnedToRoom
        is HistoryFactPersistenceDto.TableChanged -> HistoryFact.TableChanged(
            HistoryTableChangeMapper(registries, json).decode(dto.result),
        )
    }

    /** 使用 registry 將桌況編成持久化 DTO。 */
    private fun encodeTable(state: TableState) = state.toPersistenceDto(
        registries.ruleConfigs,
        registries.discardPiles,
        registries.playerRuleStates,
        registries.dynamicRuleStates,
        registries.exhaustiveDrawReasons,
        registries.extensionGameActions,
        json,
    )

    /** 使用 registry 將桌況 DTO 還原成 domain 桌況。 */
    private fun decodeTable(dto: TableStatePersistenceDto) = dto.toDomain(
        registries.ruleConfigs,
        registries.discardPiles,
        registries.playerRuleStates,
        registries.dynamicRuleStates,
        registries.exhaustiveDrawReasons,
        registries.extensionGameActions,
        json,
    )
}

/** 將胡牌後續決策轉成持久化 DTO。 */
private fun WinRoundDirective.toPersistenceDto(): WinRoundDirectivePersistenceDto = when (this) {
    WinRoundDirective.EndRound -> WinRoundDirectivePersistenceDto.EndRound
    is WinRoundDirective.ContinueRound -> WinRoundDirectivePersistenceDto.ContinueRound(
        newlyFinishedPlayerIds.map(Uuid::toString).toSet(),
        nextPlayerId.toString(),
        settlementMode.name,
    )
}

/** 將胡牌後續決策 DTO 還原成 domain 決策。 */
private fun WinRoundDirectivePersistenceDto.toDomain(): WinRoundDirective = when (this) {
    WinRoundDirectivePersistenceDto.EndRound -> WinRoundDirective.EndRound
    is WinRoundDirectivePersistenceDto.ContinueRound -> WinRoundDirective.ContinueRound(
        newlyFinishedPlayerIds.map(Uuid::parse).toSet(),
        Uuid.parse(nextPlayerId),
        ContinuingWinSettlementMode.valueOf(settlementMode),
    )
}

/** 將動作結果轉成持久化 DTO。 */
private fun HistoryActionResult.toPersistenceDto() = HistoryActionResultPersistenceDto(
    affectedTileIds = affectedTileIds.map(Uuid::toString),
    newlyRevealedTileIds = newlyRevealedTileIds.map(Uuid::toString),
    remainingWallTileCount = remainingWallTileCount,
    reservedWallTileIds = reservedWallTileIds.map(Uuid::toString),
    scoresByPlayerId = scoresByPlayerId.mapKeys { it.key.toString() },
    nextPlayerId = nextPlayerId.toString(),
)

/** 將動作結果 DTO 還原成 domain 結果。 */
private fun HistoryActionResultPersistenceDto.toDomain() = HistoryActionResult(
    affectedTileIds = affectedTileIds.map(Uuid::parse),
    newlyRevealedTileIds = newlyRevealedTileIds.map(Uuid::parse),
    remainingWallTileCount = remainingWallTileCount,
    reservedWallTileIds = reservedWallTileIds.map(Uuid::parse),
    scoresByPlayerId = scoresByPlayerId.mapKeys { Uuid.parse(it.key) },
    nextPlayerId = Uuid.parse(nextPlayerId),
)
