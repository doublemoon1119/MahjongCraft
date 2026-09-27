package com.doublemoon1119.mahjongcraft.flow.persistence.dto.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryCaptureState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementMode
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.config.GameFlowConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.config.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.GameActionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.RoundCompletionSummaryPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.RoundPreparationSubmissionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** 權威歷史待寫事件的持久化快照；此格式與 SQLite schema 分開演進。 */
@Serializable
data class HistoryCapturePersistenceDto(
    /** 此快照格式的版本號。 */
    val formatVersion: Int = 1,
    /** 各對局下一個可分配的事件序號，鍵為對局 UUID 字串。 */
    val nextSequenceByMatchId: Map<String, Long> = emptyMap(),
    /** 尚未交給歷史儲存端的事件；各場次的事件依序號排序。 */
    val pendingEvents: List<HistoryOutboxEventPersistenceDto> = emptyList(),
    /** 各對局第一個無法完整還原的事件序號，鍵為對局 UUID 字串。 */
    val firstMissingSequenceByMatchId: Map<String, Long> = emptyMap(),
) {
    init {
        require(formatVersion == 1) { "Unsupported history outbox format $formatVersion" }
    }
}

/** 一筆已指派穩定鍵、可重試寫入的歷史事件。 */
@Serializable
data class HistoryOutboxEventPersistenceDto(
    /** 事件所屬對局的 UUID 字串。 */
    val matchId: String,
    /** 事件所屬牌桌的 UUID 字串。 */
    val tableId: String,
    /** 事件發生時所在的局數。 */
    val roundNumber: Int,
    /** 在同一對局內單調遞增的事件序號。 */
    val sequence: Long,
    /** 事件發生時間的 Unix epoch 毫秒值。 */
    val occurredAtEpochMillis: Long,
    /** 觸發事件的玩家 UUID 字串；系統事件為 null。 */
    val actorPlayerId: String?,
    /** 事件的具體歷史事實。 */
    val fact: HistoryFactPersistenceDto,
)

/** 帶明確序列化種類的歷史事實 DTO。 */
@Serializable
sealed interface HistoryFactPersistenceDto {
    /** 對局建立時的完整桌況與流程設定。 */
    @Serializable
    @SerialName("match_started")
    data class MatchStarted(
        /** 對局開始時的桌況。 */
        val state: TableStatePersistenceDto,
        /** 對局使用的流程設定。 */
        val flowConfig: GameFlowConfigPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 新局開始時的完整桌況。 */
    @Serializable
    @SerialName("round_started")
    data class RoundStarted(
        /** 新局開始時的桌況。 */
        val state: TableStatePersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 開局準備流程開始執行一個步驟。 */
    @Serializable
    @SerialName("round_preparation_started")
    data class RoundPreparationStarted(
        /** 準備步驟的穩定識別碼。 */
        val stepId: String,
        /** 準備步驟在流程中的索引。 */
        val stepIndex: Int,
    ) : HistoryFactPersistenceDto

    /** 玩家提交開局準備內容後的結果。 */
    @Serializable
    @SerialName("round_preparation_submitted")
    data class RoundPreparationSubmitted(
        /** 被提交的準備步驟識別碼。 */
        val stepId: String,
        /** 被提交的準備步驟索引。 */
        val stepIndex: Int,
        /** 玩家提交的準備內容。 */
        val submission: RoundPreparationSubmissionPersistenceDto,
        /** 提交後的桌況；尚未完成步驟時可能為 null。 */
        val resultingState: TableStatePersistenceDto?,
        /** 下一個準備步驟識別碼；沒有下一步時為 null。 */
        val nextStepId: String?,
    ) : HistoryFactPersistenceDto

    /** 系統自動完成開局準備步驟後的結果。 */
    @Serializable
    @SerialName("round_preparation_automatic_resolved")
    data class RoundPreparationAutomaticallyResolved(
        /** 被自動完成的準備步驟識別碼。 */
        val stepId: String,
        /** 被自動完成的準備步驟索引。 */
        val stepIndex: Int,
        /** 自動完成後的桌況。 */
        val resultingState: TableStatePersistenceDto,
        /** 下一個準備步驟識別碼；沒有下一步時為 null。 */
        val nextStepId: String?,
    ) : HistoryFactPersistenceDto

    /** 規則接受一個玩家動作後的結果。 */
    @Serializable
    @SerialName("action_accepted")
    data class ActionAccepted(
        /** 被接受的動作。 */
        val action: GameActionPersistenceDto,
        /** 動作套用後的權威結果。 */
        val result: HistoryActionResultPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 多方反應結束後的實際結算結果。 */
    @Serializable
    @SerialName("reaction_resolved")
    data class ReactionResolved(
        /** 反應結算後實際成立的動作；沒有動作成立時為 null。 */
        val resolvedAction: GameActionPersistenceDto?,
        /** 單一得標玩家的 UUID 字串；全員過牌或多家和牌時為 null。 */
        val actorPlayerId: String?,
        /** 反應結算後的桌況。 */
        val resultingState: TableStatePersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 本局結束時的結算摘要。 */
    @Serializable
    @SerialName("round_completed")
    data class RoundCompleted(
        /** 本局結算摘要。 */
        val summary: RoundCompletionSummaryPersistenceDto,
    ) : HistoryFactPersistenceDto

    /** 整場對局結束時的原因與最終分數。 */
    @Serializable
    @SerialName("match_completed")
    data class MatchCompleted(
        /** 對局結束原因的 namespaced key。 */
        val reasonId: String,
        /** 各玩家最終分數，鍵為玩家 UUID 字串。 */
        val finalScoresByPlayerId: Map<String, Int>,
    ) : HistoryFactPersistenceDto

    /** 胡牌後續流程完成決策後的結果。 */
    @Serializable
    @SerialName("win_continuation_resolved")
    data class WinContinuationResolved(
        /** 胡牌後決定的本局後續。 */
        val directive: WinRoundDirectivePersistenceDto,
        /** 套用決策後的桌況；桌況未改變時為 null。 */
        val resultingState: TableStatePersistenceDto?,
    ) : HistoryFactPersistenceDto

    /** 規則效果完成後的桌況與可能的本局結算。 */
    @Serializable
    @SerialName("rule_effect_resolved")
    data class RuleEffectResolved(
        /** 規則效果的識別碼。 */
        val reasonId: String,
        /** 規則效果套用後的桌況。 */
        val resultingState: TableStatePersistenceDto,
        /** 規則效果同時完成本局時的結算摘要；否則為 null。 */
        val roundCompletion: RoundCompletionSummaryPersistenceDto?,
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

    /** 依指定結算模式繼續本局，並交由下一位玩家行動。 */
    @Serializable
    @SerialName("continue_round")
    data class ContinueRound(
        /** 因本次結算而完成的玩家 UUID 字串。 */
        val newlyFinishedPlayerIds: Set<String>,
        /** 下一個行動玩家的 UUID 字串。 */
        val nextPlayerId: String,
        /** 結算模式的列舉名稱。 */
        val settlementMode: String,
    ) : WinRoundDirectivePersistenceDto
}

/** 動作提交後的完整權威桌況及相關索引資料。 */
@Serializable
data class HistoryActionResultPersistenceDto(
    /** 動作完成後的桌況。 */
    val resultingState: TableStatePersistenceDto,
    /** 動作直接影響的牌 UUID 字串。 */
    val affectedTileIds: List<String>,
    /** 因本次動作新公開的牌 UUID 字串。 */
    val newlyRevealedTileIds: List<String>,
    /** 動作完成後活牌牆剩餘牌數。 */
    val remainingWallTileCount: Int,
    /** 動作完成後保留區中的牌 UUID 字串。 */
    val reservedWallTileIds: List<String>,
    /** 動作完成後各玩家分數。 */
    val scoresByPlayerId: Map<String, Int>,
    /** 下一個行動玩家的 UUID 字串。 */
    val nextPlayerId: String,
)

/** 使用已凍結 registry 在 domain 事件與持久化 DTO 間轉換完整 outbox。 */
class HistoryCapturePersistenceMapper(
    /** 解碼規則、動作與桌況所需的持久化 registry。 */
    private val registries: PersistenceRegistries,
    /** 用於擴充動作內容序列化的 JSON 設定。 */
    private val json: Json = Json,
) {
    /** 將待寫事件編成權威存檔 DTO；無法編碼的事件會記錄序號缺口並略過。 */
    fun encode(state: HistoryCaptureState): HistoryCapturePersistenceDto {
        val missing = state.firstMissingSequenceByMatchId.toMutableMap()
        val encoded = state.pendingEvents.mapNotNull { event ->
            runCatching { encodeEvent(event) }.getOrElse {
                missing[event.matchId] = minOf(missing[event.matchId] ?: event.sequence, event.sequence)
                null
            }
        }
        return HistoryCapturePersistenceDto(
            nextSequenceByMatchId = state.nextSequenceByMatchId.mapKeys { it.key.toString() },
            pendingEvents = encoded,
            firstMissingSequenceByMatchId = missing.mapKeys { it.key.toString() },
        )
    }

    /** 驗證並還原權威待寫事件；無法解碼的事件會記錄序號缺口並略過。 */
    fun decode(dto: HistoryCapturePersistenceDto): HistoryCaptureState {
        val missing = dto.firstMissingSequenceByMatchId.mapKeys { Uuid.parse(it.key) }.toMutableMap()
        val decoded = dto.pendingEvents.mapNotNull { event ->
            val matchId = Uuid.parse(event.matchId)
            runCatching { decodeEvent(event) }.getOrElse {
                missing[matchId] = minOf(missing[matchId] ?: event.sequence, event.sequence)
                null
            }
        }
        return HistoryCaptureState(
            nextSequenceByMatchId = dto.nextSequenceByMatchId.mapKeys { Uuid.parse(it.key) },
            pendingEvents = decoded,
            firstMissingSequenceByMatchId = missing,
        )
    }

    /** 將單筆 domain 事件轉成持久化 DTO。 */
    private fun encodeEvent(event: HistoryOutboxEvent) = HistoryOutboxEventPersistenceDto(
        matchId = event.matchId.toString(),
        tableId = event.tableId.toString(),
        roundNumber = event.roundNumber,
        sequence = event.sequence,
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
            fact.resultingTableState?.let(::encodeTable),
            fact.nextStepId,
        )
        is HistoryFact.RoundPreparationAutomaticallyResolved -> HistoryFactPersistenceDto.RoundPreparationAutomaticallyResolved(
            fact.stepId,
            fact.stepIndex,
            encodeTable(fact.resultingTableState),
            fact.nextStepId,
        )
        is HistoryFact.ActionAccepted -> HistoryFactPersistenceDto.ActionAccepted(
            fact.action.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            fact.result.toPersistenceDto(::encodeTable),
        )
        is HistoryFact.ReactionResolved -> HistoryFactPersistenceDto.ReactionResolved(
            fact.resolvedAction?.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            fact.actorPlayerId?.toString(),
            encodeTable(fact.resultingTableState),
        )
        is HistoryFact.RoundCompleted -> HistoryFactPersistenceDto.RoundCompleted(fact.summary.toPersistenceDto())
        is HistoryFact.MatchCompleted -> HistoryFactPersistenceDto.MatchCompleted(
            fact.reasonId,
            fact.finalScoresByPlayerId.mapKeys { it.key.toString() },
        )
        is HistoryFact.WinContinuationResolved -> HistoryFactPersistenceDto.WinContinuationResolved(
            fact.directive.toPersistenceDto(),
            fact.resultingTableState?.let(::encodeTable),
        )
        is HistoryFact.RuleEffectResolved -> HistoryFactPersistenceDto.RuleEffectResolved(
            fact.reasonId,
            encodeTable(fact.resultingTableState),
            fact.roundCompletion?.toPersistenceDto(),
        )
        HistoryFact.ReturnedToRoom -> HistoryFactPersistenceDto.ReturnedToRoom
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
            dto.resultingState?.let(::decodeTable),
            dto.nextStepId,
        )
        is HistoryFactPersistenceDto.RoundPreparationAutomaticallyResolved -> HistoryFact.RoundPreparationAutomaticallyResolved(
            dto.stepId,
            dto.stepIndex,
            decodeTable(dto.resultingState),
            dto.nextStepId,
        )
        is HistoryFactPersistenceDto.ActionAccepted -> HistoryFact.ActionAccepted(
            dto.action.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            dto.result.toDomain(::decodeTable),
        )
        is HistoryFactPersistenceDto.ReactionResolved -> HistoryFact.ReactionResolved(
            dto.resolvedAction?.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions, json),
            dto.actorPlayerId?.let(Uuid::parse),
            decodeTable(dto.resultingState),
        )
        is HistoryFactPersistenceDto.RoundCompleted -> HistoryFact.RoundCompleted(dto.summary.toDomain())
        is HistoryFactPersistenceDto.MatchCompleted -> HistoryFact.MatchCompleted(
            dto.reasonId,
            dto.finalScoresByPlayerId.mapKeys { Uuid.parse(it.key) },
        )
        is HistoryFactPersistenceDto.WinContinuationResolved -> HistoryFact.WinContinuationResolved(
            dto.directive.toDomain(),
            dto.resultingState?.let(::decodeTable),
        )
        is HistoryFactPersistenceDto.RuleEffectResolved -> HistoryFact.RuleEffectResolved(
            dto.reasonId,
            decodeTable(dto.resultingState),
            dto.roundCompletion?.toDomain(),
        )
        HistoryFactPersistenceDto.ReturnedToRoom -> HistoryFact.ReturnedToRoom
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
private fun HistoryActionResult.toPersistenceDto(
    encodeTable: (TableState) -> TableStatePersistenceDto,
) = HistoryActionResultPersistenceDto(
    resultingState = encodeTable(resultingTableState),
    affectedTileIds = affectedTileIds.map(Uuid::toString),
    newlyRevealedTileIds = newlyRevealedTileIds.map(Uuid::toString),
    remainingWallTileCount = remainingWallTileCount,
    reservedWallTileIds = reservedWallTileIds.map(Uuid::toString),
    scoresByPlayerId = scoresByPlayerId.mapKeys { it.key.toString() },
    nextPlayerId = nextPlayerId.toString(),
)

/** 將動作結果 DTO 還原成 domain 結果。 */
private fun HistoryActionResultPersistenceDto.toDomain(
    decodeTable: (TableStatePersistenceDto) -> TableState,
) = HistoryActionResult(
    resultingTableState = decodeTable(resultingState),
    affectedTileIds = affectedTileIds.map(Uuid::parse),
    newlyRevealedTileIds = newlyRevealedTileIds.map(Uuid::parse),
    remainingWallTileCount = remainingWallTileCount,
    reservedWallTileIds = reservedWallTileIds.map(Uuid::parse),
    scoresByPlayerId = scoresByPlayerId.mapKeys { Uuid.parse(it.key) },
    nextPlayerId = Uuid.parse(nextPlayerId),
)
