package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRoundEventsRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRoundStateRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayMeld
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayTransaction
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayWinningHand
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryWinDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryWinDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryWinnerDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import kotlinx.serialization.json.Json

/** 將單局事件請求 DTO 映射為 Flow 要求。
 * @return 可交給 Flow use case 驗證的查詢要求。
 */
fun HistoryRoundEventsRequestDto.toDomain(): HistoryRoundEventsRequest = HistoryRoundEventsRequest(
    matchId = matchId.toHistoryUuid(),
    scope = scope.toDomain(),
    roundNumber = roundNumber,
    startTransactionIndex = startTransactionIndex,
    limit = limit,
)

/** 將單局桌況請求 DTO 映射為 Flow 要求。
 * @return 可交給 Flow use case 驗證的查詢要求。
 */
fun HistoryRoundStateRequestDto.toDomain(): HistoryRoundStateRequest = HistoryRoundStateRequest(
    matchId = matchId.toHistoryUuid(),
    scope = scope.toDomain(),
    roundNumber = roundNumber,
    position = position.toDomain(),
)

/** 將網路局內位置映射為 Flow 位置。 */
fun HistoryRoundPositionDto.toDomain(): HistoryRoundPosition = when (this) {
    HistoryRoundPositionDto.Initial -> HistoryRoundPosition.Initial
    is HistoryRoundPositionDto.AfterTransaction -> HistoryRoundPosition.AfterTransaction(index)
}

/** 將 Flow 局內位置映射為網路位置。 */
fun HistoryRoundPosition.toDto(): HistoryRoundPositionDto = when (this) {
    HistoryRoundPosition.Initial -> HistoryRoundPositionDto.Initial
    is HistoryRoundPosition.AfterTransaction -> HistoryRoundPositionDto.AfterTransaction(index)
}

/** 將 Flow 單局事件映射為網路 DTO。
 * @return 可公開傳輸的事件 DTO。
 */
fun HistoryRoundEvents.toDto(): HistoryRoundEventsDto = HistoryRoundEventsDto(
    identity = identity.toDto(),
    roundNumber = roundNumber,
    transactions = transactions.map { it.toDto() },
    nextTransactionIndex = nextTransactionIndex,
    tileCatalog = tileCatalog.tiles.map { it.toDto() },
)

/** 將 Replay 對局識別資料映射為網路 DTO。
 * @return 不含私有狀態的對局識別 DTO。
 */
private fun HistoryReplayIdentity.toDto(): HistoryReplayIdentityDto = HistoryReplayIdentityDto(
    matchId = matchId.toString(),
    tableId = tableId.toString(),
    players = players.map { HistoryReplayPlayerIdentityDto(it.initialSeatIndex, it.playerId.toString(), it.aiStrategyKey) },
)

/** 將 Replay 交易映射為網路 DTO。
 * @return 可序列化的交易 DTO。
 */
private fun HistoryReplayTransaction.toDto(): HistoryReplayTransactionDto = HistoryReplayTransactionDto(
    index = index,
    occurredAtEpochMillis = occurredAtEpochMillis,
    isOpening = isOpening,
    declaredTileCountAfter = declaredTileCountAfter,
    facts = facts.map { it.toDto() },
)

/** 將 Replay 語意事實映射為不含原始 payload 的網路 DTO。
 * @return 可公開傳輸的事實 DTO。
 */
private fun HistoryReplayFact.toDto(): HistoryReplayFactDto = when (this) {
    is HistoryReplayFact.KnownAction -> HistoryReplayFactDto.KnownAction(typeKey, actorSeat, actionType, directTiles.map { it.tileIndex }, revealedTiles.map { it.tileIndex }, extensionTypeId)
    is HistoryReplayFact.Reaction -> HistoryReplayFactDto.Reaction(typeKey, actionType, resolvedActorSeat)
    is HistoryReplayFact.Preparation -> HistoryReplayFactDto.Preparation(typeKey, stepId, stepIndex, nextStepId)
    is HistoryReplayFact.Completion -> HistoryReplayFactDto.Completion(typeKey, outcome?.toDto())
    is HistoryReplayFact.RuleEffect -> HistoryReplayFactDto.RuleEffect(typeKey, reasonId, outcome?.toDto())
    is HistoryReplayFact.Opaque -> HistoryReplayFactDto.Opaque(typeKey, actorSeat, directTiles.map { it.tileIndex }, revealedTiles.map { it.tileIndex })
}

/** 將局結算結果映射為網路 DTO。
 * @return 可公開傳輸的結算 DTO。
 */
private fun HistoryRoundOutcome.toDto(): HistoryRoundOutcomeDto = HistoryRoundOutcomeDto(
    reasonId = reasonId,
    beneficiarySeats = beneficiarySeats,
    scoresBySeat = scoresBySeat,
    classification = classification?.name,
    responsibleSeats = responsibleSeats,
    transitionDirective = transitionDirective?.name,
    scoreChangesBySeat = scoreChangesBySeat,
    winnerDetails = winnerDetails.map { it.toDto() },
    hasEarlierWinSettlement = hasEarlierWinSettlement,
)

/** 將歷史贏家詳情映射為網路 DTO。
 * @return 可公開傳輸的贏家詳情。
 */
private fun HistoryWinnerDetails.toDto(): HistoryWinnerDetailsDto = HistoryWinnerDetailsDto(
    seatIndex = seatIndex,
    detailFields = detailFields.map { it.toDto() },
    hand = hand?.toDto(),
)

/** 將歷史胡牌手牌映射為網路 DTO。 */
private fun HistoryReplayWinningHand.toDto(): HistoryReplayWinningHandDto = HistoryReplayWinningHandDto(
    standingTiles = standingTiles.map { it.tileIndex },
    winningTile = winningTile?.tileIndex,
)

/** 將歷史胡牌詳情欄位映射為網路 DTO。
 * @return 可公開傳輸的詳情欄位。
 */
private fun HistoryWinDetailField.toDto(): HistoryWinDetailFieldDto = HistoryWinDetailFieldDto(id, value.toDto())

/** 將歷史胡牌詳情值映射為網路 DTO。
 * @return 可公開傳輸的詳情值。
 */
private fun HistoryWinDetailValue.toDto(): HistoryWinDetailValueDto = when (this) {
    is HistoryWinDetailValue.Quantities -> HistoryWinDetailValueDto.Quantities(quantities.map { it.toDto() })
    is HistoryWinDetailValue.Entries -> HistoryWinDetailValueDto.Entries(
        entries.map { HistoryWinDetailValueDto.Entries.EntryDto(it.id, it.quantity?.toDto()) },
    )
    is HistoryWinDetailValue.Tiles -> HistoryWinDetailValueDto.Tiles(tiles.map { it.tileIndex })
}

/** 將有單位數值映射為網路 DTO。 */
private fun WinSettlementQuantity.toDto(): HistoryWinDetailQuantityDto = HistoryWinDetailQuantityDto(unitId, amount)

/** 將 Flow 單局桌況映射為網路 DTO。
 * @return 可公開傳輸的桌況 DTO。
 */
fun HistoryRoundState.toDto(): HistoryRoundStateDto = HistoryRoundStateDto(
    identity = identity.toDto(),
    roundNumber = roundNumber,
    position = position.toDto(),
    tileCatalog = tileCatalog.tiles.map { it.toDto() },
    players = players.map { it.toDto() },
    wallTiles = wallTiles.map { it.tileIndex },
    reservedTiles = reservedTiles.map { it.tileIndex },
    currentPlayerSeat = currentPlayerSeat,
    dealerSeat = dealerSeat,
    prevalentWind = prevalentWind.toDto(),
    roundPosition = roundPosition.toDto(),
    comboCount = comboCount,
    finishedPlayerSeats = finishedPlayerSeats,
    dynamicRuleState = dynamicRuleState?.toDto(),
    hasPendingReaction = hasPendingReaction,
    hasPendingKanReaction = hasPendingKanReaction,
    outcome = outcome?.toDto(),
)

/** 將 Replay 玩家桌況映射為網路 DTO。
 * @return 可公開傳輸的玩家桌況 DTO。
 */
private fun HistoryReplayPlayerState.toDto(): HistoryReplayPlayerStateDto = HistoryReplayPlayerStateDto(
    initialSeatIndex = initialSeatIndex,
    handTiles = handTiles.map { it.tileIndex },
    melds = melds.map { it.toDto() },
    lastDrawn = lastDrawn?.tileIndex,
    discards = discards.map { HistoryReplayDiscardDto(it.tile.tileIndex, it.isTaken, it.markers) },
    score = score,
    seatWind = seatWind.toDto(),
    playerRuleState = playerRuleState?.toDto(),
)

/** 將 Replay 副露映射為網路 DTO。
 * @return 可公開傳輸的副露 DTO。
 */
private fun HistoryReplayMeld.toDto(): HistoryReplayMeldDto = HistoryReplayMeldDto(
    type = type.toDto(),
    tiles = tiles.map { it.tileIndex },
    sourceTile = sourceTile?.tileIndex,
    sourceDirection = sourceDirection?.toDto(),
)

/** 將 Replay 規則公開資訊映射為網路 DTO。
 * @return 可公開傳輸的規則資訊 DTO。
 */
private fun HistoryReplayRuleInformation.toDto(): HistoryReplayRuleInformationDto = HistoryReplayRuleInformationDto(typeKey, summary)

/**
 * 將事件查詢要求 DTO 編碼成傳輸字串。
 * @param json 使用的 JSON codec。
 * @return 編碼後的要求字串。
 */
fun HistoryRoundEventsRequestDto.encode(json: Json): String = json.encodeToString(HistoryRoundEventsRequestDto.serializer(), this)

/** 將傳輸字串解碼為單局事件請求 DTO。
 * @param json 使用的 JSON codec。
 * @return 解碼後的請求 DTO。
 */
fun String.decodeHistoryRoundEventsRequest(json: Json): HistoryRoundEventsRequestDto = json.decodeFromString(HistoryRoundEventsRequestDto.serializer(), this)

/**
 * 將桌況查詢要求 DTO 編碼成傳輸字串。
 * @param json 使用的 JSON codec。
 * @return 編碼後的要求字串。
 */
fun HistoryRoundStateRequestDto.encode(json: Json): String = json.encodeToString(HistoryRoundStateRequestDto.serializer(), this)

/** 將傳輸字串解碼為單局桌況請求 DTO。
 * @param json 使用的 JSON codec。
 * @return 解碼後的請求 DTO。
 */
fun String.decodeHistoryRoundStateRequest(json: Json): HistoryRoundStateRequestDto = json.decodeFromString(HistoryRoundStateRequestDto.serializer(), this)
