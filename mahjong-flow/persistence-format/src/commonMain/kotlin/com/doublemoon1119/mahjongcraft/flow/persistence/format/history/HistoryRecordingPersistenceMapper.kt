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
import com.doublemoon1119.mahjongcraft.flow.common.game.model.TileReplacementRemoval
import com.doublemoon1119.mahjongcraft.flow.common.game.model.TileReplacementRound
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 使用已凍結 registry 在 domain 事件與持久化 DTO 間轉換完整 outbox。
 *
 * @property registries 解碼規則、動作與桌況所需的持久化 registry。
 */
class HistoryRecordingPersistenceMapper(
    private val registries: PersistenceRegistries,
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
                    endedAtEpochMillis = terminal.endedAtEpochMillis,
                    completed = terminal.completed,
                    venueId = terminal.venueId.toString(),
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
                    endedAtEpochMillis = terminal.endedAtEpochMillis,
                    completed = terminal.completed,
                    venueId = Uuid.parse(terminal.venueId),
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
            state = encodeTable(fact.tableState),
            flowConfig = fact.flowConfig.toPersistenceDto(),
            aiPlayerStrategyKeys = fact.aiPlayerStrategyKeys.mapKeys { (playerId, _) -> playerId.toString() },
        )
        is HistoryFact.RoundStarted -> HistoryFactPersistenceDto.RoundStarted(encodeTable(fact.tableState))
        is HistoryFact.DiceRolled -> HistoryFactPersistenceDto.DiceRolled(fact.roll.toPersistenceDto())
        is HistoryFact.RuleDecisionStarted -> HistoryFactPersistenceDto.RuleDecisionStarted(fact.timing.toPersistenceDto(), fact.stepId, fact.stepIndex)
        is HistoryFact.RuleDecisionSubmitted -> HistoryFactPersistenceDto.RuleDecisionSubmitted(
            timing = fact.timing.toPersistenceDto(),
            stepId = fact.stepId,
            stepIndex = fact.stepIndex,
            submission = fact.submission.toPersistenceDto(),
            nextStepId = fact.nextStepId,
            passed = fact.passed,
        )
        is HistoryFact.RuleDecisionAutomaticallyResolved -> HistoryFactPersistenceDto.RuleDecisionAutomaticallyResolved(
            timing = fact.timing.toPersistenceDto(),
            stepId = fact.stepId,
            stepIndex = fact.stepIndex,
            nextStepId = fact.nextStepId,
        )
        is HistoryFact.TileReplacementRoundResolved -> HistoryFactPersistenceDto.TileReplacementRoundResolved(
            timing = fact.timing.toPersistenceDto(),
            round = fact.round.round,
            removals = fact.round.removals.map { removal ->
                HistoryFactPersistenceDto.TileReplacementRoundResolved.Removal(removal.playerId.toString(), removal.tileIds.map(Uuid::toString))
            },
        )
        is HistoryFact.ActionAccepted -> HistoryFactPersistenceDto.ActionAccepted(
            action = fact.action.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions),
            result = fact.result.toPersistenceDto(),
        )
        is HistoryFact.ActionScored -> HistoryFactPersistenceDto.ActionScored(
            action = fact.action.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions),
            scoreDeltasByPlayerId = fact.scoreDeltas.mapKeys { it.key.toString() },
            potDeposit = fact.potDeposit,
        )
        is HistoryFact.ReactionResolved -> HistoryFactPersistenceDto.ReactionResolved(
            resolvedAction = fact.resolvedAction?.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions),
            actorPlayerId = fact.actorPlayerId?.toString(),
        )
        is HistoryFact.RoundCompleted -> HistoryFactPersistenceDto.RoundCompleted(
            summary = fact.summary.toPersistenceDto(),
        )
        is HistoryFact.WinSettled -> HistoryFactPersistenceDto.WinSettled(
            outcomeId = fact.outcomeId,
            winDetails = fact.winDetails.map { it.toPersistenceDto(registries.componentViews) },
            responsiblePlayerIds = fact.responsiblePlayerIds.map(Uuid::toString),
        )
        is HistoryFact.MatchCompleted -> HistoryFactPersistenceDto.MatchCompleted(
            reasonId = fact.reasonId,
            finalScoresByPlayerId = fact.finalScoresByPlayerId.mapKeys { it.key.toString() },
        )
        is HistoryFact.MatchAborted -> HistoryFactPersistenceDto.MatchAborted(fact.reasonId)
        is HistoryFact.WinContinuationResolved -> HistoryFactPersistenceDto.WinContinuationResolved(
            directive = fact.directive.toPersistenceDto(),
        )
        is HistoryFact.RuleEffectResolved -> HistoryFactPersistenceDto.RuleEffectResolved(
            reasonId = fact.reasonId,
            roundCompletion = fact.roundCompletion?.toPersistenceDto(),
            winDetails = fact.winDetails.map { it.toPersistenceDto(registries.componentViews) },
        )
        HistoryFact.ReturnedToRoom -> HistoryFactPersistenceDto.ReturnedToRoom
        is HistoryFact.TableChanged -> HistoryFactPersistenceDto.TableChanged(
            result = HistoryTableChangeMapper(registries).encode(fact.result),
        )
        is HistoryFact.TilePresentationChanged -> HistoryFactPersistenceDto.TilePresentationChanged(
            presentations = fact.presentations.entries.sortedBy { it.key.toString() }.associate { (tileId, presentation) ->
                tileId.toString() to presentation.toPersistenceDto(registries.componentViews)
            },
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
            tableState = decodeTable(dto.state),
            flowConfig = dto.flowConfig.toDomain(),
            aiPlayerStrategyKeys = dto.aiPlayerStrategyKeys.mapKeys { (playerId, _) -> Uuid.parse(playerId) },
        )
        is HistoryFactPersistenceDto.RoundStarted -> HistoryFact.RoundStarted(decodeTable(dto.state))
        is HistoryFactPersistenceDto.DiceRolled -> HistoryFact.DiceRolled(dto.roll.toDomain())
        is HistoryFactPersistenceDto.RuleDecisionStarted -> HistoryFact.RuleDecisionStarted(dto.timing.toDomain(), dto.stepId, dto.stepIndex)
        is HistoryFactPersistenceDto.RuleDecisionSubmitted -> HistoryFact.RuleDecisionSubmitted(
            timing = dto.timing.toDomain(),
            stepId = dto.stepId,
            stepIndex = dto.stepIndex,
            submission = dto.submission.toDomain(),
            nextStepId = dto.nextStepId,
            passed = dto.passed,
        )
        is HistoryFactPersistenceDto.RuleDecisionAutomaticallyResolved -> HistoryFact.RuleDecisionAutomaticallyResolved(
            timing = dto.timing.toDomain(),
            stepId = dto.stepId,
            stepIndex = dto.stepIndex,
            nextStepId = dto.nextStepId,
        )
        is HistoryFactPersistenceDto.TileReplacementRoundResolved -> HistoryFact.TileReplacementRoundResolved(
            timing = dto.timing.toDomain(),
            round = TileReplacementRound(
                round = dto.round,
                removals = dto.removals.map { removal -> TileReplacementRemoval(Uuid.parse(removal.playerId), removal.tileIds.map(Uuid::parse)) },
            ),
        )
        is HistoryFactPersistenceDto.ActionAccepted -> HistoryFact.ActionAccepted(
            action = dto.action.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions),
            result = dto.result.toDomain(),
        )
        is HistoryFactPersistenceDto.ActionScored -> HistoryFact.ActionScored(
            action = dto.action.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions),
            scoreDeltas = dto.scoreDeltasByPlayerId.mapKeys { Uuid.parse(it.key) },
            potDeposit = dto.potDeposit,
        )
        is HistoryFactPersistenceDto.ReactionResolved -> HistoryFact.ReactionResolved(
            resolvedAction = dto.resolvedAction?.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions),
            actorPlayerId = dto.actorPlayerId?.let(Uuid::parse),
        )
        is HistoryFactPersistenceDto.RoundCompleted -> HistoryFact.RoundCompleted(
            summary = dto.summary.toDomain(),
        )
        is HistoryFactPersistenceDto.WinSettled -> HistoryFact.WinSettled(
            outcomeId = dto.outcomeId,
            winDetails = dto.winDetails.map { it.toDomain(registries.componentViews) },
            responsiblePlayerIds = dto.responsiblePlayerIds.map(Uuid::parse),
        )
        is HistoryFactPersistenceDto.MatchCompleted -> HistoryFact.MatchCompleted(
            reasonId = dto.reasonId,
            finalScoresByPlayerId = dto.finalScoresByPlayerId.mapKeys { Uuid.parse(it.key) },
        )
        is HistoryFactPersistenceDto.MatchAborted -> HistoryFact.MatchAborted(dto.reasonId)
        is HistoryFactPersistenceDto.WinContinuationResolved -> HistoryFact.WinContinuationResolved(
            directive = dto.directive.toDomain(),
        )
        is HistoryFactPersistenceDto.RuleEffectResolved -> HistoryFact.RuleEffectResolved(
            reasonId = dto.reasonId,
            roundCompletion = dto.roundCompletion?.toDomain(),
            winDetails = dto.winDetails.map { it.toDomain(registries.componentViews) },
        )
        HistoryFactPersistenceDto.ReturnedToRoom -> HistoryFact.ReturnedToRoom
        is HistoryFactPersistenceDto.TableChanged -> HistoryFact.TableChanged(
            result = HistoryTableChangeMapper(registries).decode(dto.result),
        )
        is HistoryFactPersistenceDto.TilePresentationChanged -> HistoryFact.TilePresentationChanged(
            presentations = dto.presentations.entries.associate { (tileId, presentation) -> Uuid.parse(tileId) to presentation.toDomain(registries.componentViews) },
        )
    }

    /**
     * 使用 registry 將桌況編成持久化 DTO。
     *
     * @param state 要保存的完整桌況。
     * @return 編碼後的完整桌況 DTO。
     */
    private fun encodeTable(state: TableState) = state.toPersistenceDto(
        ruleConfigRegistry = registries.ruleConfigs,
        discardPileRegistry = registries.discardPiles,
        exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
        extensionGameActionRegistry = registries.extensionGameActions,
        componentRegistry = registries.components,
    )

    /**
     * 使用 registry 將桌況 DTO 還原成 domain 桌況。
     *
     * @param dto 已保存的完整桌況。
     * @return 還原後的完整桌況。
     */
    private fun decodeTable(dto: TableStatePersistenceDto) = dto.toDomain(
        ruleConfigRegistry = registries.ruleConfigs,
        discardPileRegistry = registries.discardPiles,
        exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
        extensionGameActionRegistry = registries.extensionGameActions,
        componentRegistry = registries.components,
    )
}

/**
 * 將已確定的和牌明細映射為保存格式。
 *
 * @param componentViews 牌面特徵的保存轉換。
 * @return 不含任意規則領域物件的明確 DTO。
 */
private fun HistoryWinDetails.toPersistenceDto(componentViews: ComponentViewPersistenceRegistry) = HistoryWinDetailsPersistenceDto(
    playerId = playerId.toString(),
    detailFields = detailFields.map { field ->
        HistoryWinDetailFieldPersistenceDto(field.id, field.value.toPersistenceDto(componentViews))
    },
    hand = hand?.let { HistoryWinningHandPersistenceDto(it.standingTileIds.map(Uuid::toString), it.winningTileId?.toString()) },
)

/**
 * 將保存的和牌明細還原為記錄契約。
 *
 * @param componentViews 牌面特徵的保存轉換。
 * @return 已解析玩家 UUID 與明細值的記錄。
 */
private fun HistoryWinDetailsPersistenceDto.toDomain(componentViews: ComponentViewPersistenceRegistry) = HistoryWinDetails(
    playerId = Uuid.parse(playerId),
    detailFields = detailFields.map { field ->
        WinSettlementDetailField(field.id, field.value.toDomain(componentViews))
    },
    hand = hand?.let { HistoryWinningHand(it.standingTileIds.map(Uuid::parse), it.winningTileId?.let(Uuid::parse)) },
)

/**
 * 將規則中立明細值映射為明確保存種類。
 *
 * @param componentViews 牌面特徵的保存轉換。
 * @return 數值、已裁切牌面或條目 DTO。
 */
private fun WinSettlementDetailValue.toPersistenceDto(componentViews: ComponentViewPersistenceRegistry): HistoryWinDetailValuePersistenceDto = when (this) {
    is WinSettlementDetailValue.Quantities -> HistoryWinDetailValuePersistenceDto.Quantities(quantities.map { it.toPersistenceDto() })
    is WinSettlementDetailValue.TileFaces -> HistoryWinDetailValuePersistenceDto.TileFaces(faces.map { it.toPersistenceDto(componentViews) })
    is WinSettlementDetailValue.Entries -> HistoryWinDetailValuePersistenceDto.Entries(
        entries = entries.map { entry ->
            HistoryWinDetailValuePersistenceDto.Entries.Entry(
                id = entry.id,
                quantity = entry.quantity?.toPersistenceDto(),
                nameQuantity = entry.nameQuantity?.toPersistenceDto(),
                appliesToSomePayersOnly = entry.appliesToSomePayersOnly,
            )
        },
    )
}

/**
 * 將保存的明細值還原，不執行規則計算。
 *
 * @param componentViews 牌面特徵的保存轉換。
 * @return 原有順序的明細值。
 */
private fun HistoryWinDetailValuePersistenceDto.toDomain(componentViews: ComponentViewPersistenceRegistry): WinSettlementDetailValue = when (this) {
    is HistoryWinDetailValuePersistenceDto.Quantities -> WinSettlementDetailValue.Quantities(quantities.map { it.toDomain() })
    is HistoryWinDetailValuePersistenceDto.TileFaces -> WinSettlementDetailValue.TileFaces(faces.map { it.toDomain(componentViews) })
    is HistoryWinDetailValuePersistenceDto.Entries -> WinSettlementDetailValue.Entries(
        entries = entries.map { entry ->
            WinSettlementDetailEntry(
                id = entry.id,
                quantity = entry.quantity?.toDomain(),
                nameQuantity = entry.nameQuantity?.toDomain(),
                appliesToSomePayersOnly = entry.appliesToSomePayersOnly,
            )
        },
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
        newlyFinishedPlayerIds = newlyFinishedPlayerIds.map(Uuid::toString).toSet(),
        nextPlayerId = nextPlayerId.toString(),
        settlementDetail = settlementDetail.name,
    )
}

/** 將胡牌後續決策 DTO 還原成 domain 決策。 */
private fun WinRoundDirectivePersistenceDto.toDomain(): WinRoundDirective = when (this) {
    WinRoundDirectivePersistenceDto.EndRound -> WinRoundDirective.EndRound
    is WinRoundDirectivePersistenceDto.ContinueRound -> WinRoundDirective.ContinueRound(
        newlyFinishedPlayerIds = newlyFinishedPlayerIds.map(Uuid::parse).toSet(),
        nextPlayerId = Uuid.parse(nextPlayerId),
        settlementDetail = ContinuingWinSettlementDetail.valueOf(settlementDetail),
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
