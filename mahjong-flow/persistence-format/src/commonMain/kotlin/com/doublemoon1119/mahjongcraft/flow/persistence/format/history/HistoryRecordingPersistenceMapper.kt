package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTransfer
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinningHand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

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
    /**
     * 將單筆待寫事件編碼；失敗時交由呼叫端保留並重試，絕不略過後確認。
     *
     * @param event 欲寫入持久化儲存端的單筆權威事件。
     * @return 可持久化的單筆歷史事件 DTO。
     */
    fun encodePendingEvent(event: HistoryOutboxEvent): HistoryOutboxEventPersistenceDto = encodeEvent(event)

    /**
     * 將單筆資料庫事件解回權威事實；失敗時交由呼叫端標記缺口，絕不略過後封存。
     *
     * @param event 從持久化儲存端讀取的單筆歷史事件 DTO。
     * @return 還原的權威歷史事件。
     */
    fun decodePendingEvent(event: HistoryOutboxEventPersistenceDto): HistoryOutboxEvent = decodeEvent(event)

    /**
     * 將歷史記錄狀態編成權威存檔 DTO；待寫事件編碼失敗時記錄序號缺口並略過該事件。
     *
     * 隔離轉移中已接受的事件不採用略過處理；這些事件的編碼錯誤會向呼叫端傳遞。
     *
     * @param state 待保存的歷史記錄狀態。
     * @return 編碼後的歷史記錄狀態，包含事件編碼失敗的序號缺口。
     */
    fun encode(state: HistoryRecordingState): HistoryRecordingPersistenceDto {
        val missing = state.firstMissingSequenceByMatchId.toMutableMap()
        val encoded = state.pendingEvents.mapNotNull { event ->
            runCatching { encodeEvent(event) }.getOrElse {
                missing[event.matchId] = minOf(missing[event.matchId] ?: event.sequence, event.sequence)
                null
            }
        }
        return HistoryRecordingPersistenceDto(
            formatVersion = HistoryRecordingPersistenceDto.FORMAT_VERSION,
            nextSequenceByMatchId = state.nextSequenceByMatchId.mapKeys { it.key.toString() },
            pendingEvents = encoded,
            firstMissingSequenceByMatchId = missing.mapKeys { it.key.toString() },
            decisionsByMatchId = state.decisionsByMatchId.mapKeys { it.key.toString() }.mapValues { it.value.name },
            terminalByMatchId = state.terminalByMatchId.mapKeys { it.key.toString() }.mapValues { (_, terminal) ->
                HistoryRecordingTerminalPersistenceDto(
                    terminal.endedAtEpochMillis,
                    terminal.completed,
                    terminal.venueId.toString(),
                )
            },
            transfersByMatchId = state.transfersByMatchId.mapKeys { it.key.toString() }.mapValues { (_, transfer) ->
                HistoryRecordingTransferPersistenceDto(transfer.venueId.toString(), transfer.lastAcceptedBatch.map(::encodeEvent), transfer.matchCompleted)
            },
        )
    }

    /**
     * 還原歷史記錄狀態；待寫事件內容解碼失敗時記錄序號缺口並略過該事件。
     *
     * 此處不會略過無效的場次識別碼或其他狀態欄位；這些資料的解碼錯誤會向呼叫端傳遞。
     *
     * @param dto 待還原的歷史記錄狀態 DTO。
     * @return 還原的歷史記錄狀態，包含無法解碼事件的序號缺口。
     */
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
                    Uuid.parse(terminal.venueId),
                )
            },
            transfersByMatchId = dto.transfersByMatchId.mapKeys { Uuid.parse(it.key) }.mapValues { (_, transfer) ->
                HistoryRecordingTransfer(Uuid.parse(transfer.venueId), transfer.lastAcceptedBatch.map(::decodeEvent), transfer.matchCompleted)
            },
        )
    }

    /**
     * 將單筆 domain 事件轉成持久化 DTO。
     *
     * @param event 要保存的權威歷史事件。
     * @return 保存事件封套與事實的 DTO。
     */
    private fun encodeEvent(event: HistoryOutboxEvent) = HistoryOutboxEventPersistenceDto(
        matchId = event.matchId.toString(),
        venueId = event.venueId.toString(),
        roundNumber = event.roundNumber,
        sequence = event.sequence,
        transactionFirstSequence = event.transactionFirstSequence,
        occurredAtEpochMillis = event.occurredAtEpochMillis,
        actorPlayerId = event.actorPlayerId?.toString(),
        fact = encodeFact(event.fact),
    )

    /**
     * 將單筆持久化事件還原成 domain 事件。
     *
     * @param dto 已保存的事件封套與事實。
     * @return 還原後的權威歷史事件。
     */
    private fun decodeEvent(dto: HistoryOutboxEventPersistenceDto) = HistoryOutboxEvent(
        matchId = Uuid.parse(dto.matchId),
        venueId = Uuid.parse(dto.venueId),
        roundNumber = dto.roundNumber,
        sequence = dto.sequence,
        transactionFirstSequence = dto.transactionFirstSequence,
        occurredAtEpochMillis = dto.occurredAtEpochMillis,
        actorPlayerId = dto.actorPlayerId?.let(Uuid::parse),
        fact = decodeFact(dto.fact),
    )

    /**
     * 將 domain 歷史事實轉成帶明確種類的持久化 DTO。
     *
     * @param fact 要保存的歷史事實。
     * @return 具有明確事實種類的存檔 DTO。
     */
    private fun encodeFact(fact: HistoryFact): HistoryFactPersistenceDto = when (fact) {
        is HistoryFact.MatchStarted -> HistoryFactPersistenceDto.MatchStarted(
            encodeTable(fact.tableState),
            fact.flowConfig.toPersistenceDto(),
            fact.aiPlayerStrategyKeys.mapKeys { (playerId, _) -> playerId.toString() },
        )
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
        is HistoryFact.RoundCompleted -> HistoryFactPersistenceDto.RoundCompleted(
            fact.summary.toPersistenceDto(),
        )
        is HistoryFact.WinSettled -> HistoryFactPersistenceDto.WinSettled(
            fact.outcomeId,
            fact.winDetails.map(HistoryWinDetails::toPersistenceDto),
            fact.responsiblePlayerIds.map(Uuid::toString),
        )
        is HistoryFact.MatchCompleted -> HistoryFactPersistenceDto.MatchCompleted(
            fact.reasonId,
            fact.finalScoresByPlayerId.mapKeys { it.key.toString() },
        )
        is HistoryFact.MatchAborted -> HistoryFactPersistenceDto.MatchAborted(fact.reasonId)
        is HistoryFact.WinContinuationResolved -> HistoryFactPersistenceDto.WinContinuationResolved(
            fact.directive.toPersistenceDto(),
        )
        is HistoryFact.RuleEffectResolved -> HistoryFactPersistenceDto.RuleEffectResolved(
            fact.reasonId,
            fact.roundCompletion?.toPersistenceDto(),
            fact.winDetails.map(HistoryWinDetails::toPersistenceDto),
        )
        HistoryFact.ReturnedToRoom -> HistoryFactPersistenceDto.ReturnedToRoom
        is HistoryFact.TableChanged -> HistoryFactPersistenceDto.TableChanged(
            HistoryTableChangeMapper(registries, json).encode(fact.result),
        )
    }

    /**
     * 將持久化歷史事實還原成 domain 事實。
     *
     * @param dto 已保存的歷史事實。
     * @return 還原後的歷史事實。
     */
    private fun decodeFact(dto: HistoryFactPersistenceDto): HistoryFact = when (dto) {
        is HistoryFactPersistenceDto.MatchStarted -> HistoryFact.MatchStarted(
            decodeTable(dto.state),
            dto.flowConfig.toDomain(),
            dto.aiPlayerStrategyKeys.mapKeys { (playerId, _) -> Uuid.parse(playerId) },
        )
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
        is HistoryFactPersistenceDto.RoundCompleted -> HistoryFact.RoundCompleted(
            dto.summary.toDomain(),
        )
        is HistoryFactPersistenceDto.WinSettled -> HistoryFact.WinSettled(
            dto.outcomeId,
            dto.winDetails.map(HistoryWinDetailsPersistenceDto::toDomain),
            dto.responsiblePlayerIds.map(Uuid::parse),
        )
        is HistoryFactPersistenceDto.MatchCompleted -> HistoryFact.MatchCompleted(
            dto.reasonId,
            dto.finalScoresByPlayerId.mapKeys { Uuid.parse(it.key) },
        )
        is HistoryFactPersistenceDto.MatchAborted -> HistoryFact.MatchAborted(dto.reasonId)
        is HistoryFactPersistenceDto.WinContinuationResolved -> HistoryFact.WinContinuationResolved(
            dto.directive.toDomain(),
        )
        is HistoryFactPersistenceDto.RuleEffectResolved -> HistoryFact.RuleEffectResolved(
            dto.reasonId,
            dto.roundCompletion?.toDomain(),
            dto.winDetails.map(HistoryWinDetailsPersistenceDto::toDomain),
        )
        HistoryFactPersistenceDto.ReturnedToRoom -> HistoryFact.ReturnedToRoom
        is HistoryFactPersistenceDto.TableChanged -> HistoryFact.TableChanged(
            HistoryTableChangeMapper(registries, json).decode(dto.result),
        )
    }

    /**
     * 使用 registry 將桌況編成持久化 DTO。
     *
     * @param state 要保存的完整桌況。
     * @return 編碼後的完整桌況 DTO。
     */
    private fun encodeTable(state: TableState) = state.toPersistenceDto(
        registries.ruleConfigs,
        registries.discardPiles,
        registries.playerRuleStates,
        registries.dynamicRuleStates,
        registries.exhaustiveDrawReasons,
        registries.extensionGameActions,
        json,
    )

    /**
     * 使用 registry 將桌況 DTO 還原成 domain 桌況。
     *
     * @param dto 已保存的完整桌況。
     * @return 還原後的完整桌況。
     */
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

/**
 * 將已確定的和牌明細映射為保存格式。
 *
 * @return 不含任意規則領域物件的明確 DTO。
 */
private fun HistoryWinDetails.toPersistenceDto() = HistoryWinDetailsPersistenceDto(
    playerId = playerId.toString(),
    detailFields = detailFields.map { field ->
        HistoryWinDetailFieldPersistenceDto(field.id, field.value.toPersistenceDto())
    },
    hand = hand?.let { HistoryWinningHandPersistenceDto(it.standingTileIds.map(Uuid::toString), it.winningTileId?.toString()) },
)

/**
 * 將保存的和牌明細還原為記錄契約。
 *
 * @return 已解析玩家 UUID 與明細值的記錄。
 */
private fun HistoryWinDetailsPersistenceDto.toDomain() = HistoryWinDetails(
    playerId = Uuid.parse(playerId),
    detailFields = detailFields.map { field ->
        WinSettlementDetailField(field.id, field.value.toDomain())
    },
    hand = hand?.let { HistoryWinningHand(it.standingTileIds.map(Uuid::parse), it.winningTileId?.let(Uuid::parse)) },
)

/**
 * 將規則中立明細值映射為明確保存種類。
 *
 * @return 數值、牌 UUID 或條目 DTO。
 */
private fun WinSettlementDetailValue.toPersistenceDto(): HistoryWinDetailValuePersistenceDto = when (this) {
    is WinSettlementDetailValue.Quantities -> HistoryWinDetailValuePersistenceDto.Quantities(quantities.map { it.toPersistenceDto() })
    is WinSettlementDetailValue.Tiles -> HistoryWinDetailValuePersistenceDto.Tiles(tileIds.map(Uuid::toString))
    is WinSettlementDetailValue.Entries -> HistoryWinDetailValuePersistenceDto.Entries(
        entries.map { entry -> HistoryWinDetailValuePersistenceDto.Entries.Entry(entry.id, entry.quantity?.toPersistenceDto()) },
    )
}

/**
 * 將保存的明細值還原，不執行規則計算。
 *
 * @return 原有順序的明細值。
 */
private fun HistoryWinDetailValuePersistenceDto.toDomain(): WinSettlementDetailValue = when (this) {
    is HistoryWinDetailValuePersistenceDto.Quantities -> WinSettlementDetailValue.Quantities(quantities.map { it.toDomain() })
    is HistoryWinDetailValuePersistenceDto.Tiles -> WinSettlementDetailValue.Tiles(tileIds.map(Uuid::parse))
    is HistoryWinDetailValuePersistenceDto.Entries -> WinSettlementDetailValue.Entries(
        entries.map { entry -> WinSettlementDetailEntry(entry.id, entry.quantity?.toDomain()) },
    )
}

/** 將有單位數值映射為保存格式。 */
private fun WinSettlementQuantity.toPersistenceDto() = HistoryWinDetailQuantityPersistenceDto(unitId, amount)

/** 將保存的有單位數值還原。 */
private fun HistoryWinDetailQuantityPersistenceDto.toDomain() = WinSettlementQuantity(unitId, amount)

/** 將胡牌後續決策轉成持久化 DTO。 */
private fun WinRoundDirective.toPersistenceDto(): WinRoundDirectivePersistenceDto = when (this) {
    WinRoundDirective.EndRound -> WinRoundDirectivePersistenceDto.EndRound
    is WinRoundDirective.ContinueRound -> WinRoundDirectivePersistenceDto.ContinueRound(
        newlyFinishedPlayerIds.map(Uuid::toString).toSet(),
        nextPlayerId.toString(),
        settlementDetail.name,
    )
}

/** 將胡牌後續決策 DTO 還原成 domain 決策。 */
private fun WinRoundDirectivePersistenceDto.toDomain(): WinRoundDirective = when (this) {
    WinRoundDirectivePersistenceDto.EndRound -> WinRoundDirective.EndRound
    is WinRoundDirectivePersistenceDto.ContinueRound -> WinRoundDirective.ContinueRound(
        newlyFinishedPlayerIds.map(Uuid::parse).toSet(),
        Uuid.parse(nextPlayerId),
        ContinuingWinSettlementDetail.valueOf(settlementDetail),
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
