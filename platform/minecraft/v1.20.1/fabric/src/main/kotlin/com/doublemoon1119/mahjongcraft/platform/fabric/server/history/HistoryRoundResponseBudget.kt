package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.fabric.network.HistoryQueryLimits

/**
 * 一次映射後按完整交易縮頁，同步裁切截止位置的牌字典。
 *
 * @param request 原查詢上下文。
 * @param page 已授權讀取的事件頁。
 * @param json 線路編碼設定。
 * @param registries 牌面特徵的網路轉換。
 * @param maximumBytes 回覆 UTF-8 位元組上限。
 * @return 可傳送頁面或單筆亦超限的失敗。
 */
internal fun boundedHistoryRoundEvents(
    request: HistoryRoundEventsRequestDto,
    page: HistoryRoundEvents,
    json: NetworkJson,
    registries: NetworkDtoRegistries,
    maximumBytes: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryRoundEventsResponseDto {
    val mapped = page.toDto(registries)
    var count = mapped.transactions.size
    while (true) {
        val transactions = mapped.transactions.take(count)
        val shortened = count < mapped.transactions.size
        val next = if (shortened) transactions.lastOrNull()?.index?.plus(1) else mapped.nextTransactionIndex
        val catalog = if (shortened) mapped.tileCatalog.take(checkNotNull(transactions.lastOrNull()).declaredTileCountAfter) else mapped.tileCatalog
        val response = HistoryRoundEventsResponseDto(
            requestId = request.requestId,
            matchId = request.matchId,
            roundNumber = request.roundNumber,
            startTransactionIndex = request.startTransactionIndex,
            events = mapped.copy(transactions = transactions, nextTransactionIndex = next, tileCatalog = catalog),
            errorCode = null,
        )
        if (encodedHistoryResponseUtf8Size(json, HistoryRoundEventsResponseDto.serializer(), response) <= maximumBytes) return response
        if (count <= 1) {
            return HistoryRoundEventsResponseDto(
                requestId = request.requestId,
                matchId = request.matchId,
                roundNumber = request.roundNumber,
                startTransactionIndex = request.startTransactionIndex,
                errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE,
                events = null,
            )
        }
        count--
    }
}

/**
 * 傳送從第一個位置起放得進預算的連續桌況；每份桌況都完整傳送，不截斷持牌或牌牆，放不下的後續位置整份省略。
 *
 * @param request 原查詢上下文。
 * @param states 已授權、依位置先後排列的完整狀態。
 * @param json 線路編碼設定。
 * @param registries 牌面特徵的網路轉換。
 * @param maximumBytes 回覆 UTF-8 位元組上限。
 * @return 放得下的連續狀態；第一份就放不下時為超限錯誤。
 */
internal fun boundedHistoryRoundStates(
    request: HistoryRoundStateRequestDto,
    states: List<HistoryRoundState>,
    json: NetworkJson,
    registries: NetworkDtoRegistries,
    maximumBytes: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryRoundStateResponseDto {
    val encoded = states.map { it.toDto(registries) }
    var count = encoded.size
    while (count > 0) {
        val response = HistoryRoundStateResponseDto(request.requestId, request.matchId, request.roundNumber, request.position, states = encoded.take(count), errorCode = null)
        if (encodedHistoryResponseUtf8Size(json, HistoryRoundStateResponseDto.serializer(), response) <= maximumBytes) return response
        count--
    }
    return HistoryRoundStateResponseDto(request.requestId, request.matchId, request.roundNumber, request.position, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, states = emptyList())
}
