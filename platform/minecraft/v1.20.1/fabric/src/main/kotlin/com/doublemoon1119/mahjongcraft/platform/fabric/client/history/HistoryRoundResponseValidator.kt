package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.fabric.network.HistoryQueryLimits
import kotlin.uuid.Uuid

/** 單局歷史回覆的可接受結果。 */
internal sealed interface HistoryRoundValidationResult<out T> {
    /** 回覆通過上下文、索引與牌目錄驗證。
     * @property value 通過驗證的回覆內容。
     */
    data class Success<T>(val value: T) : HistoryRoundValidationResult<T>

    /** 伺服器以穩定錯誤碼拒絕要求；這不是格式錯誤。
     * @property code 伺服器提供的穩定錯誤碼。
     */
    data class Error(val code: HistoryQueryErrorCodeDto) : HistoryRoundValidationResult<Nothing>

    /** 回覆格式或內容不符合原要求。
     * @property reason 客戶端拒絕回覆的原因。
     */
    data class Invalid(val reason: HistoryRoundValidationError) : HistoryRoundValidationResult<Nothing>
}

/** 單局歷史回覆無法安全套用的原因。 */
internal enum class HistoryRoundValidationError {
    /** 回覆要求識別碼不符。 */
    REQUEST_MISMATCH,

    /** 回覆對局或局序號不符。 */
    CONTEXT_MISMATCH,

    /** 成功回覆缺少內容，或錯誤回覆仍帶有內容。 */
    CONTENT_MISMATCH,

    /** 回覆中的交易索引不遞增或不在要求範圍。 */
    TRANSACTION_INDEX_INVALID,

    /** 牌索引超出回覆牌目錄。 */
    TILE_INDEX_INVALID,

    /** 玩家座位索引無法由回覆身份識別。 */
    SEAT_INDEX_INVALID,

    /** 桌況位置與要求不符。 */
    POSITION_MISMATCH,
}

/**
 * 驗證單局事件與桌況回覆，避免過期或偽造內容直接進入呈現層。
 *
 * 未知的 replay fact 會被視為可保留的 opaque 內容；只有上下文、牌索引與座位索引
 * 無法驗證時才拒絕整個回覆。
 */
internal object HistoryRoundResponseValidator {
    /** 客戶端接受的單局交易索引安全上限，獨立於伺服器資料庫限制。 */
    private const val MAX_TRANSACTION_INDEX = 8192

    /**
     * 驗證單局事件回覆。
     *
     * @param request 原本送出的事件要求。
     * @param response 伺服器回覆。
     * @return 可安全使用的事件、穩定錯誤或拒絕原因。
     */
    fun validateEvents(
        request: HistoryRoundEventsRequestDto,
        response: HistoryRoundEventsResponseDto,
    ): HistoryRoundValidationResult<HistoryRoundEventsDto> {
        if (response.requestId != request.requestId) return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.REQUEST_MISMATCH)
        if (response.matchId != request.matchId || response.roundNumber != request.roundNumber || response.startTransactionIndex != request.startTransactionIndex) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTEXT_MISMATCH)
        }
        response.errorCode?.let {
            return if (response.events == null) HistoryRoundValidationResult.Error(it) else HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTENT_MISMATCH)
        }
        val events = response.events ?: return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTENT_MISMATCH)
        if (events.roundNumber != request.roundNumber || !validIdentity(events.identity, request.matchId)) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTEXT_MISMATCH)
        }
        val validation = validateEventsContent(events, request.startTransactionIndex, request.limit)
        return validation ?: HistoryRoundValidationResult.Success(events)
    }

    /**
     * 驗證單局桌況回覆。
     *
     * @param request 原本送出的桌況要求。
     * @param response 伺服器回覆。
     * @return 可安全使用的桌況、穩定錯誤或拒絕原因。
     */
    fun validateState(
        request: HistoryRoundStateRequestDto,
        response: HistoryRoundStateResponseDto,
    ): HistoryRoundValidationResult<HistoryRoundStateDto> {
        if (response.requestId != request.requestId) return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.REQUEST_MISMATCH)
        if (response.matchId != request.matchId || response.roundNumber != request.roundNumber) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTEXT_MISMATCH)
        }
        if (response.position != request.position) return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.POSITION_MISMATCH)
        response.errorCode?.let {
            return if (response.state == null) HistoryRoundValidationResult.Error(it) else HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTENT_MISMATCH)
        }
        val state = response.state ?: return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTENT_MISMATCH)
        if (state.roundNumber != request.roundNumber || state.position != request.position || !validIdentity(state.identity, request.matchId)) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTEXT_MISMATCH)
        }
        val seats = seatIndexes(state.identity)
        if (seats == null ||
            state.players.map { it.initialSeatIndex }.toSet() != seats ||
            state.players.size != seats.size ||
            !seats.contains(state.currentPlayerSeat) ||
            !seats.contains(state.dealerSeat) ||
            !state.finishedPlayerSeats.all(seats::contains)
        ) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.SEAT_INDEX_INVALID)
        }
        if (!validTileIndexes(state.tileCatalog.size, state.wallTiles) || !validTileIndexes(state.tileCatalog.size, state.reservedTiles)) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.TILE_INDEX_INVALID)
        }
        for (player in state.players) {
            if (!seats.contains(player.initialSeatIndex) ||
                !validTileIndexes(state.tileCatalog.size, player.handTiles) ||
                player.lastDrawn?.let { !validTileIndex(state.tileCatalog.size, it) } == true ||
                !validTileIndexes(state.tileCatalog.size, player.discards.map { it.tile }) ||
                player.melds.any { !validTileIndexes(state.tileCatalog.size, it.tiles) || !validTileIndexOrNull(state.tileCatalog.size, it.sourceTile) }
            ) {
                return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.TILE_INDEX_INVALID)
            }
        }
        if (!validOutcome(state.outcome, seats, state.tileCatalog.size)) return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.SEAT_INDEX_INVALID)
        return HistoryRoundValidationResult.Success(state)
    }

    /** 驗證事件序列的交易、牌目錄及玩家座位引用。
     * @param events 待驗證的事件頁。
     * @param startIndex 要求的起始交易索引。
     * @param limit 要求的交易數量上限。
     * @return 失敗原因，或 null 表示通過。
     */
    private fun validateEventsContent(events: HistoryRoundEventsDto, startIndex: Int, limit: Int): HistoryRoundValidationResult.Invalid? {
        val seats = seatIndexes(events.identity) ?: return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.SEAT_INDEX_INVALID)
        if (startIndex < 0 || startIndex > MAX_TRANSACTION_INDEX || limit < 0 || limit > MAX_TRANSACTION_INDEX || events.transactions.size > limit) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.TRANSACTION_INDEX_INVALID)
        }
        var previous = startIndex - 1
        var previousDeclaredCount = 0
        for (transaction in events.transactions) {
            if (transaction.index !in 0 until MAX_TRANSACTION_INDEX ||
                transaction.index != previous + 1 ||
                transaction.declaredTileCountAfter < previousDeclaredCount ||
                transaction.declaredTileCountAfter < 0
            ) {
                return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.TRANSACTION_INDEX_INVALID)
            }
            previous = transaction.index
            previousDeclaredCount = transaction.declaredTileCountAfter
            for (fact in transaction.facts) {
                val actor = when (fact) {
                    is HistoryReplayFactDto.KnownAction -> fact.actorSeat
                    is HistoryReplayFactDto.Reaction -> fact.resolvedActorSeat
                    is HistoryReplayFactDto.Opaque -> fact.actorSeat
                    else -> null
                }
                if (actor != null && !seats.contains(actor)) return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.SEAT_INDEX_INVALID)
                val tiles = when (fact) {
                    is HistoryReplayFactDto.KnownAction -> fact.directTiles + fact.revealedTiles
                    is HistoryReplayFactDto.Opaque -> fact.directTiles + fact.revealedTiles
                    else -> emptyList()
                }
                if (!validTileIndexes(events.tileCatalog.size, tiles) || tiles.any { it >= transaction.declaredTileCountAfter }) {
                    return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.TILE_INDEX_INVALID)
                }
                val outcome = when (fact) {
                    is HistoryReplayFactDto.Completion -> fact.outcome
                    is HistoryReplayFactDto.RuleEffect -> fact.outcome
                    else -> null
                }
                if (!validOutcome(outcome, seats, events.tileCatalog.size, transaction.declaredTileCountAfter)) {
                    return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.CONTENT_MISMATCH)
                }
            }
        }
        val nextTransactionIndex = events.nextTransactionIndex
        if (nextTransactionIndex != null &&
            (events.transactions.isEmpty() || nextTransactionIndex !in 0..MAX_TRANSACTION_INDEX || nextTransactionIndex != events.transactions.last().index + 1)
        ) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.TRANSACTION_INDEX_INVALID)
        }
        if (events.transactions.size > 0 && previousDeclaredCount != events.tileCatalog.size) {
            return HistoryRoundValidationResult.Invalid(HistoryRoundValidationError.TILE_INDEX_INVALID)
        }
        return null
    }

    /** 驗證對局與玩家的身份識別碼；座位集合另行驗證。
     * @param identity 回覆中的 Replay 身份。
     * @param expectedMatchId 要求中的對局識別碼。
     * @return 所有身份識別碼格式正確且對局相符時為 true。
     */
    private fun validIdentity(identity: HistoryReplayIdentityDto, expectedMatchId: String): Boolean = identity.matchId == expectedMatchId &&
        canonicalUuid(identity.matchId) &&
        canonicalUuid(identity.tableId) &&
        identity.players.all { player ->
            val playerId = player.playerId
            playerId == null || canonicalUuid(playerId)
        }

    /** 取得身份中的非負且不重複座位。
     * @param identity 回覆中的 Replay 身份。
     * @return 唯一座位集合，或身份沒有有效座位時為 null。
     */
    private fun seatIndexes(identity: HistoryReplayIdentityDto): Set<Int>? {
        if (identity.players.isEmpty()) return null
        val seats = identity.players.map { it.initialSeatIndex }
        return seats.takeIf { it.all { seat -> seat >= 0 } && it.toSet().size == it.size }?.toSet()
    }

    /** 驗證局結算結果的座位引用。
     * @param outcome 局結算結果。
     * @param seats 有效座位集合。
     * @param tileCatalogSize 回覆牌目錄大小。
     * @param declaredTileCount 該交易宣告的牌數量；桌況回覆沒有交易限制時為 null。
     * @return 引用與詳情內容均有效時為 true。
     */
    private fun validOutcome(
        outcome: HistoryRoundOutcomeDto?,
        seats: Set<Int>,
        tileCatalogSize: Int,
        declaredTileCount: Int? = null,
    ): Boolean = outcome == null ||
        (
            outcome.beneficiarySeats.all(seats::contains) &&
                outcome.responsibleSeats.all(seats::contains) &&
                outcome.scoresBySeat.keys.all(seats::contains) &&
                outcome.scoreChangesBySeat.keys.all(seats::contains) &&
                outcome.scoreChangesBySeat.keys.all(outcome.scoresBySeat::containsKey) &&
                outcome.winnerDetails.map { it.seatIndex }.let { winnerSeats ->
                    winnerSeats.distinct().size == winnerSeats.size &&
                        winnerSeats.all(seats::contains) &&
                        winnerSeats.all(outcome.beneficiarySeats::contains)
                } &&
                outcome.winnerDetails.all { winner -> validWinnerDetails(winner, tileCatalogSize, declaredTileCount) }
            )

    /** 驗證贏家詳情的識別碼、翻譯資料與牌參照均可安全呈現。
     * @param winner 贏家詳情。
     * @param tileCatalogSize 回覆牌目錄大小。
     * @param declaredTileCount 該交易宣告的牌數量；桌況回覆沒有交易限制時為 null。
     * @return 詳情格式與參照均有效時為 true。
     */
    private fun validWinnerDetails(
        winner: HistoryWinnerDetailsDto,
        tileCatalogSize: Int,
        declaredTileCount: Int?,
    ): Boolean {
        if (!namespaced(winner.templateKey) || winner.detailFields.map { it.id }.distinct().size != winner.detailFields.size) return false
        var textBytes = winner.templateKey.toByteArray(Charsets.UTF_8).size.toLong()
        for (field in winner.detailFields) {
            if (!namespaced(field.id)) return false
            textBytes += field.id.toByteArray(Charsets.UTF_8).size
            when (val value = field.value) {
                is HistoryWinDetailValueDto.Text -> {
                    textBytes += value.translationKey.toByteArray(Charsets.UTF_8).size
                    textBytes += value.arguments.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
                    if (value.translationKey.isBlank()) return false
                }
                is HistoryWinDetailValueDto.Entries -> {
                    for (entry in value.entries) {
                        if (entry.translationKey.isBlank() ||
                            entry.trailingText.isNotEmpty() &&
                            entry.trailingTranslationKey != null ||
                            entry.trailingTranslationArgument != null &&
                            entry.trailingTranslationKey == null ||
                            entry.trailingTranslationKey?.isBlank() == true
                        ) {
                            return false
                        }
                        textBytes += entry.translationKey.toByteArray(Charsets.UTF_8).size
                        textBytes += entry.trailingText.toByteArray(Charsets.UTF_8).size
                        textBytes += entry.trailingTranslationKey?.toByteArray(Charsets.UTF_8)?.size ?: 0
                        textBytes += entry.trailingTranslationArgument?.toByteArray(Charsets.UTF_8)?.size ?: 0
                    }
                }
                is HistoryWinDetailValueDto.Tiles -> {
                    if (!validTileIndexes(tileCatalogSize, value.tiles) ||
                        declaredTileCount != null &&
                        value.tiles.any { it >= declaredTileCount }
                    ) {
                        return false
                    }
                    textBytes += value.tiles.size.toLong() * Int.SIZE_BYTES
                }
            }
        }
        return textBytes <= HistoryQueryLimits.RESPONSE_BYTES
    }

    /** 驗證規則識別碼具有 namespace 與 path 部分。
     * @param value 待驗證識別碼。
     * @return 識別碼格式有效時為 true。
     */
    private fun namespaced(value: String): Boolean = NamespacedId.isValid(value)

    /** 驗證字串是否為標準 UUID 表示。
     * @param value 待驗證字串。
     * @return 可解析且格式與標準字串一致時為 true。
     */
    private fun canonicalUuid(value: String): Boolean = runCatching { Uuid.parse(value).toString() == value.lowercase() }.getOrDefault(false)

    /** 驗證一組牌索引均落在目錄範圍。
     * @param size 牌目錄大小。
     * @param values 待驗證索引。
     * @return 全部有效時為 true。
     */
    private fun validTileIndexes(size: Int, values: List<Int>): Boolean = values.all { validTileIndex(size, it) }

    /** 驗證單一牌索引。
     * @param size 牌目錄大小。
     * @param value 待驗證索引。
     * @return 索引有效時為 true。
     */
    private fun validTileIndex(size: Int, value: Int): Boolean = value in 0 until size

    /** 驗證可為 null 的牌索引。
     * @param size 牌目錄大小。
     * @param value 待驗證索引。
     * @return null 或有效索引時為 true。
     */
    private fun validTileIndexOrNull(size: Int, value: Int?): Boolean = value == null || validTileIndex(size, value)
}
