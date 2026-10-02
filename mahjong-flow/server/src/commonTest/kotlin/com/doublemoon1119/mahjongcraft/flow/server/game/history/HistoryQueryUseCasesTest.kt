package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryAiFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListPage
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryCursor
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryError
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryErrorCode
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryFilters
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRankRange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRuleSettings
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRuleSettingsRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortDirection
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortField
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortValue
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySummaryRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ListHistoryUseCase] 與 [GetHistorySummaryUseCase] 的授權邊界測試。 */
class HistoryQueryUseCasesTest {
    /** 驗證非管理員不能查詢全部歷史。 */
    @Test
    fun `test all scope requires administrator`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = ListHistoryUseCase(repository) { HistoryQueryPolicy() }
        val result = useCase(HistoryQueryAccess(Uuid.random(), false), HistoryListRequest(HistoryQueryScope.ALL))
        assertTrue(result is HistoryQueryResult.Failure, "Expected a denied history query")
        assertEquals("ACCESS_DENIED", result.error.code.name)
        assertEquals(0, repository.listCalls)
    }

    /** 驗證停用查詢時即使管理員也不能取得資料。 */
    @Test
    fun `test disabled policy rejects own query`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = ListHistoryUseCase(repository) { HistoryQueryPolicy(queryEnabled = false) }
        val result = useCase(HistoryQueryAccess(Uuid.random(), false), HistoryListRequest())
        assertTrue(result is HistoryQueryResult.Failure, "Expected a disabled history query")
        assertEquals("QUERY_DISABLED", result.error.code.name)
        assertEquals(0, repository.listCalls)
    }

    /** 驗證摘要查詢將範圍政策傳遞到同一授權邊界。 */
    @Test
    fun `test summary all scope requires administrator`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = GetHistorySummaryUseCase(repository) { HistoryQueryPolicy() }
        val result = useCase(
            HistoryQueryAccess(Uuid.random(), false),
            HistorySummaryRequest(Uuid.random(), HistoryQueryScope.ALL),
        )
        assertTrue(result is HistoryQueryResult.Failure, "Expected a denied summary query")
        assertEquals("ACCESS_DENIED", result.error.code.name)
        assertEquals(0, repository.summaryCalls)
    }

    /** 驗證規則設定查詢沿用摘要查詢的範圍授權邊界。 */
    @Test
    fun `test rule settings all scope requires administrator`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = GetHistoryRuleSettingsUseCase(repository) { HistoryQueryPolicy() }
        val result = useCase(
            HistoryQueryAccess(Uuid.random(), false),
            HistoryRuleSettingsRequest(Uuid.random(), HistoryQueryScope.ALL),
        )
        assertError(result, HistoryQueryErrorCode.ACCESS_DENIED)
        assertEquals(0, repository.ruleSettingsCalls)
    }

    /** 驗證規則設定查詢停用時不會觸發 repository。 */
    @Test
    fun `test rule settings disabled policy rejects own query`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = GetHistoryRuleSettingsUseCase(repository) { HistoryQueryPolicy(queryEnabled = false) }
        val result = useCase(
            HistoryQueryAccess(Uuid.random(), false),
            HistoryRuleSettingsRequest(Uuid.random()),
        )
        assertError(result, HistoryQueryErrorCode.QUERY_DISABLED)
        assertEquals(0, repository.ruleSettingsCalls)
    }

    /** 驗證合法 OWN 規則設定查詢會傳遞授權身分並回傳完整設定。 */
    @Test
    fun `test valid own rule settings query forwards access and config`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val access = HistoryQueryAccess(Uuid.random(), false)
        val result = GetHistoryRuleSettingsUseCase(repository) { HistoryQueryPolicy() }(
            access,
            HistoryRuleSettingsRequest(Uuid.random()),
        )
        assertTrue(result is HistoryQueryResult.Success, "Expected rule settings query to succeed")
        assertEquals(access, repository.lastRuleSettingsAccess)
        assertEquals(GameConfig(RiichiRuleConfig()), result.value.config)
    }

    /** 驗證管理員政策關閉時，規則設定 ALL 查詢也會被拒絕。 */
    @Test
    fun `test rule settings administrator policy denial`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val result = GetHistoryRuleSettingsUseCase(repository) {
            HistoryQueryPolicy(allowAdministratorQuery = false)
        }(
            HistoryQueryAccess(Uuid.random(), true),
            HistoryRuleSettingsRequest(Uuid.random(), HistoryQueryScope.ALL),
        )
        assertError(result, HistoryQueryErrorCode.ACCESS_DENIED)
        assertEquals(0, repository.ruleSettingsCalls)
    }

    /** 驗證管理員查詢政策關閉時，管理員也不能查詢全部歷史。 */
    @Test
    fun `test administrator all scope respects policy`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = ListHistoryUseCase(repository) { HistoryQueryPolicy(allowAdministratorQuery = false) }
        val result = useCase(HistoryQueryAccess(Uuid.random(), true), HistoryListRequest(HistoryQueryScope.ALL))
        assertError(result, HistoryQueryErrorCode.ACCESS_DENIED)
        assertEquals(0, repository.listCalls)
    }

    /** 驗證合法 OWN 查詢將受信任連線身分傳給 repository。 */
    @Test
    fun `test valid own query forwards access`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val access = HistoryQueryAccess(Uuid.random(), false)
        val result = ListHistoryUseCase(repository) { HistoryQueryPolicy() }(access, HistoryListRequest())
        assertTrue(result is HistoryQueryResult.Success, "Expected valid own query to reach repository")
        assertEquals(access, repository.lastListAccess)
    }

    /** 驗證 ALL 查詢拒絕所有只適用於目前玩家的排序與篩選。 */
    @Test
    fun `test all scope rejects own ranking controls`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = ListHistoryUseCase(repository) { HistoryQueryPolicy() }
        listOf(
            HistoryListRequest(HistoryQueryScope.ALL, HistorySortField.OWN_RANK),
            HistoryListRequest(HistoryQueryScope.ALL, HistorySortField.OWN_SCORE),
            HistoryListRequest(
                scope = HistoryQueryScope.ALL,
                filters = HistoryQueryFilters(ownRank = HistoryRankRange(minimum = 1)),
            ),
        ).forEach { request ->
            assertError(useCase(HistoryQueryAccess(Uuid.random(), true), request), HistoryQueryErrorCode.INVALID_REQUEST)
        }
        assertEquals(0, repository.listCalls)
    }

    /** 驗證清單要求的頁大小上下界。 */
    @Test
    fun `test page size bounds are enforced`() {
        assertFailsWith<IllegalArgumentException> { HistoryListRequest(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { HistoryListRequest(pageSize = 51) }
    }

    /** 驗證規則 ID、時間範圍與名次範圍的格式檢查。 */
    @Test
    fun `test list filter bounds are enforced`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = ListHistoryUseCase(repository) { HistoryQueryPolicy() }
        val access = HistoryQueryAccess(Uuid.random(), false)
        val invalidRequests = listOf(
            HistoryListRequest(filters = HistoryQueryFilters(ruleId = "not-valid")),
            HistoryListRequest(filters = HistoryQueryFilters(endedAtFromEpochMillis = 20, endedAtBeforeEpochMillis = 20)),
            HistoryListRequest(filters = HistoryQueryFilters(ownRank = HistoryRankRange(minimum = 0))),
            HistoryListRequest(filters = HistoryQueryFilters(ownRank = HistoryRankRange(minimum = 3, maximum = 2))),
            HistoryListRequest(filters = HistoryQueryFilters(playerName = " ")),
            HistoryListRequest(filters = HistoryQueryFilters(playerName = "name\u0000")),
            HistoryListRequest(filters = HistoryQueryFilters(matchId = "not-a-uuid")),
        )
        invalidRequests.forEach { request -> assertError(useCase(access, request), HistoryQueryErrorCode.INVALID_REQUEST) }
        assertEquals(0, repository.listCalls)
        assertFailsWith<IllegalArgumentException> { HistoryQueryFilters(playerName = "x".repeat(17)) }
    }

    /** 驗證 cursor 的範圍、欄位、方向與篩選條件不一致時都會被拒絕。 */
    @Test
    fun `test cursor context mismatches are rejected`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = ListHistoryUseCase(repository) { HistoryQueryPolicy() }
        val access = HistoryQueryAccess(Uuid.random(), false)
        val filters = HistoryQueryFilters()
        val base = HistoryQueryCursor(
            sortValue = HistorySortValue(10, false),
            matchId = Uuid.random(),
            scope = HistoryQueryScope.OWN,
            sortField = HistorySortField.ENDED_AT,
            sortDirection = HistorySortDirection.DESC,
            filters = filters,
        )
        val requests = listOf(
            HistoryListRequest(cursor = base.copy(scope = HistoryQueryScope.ALL)),
            HistoryListRequest(sortField = HistorySortField.DURATION, cursor = base),
            HistoryListRequest(sortDirection = HistorySortDirection.ASC, cursor = base),
            HistoryListRequest(filters = HistoryQueryFilters(ai = HistoryAiFilter.CONTAINS_AI), cursor = base),
        )
        requests.forEach { request -> assertError(useCase(access, request), HistoryQueryErrorCode.INVALID_REQUEST) }
        assertEquals(0, repository.listCalls)
    }

    /** 驗證查詢條件一致的 cursor 可以延續分頁。 */
    @Test
    fun `test matching cursor is accepted`() = runTest {
        val repository = RecordingHistoryQueryRepository()
        val useCase = ListHistoryUseCase(repository) { HistoryQueryPolicy() }
        val access = HistoryQueryAccess(Uuid.random(), false)
        val request = HistoryListRequest(
            cursor = HistoryQueryCursor(
                sortValue = HistorySortValue(10, false),
                matchId = Uuid.random(),
                scope = HistoryQueryScope.OWN,
                sortField = HistorySortField.ENDED_AT,
                sortDirection = HistorySortDirection.DESC,
                filters = HistoryQueryFilters(),
            ),
        )
        assertTrue(useCase(access, request) is HistoryQueryResult.Success, "Expected matching cursor to be accepted")
        assertEquals(1, repository.listCalls)
    }

    /**
     * 驗證查詢失敗結果使用指定的穩定錯誤代碼。
     *
     * @param result 實際查詢結果。
     * @param expectedCode 預期的穩定錯誤代碼。
     */
    private fun assertError(
        result: HistoryQueryResult<*>,
        expectedCode: HistoryQueryErrorCode,
    ) {
        assertTrue(result is HistoryQueryResult.Failure, "Expected query failure but got $result")
        assertEquals(expectedCode, result.error.code)
    }

    /** 記錄用的最小查詢 repository。
     *
     * @property listCalls 清單查詢呼叫次數。
     * @property summaryCalls 摘要查詢呼叫次數。
     * @property ruleSettingsCalls 規則設定查詢呼叫次數。
     * @property lastListAccess 最近一次清單查詢的授權身分。
     */
    private class RecordingHistoryQueryRepository : HistoryQueryRepository {
        var listCalls: Int = 0
        var summaryCalls: Int = 0
        var ruleSettingsCalls: Int = 0
        var lastRuleSettingsAccess: HistoryQueryAccess? = null
        var lastListAccess: HistoryQueryAccess? = null

        /** 回傳空的成功頁並記錄授權身分。
         * @param access 查詢授權身分。
         * @param request 清單查詢要求。
         * @return 空的成功頁。
         */
        override suspend fun list(
            access: HistoryQueryAccess,
            request: HistoryListRequest,
        ): HistoryQueryResult<HistoryListPage> {
            listCalls++
            lastListAccess = access
            return HistoryQueryResult.Success(HistoryListPage(emptyList(), null))
        }

        /** 回傳未找到結果，供授權測試驗證不應觸發此方法。
         * @param access 查詢授權身分。
         * @param request 摘要查詢要求。
         * @return 測試用查詢失敗。
         */
        override suspend fun summary(
            access: HistoryQueryAccess,
            request: HistorySummaryRequest,
        ): HistoryQueryResult<HistoryMatchDetail> {
            summaryCalls++
            return HistoryQueryResult.Failure(
                HistoryQueryError(HistoryQueryErrorCode.NOT_AVAILABLE, "No test history"),
            )
        }

        /** 回傳成功設定，供授權測試驗證允許的要求會抵達此方法。
         * @param access 查詢授權身分。
         * @param request 規則設定查詢要求。
         * @return 測試用開局設定。
         */
        override suspend fun ruleSettings(
            access: HistoryQueryAccess,
            request: HistoryRuleSettingsRequest,
        ): HistoryQueryResult<HistoryRuleSettings> {
            ruleSettingsCalls++
            lastRuleSettingsAccess = access
            return HistoryQueryResult.Success(
                HistoryRuleSettings(
                    GameConfig(RiichiRuleConfig()),
                ),
            )
        }
    }
}
