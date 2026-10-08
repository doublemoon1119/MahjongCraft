package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListPage
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryCursor
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRuleSettings
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortValue
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.network.HistoryQueryLimits
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * 將清單縮減為能以完整項目傳送的頁面，不切割單筆摘要。
 *
 * @param requestId 回應配對識別碼。
 * @param page 已授權的原始頁面。
 * @param request 原查詢條件。
 * @param principalId 可信發起者，用於本人排序游標。
 * @param json 線路編碼設定。
 * @param encodeCursor 不透明游標的編碼器。
 * @param limit JSON UTF-8 上限。
 * @return 合法回應或單筆亦超限的穩定錯誤。
 */
internal fun boundedHistoryPage(
    requestId: String,
    page: HistoryListPage,
    request: HistoryListRequest,
    principalId: Uuid,
    json: Json,
    encodeCursor: (HistoryQueryCursor) -> String,
    limit: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryListResponseDto {
    var count = page.entries.size
    while (true) {
        val entries = page.entries.take(count)
        val cursor = if (count < page.entries.size) entries.lastOrNull()?.cursorAfter(request, principalId) else page.nextCursor
        val response = HistoryListResponseDto(requestId, entries.map { it.toDto() }, cursor?.let(encodeCursor), errorCode = null, allowAll = false)
        if (json.encodeToString(HistoryListResponseDto.serializer(), response).toByteArray(Charsets.UTF_8).size <= limit) return response
        if (count <= 1) return HistoryListResponseDto(requestId, emptyList(), nextCursor = null, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, allowAll = false)
        count--
    }
}

/**
 * 將已授權的開局設定編碼為有界回應；缺少網路 codec 不視為玩家要求無效。
 *
 * @param requestId 回應配對識別碼。
 * @param settings 已從歷史文件讀取的開局設定。
 * @param registries 正式網路 DTO registry。
 * @param json 線路編碼設定。
 * @param limit JSON UTF-8 上限。
 * @return 完整設定或不含設定內容的穩定錯誤。
 */
internal fun boundedHistoryRuleSettings(
    requestId: String,
    settings: HistoryRuleSettings,
    registries: NetworkDtoRegistries,
    json: Json,
    limit: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryRuleSettingsResponseDto {
    val response = try {
        HistoryRuleSettingsResponseDto(requestId, config = settings.toDto(registries), errorCode = null)
    } catch (_: IllegalArgumentException) {
        return HistoryRuleSettingsResponseDto(requestId, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE, config = null)
    } catch (_: IllegalStateException) {
        return HistoryRuleSettingsResponseDto(requestId, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE, config = null)
    }
    return if (json.encodeToString(HistoryRuleSettingsResponseDto.serializer(), response).toByteArray(Charsets.UTF_8).size <= limit) {
        response
    } else {
        HistoryRuleSettingsResponseDto(requestId, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, config = null)
    }
}

/**
 * 以實際傳出的最後一筆建立游標，避免縮頁後漏掉未傳項目。
 *
 * @param request 完整查詢上下文。
 * @param principalId 本人排序的可信身分。
 * @return 僅含排序位置、不含授權資格的游標。
 */
private fun HistoryMatchSummary.cursorAfter(request: HistoryListRequest, principalId: Uuid): HistoryQueryCursor {
    val ownResult = results.firstOrNull { it.playerId == principalId }
    val value = when (request.sortField) {
        HistorySortField.ENDED_AT -> endedAtEpochMillis
        HistorySortField.DURATION -> duration?.inWholeMilliseconds
        HistorySortField.OWN_RANK -> ownResult?.finalRank?.toLong()
        HistorySortField.OWN_SCORE -> ownResult?.finalScore?.toLong()
    }
    return HistoryQueryCursor(
        HistorySortValue(value, value == null),
        matchId,
        request.scope,
        request.sortField,
        request.sortDirection,
        request.filters,
    )
}
