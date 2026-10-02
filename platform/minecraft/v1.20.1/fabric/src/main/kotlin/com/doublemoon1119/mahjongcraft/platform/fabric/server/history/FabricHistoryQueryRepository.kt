package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryAiFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryIntegrityFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListPage
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryOutcomeFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryParticipantSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryCursor
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryError
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryErrorCode
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryResultSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRoundSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRuleSettings
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRuleSettingsRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortDirection
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortValue
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySummaryRequest
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * 綁定收到要求時的世界 session，借用正式 writer 的唯一連線與政策 lease。
 *
 * @property writer 正式歷史資料庫生命週期。
 * @property sessionId 要求固定的世界 session，不能切換到另一個存檔。
 * @property playerIdentities 伺服器最後已知普通名稱的唯讀索引。
 */
internal class FabricHistoryQueryRepository(
    private val writer: FabricHistoryOutboxWriter,
    private val sessionId: Uuid?,
    private val playerIdentities: ServerPlayerIdentityStore,
) : HistoryQueryRepository {
    /**
     * 讀取經 SQL 參與者條件限制的摘要頁。
     *
     * @param access 可信連線身分。
     * @param request 已驗證清單要求。
     * @return 不含牌面或原始 payload 的結果。
     */
    override suspend fun list(access: HistoryQueryAccess, request: HistoryListRequest): HistoryQueryResult<HistoryListPage> {
        val participantIds = request.filters.playerName?.let { playerIdentities.findPlayerIdsByName(it).mapTo(mutableSetOf()) { id -> id.toString() } }
        if (participantIds != null && participantIds.size > MAX_HISTORY_NAME_MATCHES) return queryFailure(HistoryQueryErrorCode.CONTENT_TOO_LARGE)
        return writer.queryList(access, request, sessionId, participantIds).mapQueryResult { page ->
            HistoryListPage(page.entries.map { it.toDomainSummary() }, page.nextCursor?.toDomainCursor(request))
        }
    }

    /**
     * 取得同場安全摘要與局級時間索引。
     *
     * @param access 可信連線身分。
     * @param request 已驗證摘要要求。
     * @return 未公開與未授權共用不可取得錯誤。
     */
    override suspend fun summary(access: HistoryQueryAccess, request: HistorySummaryRequest): HistoryQueryResult<HistoryMatchDetail> {
        return when (val result = writer.querySummary(access, request, sessionId).mapQueryResult { it.entries.singleOrNull() }) {
            is HistoryQueryResult.Failure -> result
            is HistoryQueryResult.Success -> {
                val entry = result.value ?: return queryFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
                HistoryQueryResult.Success(
                    HistoryMatchDetail(
                        entry.toDomainSummary(),
                        entry.rounds.map { HistoryRoundSummary(it.roundNumber, it.startedAtEpochMillis, it.endedAtEpochMillis) },
                    ),
                )
            }
        }
    }

    /**
     * 讀取已授權完整 Replay 的開局規則設定，不回傳 Replay 事件內容。
     *
     * @param access 可信連線身分。
     * @param request 已通過 Flow 驗證的規則設定要求。
     * @return Replay 開局時保存的設定，或穩定查詢錯誤。
     */
    override suspend fun ruleSettings(
        access: HistoryQueryAccess,
        request: HistoryRuleSettingsRequest,
    ): HistoryQueryResult<HistoryRuleSettings> {
        when (val authorized = writer.querySummary(access, HistorySummaryRequest(request.matchId, request.scope), sessionId).mapQueryResult { it.entries.singleOrNull() }) {
            is HistoryQueryResult.Failure -> return authorized
            is HistoryQueryResult.Success -> {
                val summary = authorized.value ?: return queryFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
                if (!summary.toDomainSummary().resultsAvailable) return queryFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
            }
        }
        return writer.readRuleSettings(request.matchId, MAX_RULE_SETTINGS_REPLAY_BYTES, sessionId)
            .mapQueryResult(::HistoryRuleSettings)
    }
}

/**
 * 將已授權要求映射為資料庫明確的查詢條件。
 *
 * @param access 可信查詢身分。
 * @param excluded 仍在進行或轉移中的對局。
 * @param participantIds 名稱解析出的玩家 UUID；null 不限，空集合不匹配任何對局。
 * @return 同時含身分限制與游標的 SQL 要求。
 */
internal fun HistoryListRequest.toSqliteQuery(
    access: HistoryQueryAccess,
    excluded: Set<String>,
    participantIds: Set<String>? = null,
): SqliteHistoryQuery = SqliteHistoryQuery(
    playerId = access.principalId.toString(),
    includeAll = scope == HistoryQueryScope.ALL,
    sortField = when (sortField) {
        HistorySortField.ENDED_AT -> SqliteHistorySortField.ENDED_AT
        HistorySortField.DURATION -> SqliteHistorySortField.DURATION
        HistorySortField.OWN_RANK -> SqliteHistorySortField.OWN_RANK
        HistorySortField.OWN_SCORE -> SqliteHistorySortField.OWN_SCORE
    },
    sortDirection = when (sortDirection) {
        HistorySortDirection.ASC -> SqliteHistorySortDirection.ASC
        HistorySortDirection.DESC -> SqliteHistorySortDirection.DESC
    },
    ruleId = filters.ruleId,
    outcome = when (filters.outcome) {
        HistoryOutcomeFilter.COMPLETED -> SqliteHistoryOutcomeFilter.NORMAL_COMPLETED
        HistoryOutcomeFilter.INTERRUPTED -> SqliteHistoryOutcomeFilter.INTERRUPTED
        null -> null
    },
    aiFilter = when (filters.ai) {
        HistoryAiFilter.CONTAINS_AI -> SqliteHistoryAiFilter.CONTAINS_AI
        HistoryAiFilter.NO_AI -> SqliteHistoryAiFilter.NO_AI
        null -> null
    },
    integrityFilter = when (filters.integrity) {
        HistoryIntegrityFilter.COMPLETE -> SqliteHistoryIntegrityFilter.COMPLETE
        HistoryIntegrityFilter.INCOMPLETE -> SqliteHistoryIntegrityFilter.INCOMPLETE
        null -> null
    },
    endedAtLowerInclusive = filters.endedAtFromEpochMillis,
    endedAtUpperExclusive = filters.endedAtBeforeEpochMillis,
    minimumRank = filters.ownRank?.minimum,
    maximumRank = filters.ownRank?.maximum,
    pageSize = pageSize,
    cursor = cursor?.let { SqliteHistoryCursor(it.sortValue.numericValue, it.matchId.toString(), it.sortValue.nullBucket) },
    excludedMatchIds = excluded,
    matchId = filters.matchId?.let { Uuid.parse(it).toString() },
    participantIds = participantIds,
)

/**
 * 保存下一頁的完整查詢上下文。
 *
 * @param request 產生游標的要求。
 * @return 不含授權資格的 Flow 游標。
 */
private fun SqliteHistoryCursor.toDomainCursor(request: HistoryListRequest): HistoryQueryCursor = HistoryQueryCursor(
    HistorySortValue(sortValue, nullBucket),
    Uuid.parse(matchId),
    request.scope,
    request.sortField,
    request.sortDirection,
    request.filters,
)

/**
 * 將 SQL 中已證實的資料映射為安全公開資訊。
 *
 * @return 不推測缺少的時間、參與者或結算資料。
 */
private fun SqliteHistoryQueryEntry.toDomainSummary(): HistoryMatchSummary = HistoryMatchSummary(
    matchId = Uuid.parse(matchId),
    ruleId = ruleId,
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
    duration = durationMillis?.milliseconds,
    outcome = if (outcome == SqliteHistoryMatchOutcome.COMPLETED) HistoryOutcomeFilter.COMPLETED else HistoryOutcomeFilter.INTERRUPTED,
    integrity = if (state == HistoryStoredMatchState.COMPLETED) HistoryIntegrityFilter.COMPLETE else HistoryIntegrityFilter.INCOMPLETE,
    resultsAvailable = state == HistoryStoredMatchState.COMPLETED,
    participants = participants.map { HistoryParticipantSummary(it.seatIndex, Uuid.parse(it.playerId), it.aiStrategyId) },
    roundCount = rounds.size.takeIf { state == HistoryStoredMatchState.COMPLETED },
    results = participants.map {
        HistoryResultSummary(
            playerId = Uuid.parse(it.playerId),
            finalScore = it.finalScore,
            finalRank = it.finalRank,
        )
    },
)

/**
 * 將 writer 的生命週期結果轉為不洩漏內部診斷的查詢結果。
 *
 * @param mapper 成功資料的映射。
 * @return 成功值或穩定錯誤碼。
 */
private inline fun <T, R> HistoryManagementResult<T>.mapQueryResult(mapper: (T) -> R): HistoryQueryResult<R> = when (this) {
    is HistoryManagementResult.Success -> HistoryQueryResult.Success(mapper(value))
    is HistoryManagementResult.Busy -> queryFailure(HistoryQueryErrorCode.BUSY)
    is HistoryManagementResult.Disconnected, HistoryManagementResult.SessionChanged -> queryFailure(HistoryQueryErrorCode.DISCONNECTED)
    is HistoryManagementResult.Failed -> queryFailure(HistoryQueryErrorCode.NOT_AVAILABLE)
}

/**
 * 建立不包含資料庫例外內容的安全失敗。
 *
 * @param code 穩定失敗代碼。
 * @return 僅供 Flow 使用的英文診斷與錯誤碼。
 */
private fun queryFailure(code: HistoryQueryErrorCode): HistoryQueryResult.Failure = HistoryQueryResult.Failure(HistoryQueryError(code, "History query failed: ${code.name}"))

/** 名稱比對可展開的最大玩家數，避免 SQL 參數數量超出查詢限制。 */
private const val MAX_HISTORY_NAME_MATCHES = 512

/** 規則設定查詢允許解析的完整 Replay 上限；不等同於線路回應大小上限。 */
private const val MAX_RULE_SETTINGS_REPLAY_BYTES = 8 * 1024 * 1024
