package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListPage
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryPageDirection
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryCursor
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRuleSettings
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortValue
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.HistoryQueryLimits
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.encodedHistoryResponseUtf8Size
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.toDto
import kotlin.uuid.Uuid

/**
 * 將單場摘要限制在歷史查詢回覆的 UTF-8 位元組上限內。
 *
 * @param requestId 回應配對識別碼。
 * @param response 已建立的摘要回應。
 * @param json 線路編碼設定。
 * @param limit UTF-8 位元組上限。
 * @return 原回應或不含摘要內容的超限錯誤。
 */
internal fun boundedHistorySummary(
    requestId: String,
    response: HistorySummaryResponseDto,
    json: NetworkJson,
    limit: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistorySummaryResponseDto = if (
    encodedHistoryResponseUtf8Size(json, HistorySummaryResponseDto.serializer(), response) <= limit
) {
    response
} else {
    HistorySummaryResponseDto(requestId, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, detail = null)
}

/**
 * 將清單縮減為能以完整項目傳送的頁面，不切割單筆摘要。
 *
 * 往後取頁時保留前面的項目，往前取頁時保留後面的項目，讓留下的項目緊接著原游標；被捨棄那一側的游標改為最後保留的項目。
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
    json: NetworkJson,
    encodeCursor: (HistoryQueryCursor) -> String,
    limit: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryListResponseDto {
    val backward = request.direction == HistoryPageDirection.BEFORE
    var count = page.entries.size
    while (true) {
        val trimmed = count < page.entries.size
        val entries = if (backward) page.entries.takeLast(count) else page.entries.take(count)
        val next = if (trimmed && !backward) entries.lastOrNull()?.positionIn(request, principalId) else page.nextCursor
        val previous = if (trimmed && backward) entries.firstOrNull()?.positionIn(request, principalId) else page.previousCursor
        val response = HistoryListResponseDto(
            requestId = requestId,
            entries = entries.map { it.toDto() },
            nextCursor = next?.let(encodeCursor),
            previousCursor = previous?.let(encodeCursor),
            totalCount = page.totalCount,
            firstEntryIndex = page.firstEntryIndex + if (backward) page.entries.size - count else 0,
            errorCode = null,
            allowAll = false,
            allowStressTest = false,
        )
        if (encodedHistoryResponseUtf8Size(json, HistoryListResponseDto.serializer(), response) <= limit) return response
        if (count <= 1) {
            return emptyHistoryListResponse(requestId = requestId, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE)
        }
        count--
    }
}

/**
 * 建立沒有對局摘要的清單回覆，用於查詢失敗、被拒絕或回覆超出大小上限。
 *
 * @param requestId 回應配對識別碼。
 * @param errorCode 安全的查詢錯誤代碼。
 * @param allowAll 是否允許查詢全部已保存歷史。
 * @param allowStressTest 是否允許查詢壓力測試資料。
 * @return 空的清單回覆。
 */
internal fun emptyHistoryListResponse(
    requestId: String,
    errorCode: HistoryQueryErrorCodeDto,
    allowAll: Boolean = false,
    allowStressTest: Boolean = false,
): HistoryListResponseDto = HistoryListResponseDto(
    requestId = requestId,
    entries = emptyList(),
    nextCursor = null,
    previousCursor = null,
    totalCount = 0,
    firstEntryIndex = 0,
    errorCode = errorCode,
    allowAll = allowAll,
    allowStressTest = allowStressTest,
)

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
    json: NetworkJson,
    limit: Int = HistoryQueryLimits.RESPONSE_BYTES,
): HistoryRuleSettingsResponseDto {
    val response = try {
        HistoryRuleSettingsResponseDto(requestId, config = settings.toDto(registries), errorCode = null)
    } catch (_: IllegalArgumentException) {
        return HistoryRuleSettingsResponseDto(requestId, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE, config = null)
    } catch (_: IllegalStateException) {
        return HistoryRuleSettingsResponseDto(requestId, errorCode = HistoryQueryErrorCodeDto.NOT_AVAILABLE, config = null)
    }
    return if (encodedHistoryResponseUtf8Size(json, HistoryRuleSettingsResponseDto.serializer(), response) <= limit) {
        response
    } else {
        HistoryRuleSettingsResponseDto(requestId, errorCode = HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, config = null)
    }
}

/**
 * 以實際傳出的項目建立游標，避免縮頁後漏掉未傳項目。
 *
 * @param request 完整查詢上下文。
 * @param principalId 本人排序的可信身分。
 * @return 僅含排序位置、不含授權資格的游標。
 */
private fun HistoryMatchSummary.positionIn(request: HistoryListRequest, principalId: Uuid): HistoryQueryCursor {
    val ownResult = results.firstOrNull { it.playerId == principalId }
    val value = when (request.sortField) {
        HistorySortField.ENDED_AT -> endedAtEpochMillis
        HistorySortField.DURATION -> duration?.inWholeMilliseconds
        HistorySortField.OWN_RANK -> ownResult?.finalRank?.toLong()
        HistorySortField.OWN_SCORE -> ownResult?.finalScore?.toLong()
    }
    return HistoryQueryCursor(
        sortValue = HistorySortValue(value, value == null),
        matchId = matchId,
        scope = request.scope,
        sortField = request.sortField,
        sortDirection = request.sortDirection,
        filters = request.filters,
    )
}
