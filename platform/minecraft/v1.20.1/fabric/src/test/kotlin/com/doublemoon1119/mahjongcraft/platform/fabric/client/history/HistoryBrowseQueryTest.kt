package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryAiFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證歷史瀏覽查詢的正規化、轉換及表單篩選解析。 */
class HistoryBrowseQueryTest {
    /** 全部對局查詢會清除玩家名次條件並回復結束時間排序。 */
    @Test
    fun `test all scope normalizes own filters and sorting`() {
        val query = HistoryBrowseQuery(
            scope = HistoryQueryScopeDto.ALL,
            sortField = HistorySortFieldDto.OWN_SCORE,
            sortDirection = HistorySortDirectionDto.ASC,
            filters = HistoryQueryFiltersDto(ownRankMin = 2, ownRankMax = 4, ruleId = "mahjongcraft:riichi"),
        )

        assertEquals(
            HistoryBrowseQuery(
                scope = HistoryQueryScopeDto.ALL,
                sortField = HistorySortFieldDto.ENDED_AT,
                sortDirection = HistorySortDirectionDto.ASC,
                filters = HistoryQueryFiltersDto(ruleId = "mahjongcraft:riichi"),
            ),
            query.normalized(),
        )
    }

    /** 查詢轉換會保留條件、游標與固定頁大小。 */
    @Test
    fun `test query converts to request`() {
        val query = HistoryBrowseQuery(
            scope = HistoryQueryScopeDto.OWN,
            sortField = HistorySortFieldDto.DURATION,
            filters = HistoryQueryFiltersDto(outcome = HistoryOutcomeFilterDto.COMPLETED),
        )

        val request = query.toRequest("next")
        assertEquals("", request.requestId)
        assertEquals(20, request.pageSize)
        assertEquals("next", request.cursor)
        assertEquals(query.filters, request.filters)
    }

    /** 完整有效輸入會產生包含日期界線與各類別篩選的 DTO。 */
    @Test
    fun `test valid filters parse with local date bounds`() {
        val result = HistoryBrowseFilterValidation.parse(
            HistoryBrowseFilterInput(
                ruleId = "mahjongcraft:riichi",
                outcome = HistoryOutcomeFilterDto.COMPLETED,
                integrity = HistoryIntegrityFilterDto.COMPLETE,
                ai = HistoryAiFilterDto.NO_AI,
                fromDate = "2024-02-29",
                throughDate = "2024-03-01",
                ownRankMin = "1",
                ownRankMax = "10000",
            ),
            ZoneId.of("UTC"),
            HistoryQueryScopeDto.OWN,
        )
        val valid = assertIs<HistoryBrowseFilterResult.Valid>(result)
        assertEquals("mahjongcraft:riichi", valid.filters.ruleId)
        assertEquals(LocalDate.of(2024, 2, 29).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), valid.filters.endedAtFromEpochMillis)
        assertEquals(LocalDate.of(2024, 3, 2).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), valid.filters.endedAtBeforeEpochMillis)
        assertEquals(10000, valid.filters.ownRankMax)
    }

    /** 日期解析會拒絕不存在日期、非 ISO 格式及日期上界溢位。 */
    @Test
    fun `test invalid dates are reported`() {
        val result = HistoryBrowseFilterValidation.parse(
            HistoryBrowseFilterInput(fromDate = "2023-02-29", throughDate = "+999999999-12-31"),
            ZoneId.of("UTC"),
            HistoryQueryScopeDto.OWN,
        )
        val invalid = assertIs<HistoryBrowseFilterResult.Invalid>(result)
        assertEquals(HistoryBrowseFilterError.INVALID_DATE, invalid.errors[HistoryBrowseFilterField.FROM_DATE])
        assertEquals(HistoryBrowseFilterError.INVALID_DATE, invalid.errors[HistoryBrowseFilterField.THROUGH_DATE])
    }

    /** 日期界線會在夏令時間區域採用當地日的實際起始時間。 */
    @Test
    fun `test date bounds respect daylight saving time`() {
        val zone = ZoneId.of("America/New_York")
        val result = assertIs<HistoryBrowseFilterResult.Valid>(
            HistoryBrowseFilterValidation.parse(
                HistoryBrowseFilterInput(fromDate = "2024-03-10", throughDate = "2024-03-10"),
                zone,
                HistoryQueryScopeDto.OWN,
            ),
        )
        val start = LocalDate.of(2024, 3, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = LocalDate.of(2024, 3, 11).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(start, result.filters.endedAtFromEpochMillis)
        assertEquals(next, result.filters.endedAtBeforeEpochMillis)
        assertEquals(23 * 60 * 60 * 1000L, next - start)
    }

    /** 日期下界晚於日期上界時會回報兩個日期欄位的反轉範圍。 */
    @Test
    fun `test reversed date range is reported`() {
        val result = HistoryBrowseFilterValidation.parse(
            HistoryBrowseFilterInput(fromDate = "2024-03-02", throughDate = "2024-03-01"),
            ZoneId.of("UTC"),
            HistoryQueryScopeDto.OWN,
        )
        val invalid = assertIs<HistoryBrowseFilterResult.Invalid>(result)
        assertEquals(HistoryBrowseFilterError.REVERSED_RANGE, invalid.errors[HistoryBrowseFilterField.FROM_DATE])
        assertEquals(HistoryBrowseFilterError.REVERSED_RANGE, invalid.errors[HistoryBrowseFilterField.THROUGH_DATE])
    }

    /** 規則識別碼及名次文字錯誤會各自回報欄位錯誤。 */
    @Test
    fun `test invalid rule and ranks are reported`() {
        val result = HistoryBrowseFilterValidation.parse(
            HistoryBrowseFilterInput(ruleId = "Riichi", ownRankMin = "0", ownRankMax = "999999999999999999999"),
            ZoneId.of("UTC"),
            HistoryQueryScopeDto.OWN,
        )
        val invalid = assertIs<HistoryBrowseFilterResult.Invalid>(result)
        assertEquals(HistoryBrowseFilterError.INVALID_RULE, invalid.errors[HistoryBrowseFilterField.RULE])
        assertEquals(HistoryBrowseFilterError.INVALID_RANK, invalid.errors[HistoryBrowseFilterField.MIN_RANK])
        assertEquals(HistoryBrowseFilterError.INVALID_RANK, invalid.errors[HistoryBrowseFilterField.MAX_RANK])
    }

    /** 名次下界大於上界時會回報反轉範圍。 */
    @Test
    fun `test reversed rank range is reported`() {
        val result = HistoryBrowseFilterValidation.parse(
            HistoryBrowseFilterInput(ownRankMin = "4", ownRankMax = "2"),
            ZoneId.of("UTC"),
            HistoryQueryScopeDto.OWN,
        )
        val invalid = assertIs<HistoryBrowseFilterResult.Invalid>(result)
        assertEquals(HistoryBrowseFilterError.REVERSED_RANGE, invalid.errors[HistoryBrowseFilterField.MIN_RANK])
        assertEquals(HistoryBrowseFilterError.REVERSED_RANGE, invalid.errors[HistoryBrowseFilterField.MAX_RANK])
    }

    /** 全部對局範圍會忽略名次文字，不因其無效而拒絕其他篩選。 */
    @Test
    fun `test all scope ignores rank fields`() {
        val result = HistoryBrowseFilterValidation.parse(
            HistoryBrowseFilterInput(ownRankMin = "not-a-rank", ownRankMax = "0"),
            ZoneId.of("UTC"),
            HistoryQueryScopeDto.ALL,
        )
        val valid = assertIs<HistoryBrowseFilterResult.Valid>(result)
        assertEquals(null, valid.filters.ownRankMin)
        assertEquals(null, valid.filters.ownRankMax)
    }
}
