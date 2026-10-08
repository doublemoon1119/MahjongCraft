package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.HistoryQueryLimits
import kotlinx.serialization.json.Json

/**
 * 一次映射後按完整交易縮頁，同步裁切截止位置的牌字典。
 * @param request 原查詢上下文。
 * @param page 已授權讀取的事件頁。
 * @param json 線路編碼設定。
 * @param maximumBytes 回覆 UTF-8 位元組上限。
 * @return 可傳送頁面或單筆亦超限的失敗。
 */
internal fun boundedHistoryRoundEvents(
    request: HistoryRoundEventsRequestDto,
    page: HistoryRoundEvents,
    json: Json,
    maximumBytes: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryRoundEventsResponseDto {
    val mapped = page.toDto()
    var count = mapped.transactions.size
    while (true) {
        val transactions = mapped.transactions.take(count)
        val shortened = count < mapped.transactions.size
        val next = if (shortened) transactions.lastOrNull()?.index?.plus(1) else mapped.nextTransactionIndex
        val catalog = if (shortened) mapped.tileCatalog.take(checkNotNull(transactions.lastOrNull()).declaredTileCountAfter) else mapped.tileCatalog
        val response = HistoryRoundEventsResponseDto(
            request.requestId,
            request.matchId,
            request.roundNumber,
            request.startTransactionIndex,
            events = mapped.copy(transactions = transactions, nextTransactionIndex = next, tileCatalog = catalog),
            errorCode = null,
        )
        if (json.encodeToString(HistoryRoundEventsResponseDto.serializer(), response).encodeToByteArray().size <= maximumBytes) return response
        if (count <= 1) {
            return HistoryRoundEventsResponseDto(
                request.requestId,
                request.matchId,
                request.roundNumber,
                request.startTransactionIndex,
                errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE,
                events = null,
            )
        }
        count--
    }
}

/**
 * 原子傳送完整歷史桌況，不能為符合預算截斷持牌或牌牆。
 * @param request 原查詢上下文。
 * @param state 已授權的完整狀態。
 * @param json 線路編碼設定。
 * @param maximumBytes 回覆 UTF-8 位元組上限。
 * @return 完整狀態或超限錯誤。
 */
internal fun boundedHistoryRoundState(
    request: HistoryRoundStateRequestDto,
    state: HistoryRoundState,
    json: Json,
    maximumBytes: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryRoundStateResponseDto {
    val response = HistoryRoundStateResponseDto(request.requestId, request.matchId, request.roundNumber, request.position, state = state.toDto(), errorCode = null)
    return if (json.encodeToString(HistoryRoundStateResponseDto.serializer(), response).encodeToByteArray().size <= maximumBytes) {
        response
    } else {
        HistoryRoundStateResponseDto(request.requestId, request.matchId, request.roundNumber, request.position, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, state = null)
    }
}
