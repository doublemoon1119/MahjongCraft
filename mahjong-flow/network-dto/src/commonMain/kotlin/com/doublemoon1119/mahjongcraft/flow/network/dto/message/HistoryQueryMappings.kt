package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryAiFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryIntegrityFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryOutcomeFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryParticipantSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryCursor
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryErrorCode
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryFilters
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRankRange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryResultSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRoundSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortDirection
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortValue
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** 將網路查詢範圍映射為 Flow 查詢範圍。
 * @return 對應的 Flow 查詢範圍。
 */
fun HistoryQueryScopeDto.toDomain(): HistoryQueryScope = when (this) {
    HistoryQueryScopeDto.OWN -> HistoryQueryScope.OWN
    HistoryQueryScopeDto.ALL -> HistoryQueryScope.ALL
}

/** 將網路排序欄位映射為 Flow 排序欄位。
 * @return 對應的 Flow 排序欄位。
 */
fun HistorySortFieldDto.toDomain(): HistorySortField = when (this) {
    HistorySortFieldDto.ENDED_AT -> HistorySortField.ENDED_AT
    HistorySortFieldDto.DURATION -> HistorySortField.DURATION
    HistorySortFieldDto.OWN_RANK -> HistorySortField.OWN_RANK
    HistorySortFieldDto.OWN_SCORE -> HistorySortField.OWN_SCORE
}

/** 將網路排序方向映射為 Flow 排序方向。
 * @return 對應的 Flow 排序方向。
 */
fun HistorySortDirectionDto.toDomain(): HistorySortDirection = when (this) {
    HistorySortDirectionDto.ASC -> HistorySortDirection.ASC
    HistorySortDirectionDto.DESC -> HistorySortDirection.DESC
}

/** 將網路結果篩選映射為 Flow 結果篩選。
 * @return 對應的 Flow 結果篩選。
 */
fun HistoryOutcomeFilterDto.toDomain(): HistoryOutcomeFilter = when (this) {
    HistoryOutcomeFilterDto.COMPLETED -> HistoryOutcomeFilter.COMPLETED
    HistoryOutcomeFilterDto.INTERRUPTED -> HistoryOutcomeFilter.INTERRUPTED
}

/** 將網路完整性篩選映射為 Flow 完整性篩選。
 * @return 對應的 Flow 完整性篩選。
 */
fun HistoryIntegrityFilterDto.toDomain(): HistoryIntegrityFilter = when (this) {
    HistoryIntegrityFilterDto.COMPLETE -> HistoryIntegrityFilter.COMPLETE
    HistoryIntegrityFilterDto.INCOMPLETE -> HistoryIntegrityFilter.INCOMPLETE
}

/** 將網路 AI 篩選映射為 Flow AI 篩選。
 * @return 對應的 Flow AI 篩選。
 */
fun HistoryAiFilterDto.toDomain(): HistoryAiFilter = when (this) {
    HistoryAiFilterDto.CONTAINS_AI -> HistoryAiFilter.CONTAINS_AI
    HistoryAiFilterDto.NO_AI -> HistoryAiFilter.NO_AI
}

/** 將網路篩選映射為 Flow 篩選；游標由傳輸邊界的 codec 解碼。
 * @return 對應的 Flow 篩選。
 */
fun HistoryQueryFiltersDto.toDomain(): HistoryQueryFilters = HistoryQueryFilters(
    ruleId = ruleId,
    outcome = outcome?.toDomain(),
    integrity = integrity?.toDomain(),
    ai = ai?.toDomain(),
    endedAtFromEpochMillis = endedAtFromEpochMillis,
    endedAtBeforeEpochMillis = endedAtBeforeEpochMillis,
    ownRank = if (ownRankMin == null && ownRankMax == null) null else HistoryRankRange(ownRankMin, ownRankMax),
)

/** 將網路清單要求映射為 Flow 要求；[decodeCursor] 驗證並解碼不透明游標。
 *
 * @param decodeCursor 驗證並解碼傳輸 cursor 的函式。
 * @return 可交給 Flow use case 驗證的清單查詢要求。
 */
fun HistoryListRequestDto.toDomain(decodeCursor: (String?) -> HistoryQueryCursor?): HistoryListRequest = HistoryListRequest(
    scope = scope.toDomain(),
    sortField = sortField.toDomain(),
    sortDirection = sortDirection.toDomain(),
    filters = filters.toDomain(),
    pageSize = pageSize,
    cursor = decodeCursor(cursor),
)

/** 將網路 cursor 內容映射為 Flow cursor。
 * @return 驗證後的 Flow cursor。
 */
fun HistoryQueryCursorDto.toDomain(): HistoryQueryCursor = HistoryQueryCursor(
    sortValue = HistorySortValue(numericSortValue, nullBucket),
    matchId = matchId.toHistoryUuid(),
    scope = scope.toDomain(),
    sortField = sortField.toDomain(),
    sortDirection = sortDirection.toDomain(),
    filters = filters.toDomain(),
)

/** 將 Flow cursor 映射為可序列化的網路 cursor 內容。
 * @return 可序列化的網路 cursor 內容。
 */
fun HistoryQueryCursor.toDto(): HistoryQueryCursorDto = HistoryQueryCursorDto(
    numericSortValue = sortValue.numericValue,
    nullBucket = sortValue.nullBucket,
    matchId = matchId.toString(),
    scope = when (scope) {
        HistoryQueryScope.OWN -> HistoryQueryScopeDto.OWN
        HistoryQueryScope.ALL -> HistoryQueryScopeDto.ALL
    },
    sortField = when (sortField) {
        HistorySortField.ENDED_AT -> HistorySortFieldDto.ENDED_AT
        HistorySortField.DURATION -> HistorySortFieldDto.DURATION
        HistorySortField.OWN_RANK -> HistorySortFieldDto.OWN_RANK
        HistorySortField.OWN_SCORE -> HistorySortFieldDto.OWN_SCORE
    },
    sortDirection = when (sortDirection) {
        HistorySortDirection.ASC -> HistorySortDirectionDto.ASC
        HistorySortDirection.DESC -> HistorySortDirectionDto.DESC
    },
    filters = HistoryQueryFiltersDto(
        ruleId = filters.ruleId,
        outcome = filters.outcome?.toDto(),
        integrity = filters.integrity?.toDto(),
        ai = filters.ai?.let {
            when (it) {
                HistoryAiFilter.CONTAINS_AI -> HistoryAiFilterDto.CONTAINS_AI
                HistoryAiFilter.NO_AI -> HistoryAiFilterDto.NO_AI
            }
        },
        endedAtFromEpochMillis = filters.endedAtFromEpochMillis,
        endedAtBeforeEpochMillis = filters.endedAtBeforeEpochMillis,
        ownRankMin = filters.ownRank?.minimum,
        ownRankMax = filters.ownRank?.maximum,
    ),
)

/** 將 Flow cursor 以 JSON 編碼為傳輸字串。
 *
 * @param json 使用的 JSON codec。
 * @return 可放入網路 DTO 的 cursor 字串。
 */
fun HistoryQueryCursor.encode(json: Json): String = json.encodeToString(HistoryQueryCursorDto.serializer(), toDto())

/** 將傳輸字串解碼為 Flow cursor；格式錯誤由呼叫端轉成無效請求。
 *
 * @param json 使用的 JSON codec。
 * @return 驗證後的 Flow cursor。
 */
fun String.decodeHistoryCursor(json: Json): HistoryQueryCursor = json.decodeFromString(HistoryQueryCursorDto.serializer(), this).toDomain()

/** 將 Flow 查詢錯誤映射為不暴露儲存細節的傳輸代碼。
 * @return 穩定的網路錯誤代碼。
 */
fun HistoryQueryErrorCode.toDto(): HistoryQueryErrorCodeDto = when (this) {
    HistoryQueryErrorCode.QUERY_DISABLED -> HistoryQueryErrorCodeDto.QUERY_DISABLED
    HistoryQueryErrorCode.ACCESS_DENIED -> HistoryQueryErrorCodeDto.ACCESS_DENIED
    HistoryQueryErrorCode.INVALID_REQUEST -> HistoryQueryErrorCodeDto.INVALID_REQUEST
    HistoryQueryErrorCode.NOT_AVAILABLE -> HistoryQueryErrorCodeDto.NOT_AVAILABLE
    HistoryQueryErrorCode.BUSY -> HistoryQueryErrorCodeDto.BUSY
    HistoryQueryErrorCode.RATE_LIMITED -> HistoryQueryErrorCodeDto.RATE_LIMITED
    HistoryQueryErrorCode.TIMEOUT -> HistoryQueryErrorCodeDto.TIMEOUT
    HistoryQueryErrorCode.DISCONNECTED -> HistoryQueryErrorCodeDto.DISCONNECTED
    HistoryQueryErrorCode.CONTENT_TOO_LARGE -> HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE
}

/** 將 Flow 參與者摘要映射為網路參與者摘要。
 * @return 網路參與者摘要。
 */
fun HistoryParticipantSummary.toDto(): HistoryParticipantSummaryDto = HistoryParticipantSummaryDto(
    seatIndex = seatIndex,
    playerId = playerId.toString(),
    aiStrategyId = aiStrategyId,
)

/** 將 Flow 終局結果映射為網路終局結果。
 * @return 網路終局結果。
 */
fun HistoryResultSummary.toDto(): HistoryResultSummaryDto = HistoryResultSummaryDto(
    playerId = playerId.toString(),
    finalScore = finalScore,
    finalRank = finalRank,
)

/** 將 Flow 對局摘要映射為不含重播內容的網路摘要。
 * @return 不含重播內容的網路摘要。
 */
fun HistoryMatchSummary.toDto(): HistoryMatchSummaryDto = HistoryMatchSummaryDto(
    matchId = matchId.toString(),
    ruleId = ruleId,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    outcome = outcome?.toDto(),
    integrity = integrity.toDto(),
    integrityDiagnostic = integrityDiagnostic,
    durationMillis = duration?.inWholeMilliseconds,
    participants = participants.map { it.toDto() },
    roundCount = roundCount,
    resultsAvailable = resultsAvailable,
    results = results.map { it.toDto() },
)

/** 將 Flow 結果篩選映射為網路結果篩選。
 * @return 對應的網路結果篩選。
 */
fun HistoryOutcomeFilter.toDto(): HistoryOutcomeFilterDto = when (this) {
    HistoryOutcomeFilter.COMPLETED -> HistoryOutcomeFilterDto.COMPLETED
    HistoryOutcomeFilter.INTERRUPTED -> HistoryOutcomeFilterDto.INTERRUPTED
}

/** 將 Flow 完整性篩選映射為網路完整性篩選。
 * @return 對應的網路完整性篩選。
 */
fun HistoryIntegrityFilter.toDto(): HistoryIntegrityFilterDto = when (this) {
    HistoryIntegrityFilter.COMPLETE -> HistoryIntegrityFilterDto.COMPLETE
    HistoryIntegrityFilter.INCOMPLETE -> HistoryIntegrityFilterDto.INCOMPLETE
}

/** 將 Flow 局摘要映射為網路局摘要。
 * @return 網路局摘要。
 */
fun HistoryRoundSummary.toDto(): HistoryRoundSummaryDto = HistoryRoundSummaryDto(
    roundNumber = roundNumber,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
)

/** 將 Flow 對局明細映射為網路對局明細。
 * @return 網路對局明細。
 */
fun HistoryMatchDetail.toDto(): HistoryMatchDetailDto = HistoryMatchDetailDto(
    summary = summary.toDto(),
    rounds = rounds.map { it.toDto() },
)

/** 將網路 UUID 字串解析為 Flow UUID，錯誤由呼叫端轉成無效請求。
 * @return 解析後的 UUID。
 */
fun String.toHistoryUuid(): Uuid = Uuid.parse(this)
