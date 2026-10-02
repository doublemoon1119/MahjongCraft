package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListPage
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryError
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryErrorCode
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySummaryRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.MAX_HISTORY_PAGE_SIZE
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.MAX_HISTORY_PLAYER_NAME_LENGTH
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.MAX_HISTORY_RULE_ID_LENGTH
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.MIN_HISTORY_PAGE_SIZE
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/** 伺服器端歷史查詢的授權與參數驗證共用邏輯。
 * @param access 可信任的發起者資訊。
 * @param policy 當前查詢政策。
 * @param scope 要求的查詢範圍。
 * @return 不可執行時的錯誤；可執行時為 null。
 */
private fun validateHistoryAccess(
    access: HistoryQueryAccess,
    policy: HistoryQueryPolicy,
    scope: HistoryQueryScope,
): HistoryQueryError? = when {
    !policy.queryEnabled -> HistoryQueryError(HistoryQueryErrorCode.QUERY_DISABLED, "History queries are disabled")
    scope == HistoryQueryScope.ALL && (!access.isAdministrator || !policy.allowAdministratorQuery) ->
        HistoryQueryError(HistoryQueryErrorCode.ACCESS_DENIED, "Administrator history access is not allowed")
    else -> null
}

/** 檢查歷史清單要求的範圍、排序限制與游標一致性。
 * @param request 清單查詢要求。
 * @return 不可執行時的錯誤；可執行時為 null。
 */
private fun validateHistoryListRequest(
    request: HistoryListRequest,
): HistoryQueryError? {
    if (request.pageSize !in MIN_HISTORY_PAGE_SIZE..MAX_HISTORY_PAGE_SIZE) {
        return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History page size is out of range")
    }
    if (request.filters.ruleId?.let { it.length !in 1..MAX_HISTORY_RULE_ID_LENGTH || !NamespacedId.isValid(it) } == true) {
        return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History rule ID is out of range")
    }
    request.filters.playerName?.let { name ->
        if (name.isBlank() || name != name.trim() || name.length > MAX_HISTORY_PLAYER_NAME_LENGTH || name.any(Char::isISOControl)) {
            return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History player name filter is invalid")
        }
    }
    request.filters.matchId?.let { matchId ->
        if (runCatching { Uuid.parse(matchId) }.isFailure) {
            return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History match ID filter is invalid")
        }
    }
    if (request.scope == HistoryQueryScope.ALL &&
        (
            request.sortField == HistorySortField.OWN_RANK ||
                request.sortField == HistorySortField.OWN_SCORE ||
                request.filters.ownRank != null
            )
    ) {
        return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "Own-player sorting and filters require OWN scope")
    }
    request.filters.let { filters ->
        val from = filters.endedAtFromEpochMillis
        val before = filters.endedAtBeforeEpochMillis
        if (from != null && before != null && from >= before
        ) {
            return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History end-time range is invalid")
        }
        filters.ownRank?.let { range ->
            val minimum = range.minimum
            val maximum = range.maximum
            if (minimum == null && maximum == null) {
                return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History rank range is empty")
            }
            if (minimum != null &&
                minimum < 1 ||
                maximum != null &&
                maximum < 1 ||
                minimum != null &&
                maximum != null &&
                minimum > maximum
            ) {
                return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History rank range is invalid")
            }
        }
    }
    request.cursor?.let { cursor ->
        if (cursor.scope != request.scope ||
            cursor.sortField != request.sortField ||
            cursor.sortDirection != request.sortDirection ||
            cursor.filters != request.filters
        ) {
            return HistoryQueryError(HistoryQueryErrorCode.INVALID_REQUEST, "History cursor does not match the request")
        }
    }
    return null
}

/** 以授權範圍查詢歷史清單的一次性伺服器用例。
 * @property repository 提供已授權資料邊界的 repository。
 * @property policyProvider 讀取當前伺服器查詢政策的函式。
 */
class ListHistoryUseCase(
    private val repository: HistoryQueryRepository,
    private val policyProvider: () -> HistoryQueryPolicy,
) {
    /** 執行一次歷史清單查詢。
     * @param access 可信任的發起者資訊。
     * @param request 清單查詢要求。
     * @return 查詢頁或穩定錯誤。
     */
    suspend operator fun invoke(
        access: HistoryQueryAccess,
        request: HistoryListRequest,
    ): HistoryQueryResult<HistoryListPage> {
        val policy = policyProvider()
        validateHistoryAccess(access, policy, request.scope)?.let { return HistoryQueryResult.Failure(it) }
        validateHistoryListRequest(request)?.let { return HistoryQueryResult.Failure(it) }
        return repository.list(access, request)
    }
}

/** 以授權範圍查詢單一歷史摘要的一次性伺服器用例。
 * @property repository 提供已授權資料邊界的 repository。
 * @property policyProvider 讀取當前伺服器查詢政策的函式。
 */
class GetHistorySummaryUseCase(
    private val repository: HistoryQueryRepository,
    private val policyProvider: () -> HistoryQueryPolicy,
) {
    /** 執行一次歷史摘要查詢。
     * @param access 可信任的發起者資訊。
     * @param request 摘要查詢要求。
     * @return 對局詳情或穩定錯誤。
     */
    suspend operator fun invoke(
        access: HistoryQueryAccess,
        request: HistorySummaryRequest,
    ): HistoryQueryResult<HistoryMatchDetail> {
        val policy = policyProvider()
        validateHistoryAccess(access, policy, request.scope)?.let { return HistoryQueryResult.Failure(it) }
        return repository.summary(access, request)
    }
}
