package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryAiFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 歷史瀏覽表單的文字篩選輸入。
 *
 * @property ruleId 規則模組的 namespaced ID；空白值表示不限制規則。
 * @property outcome 對局結果篩選。
 * @property integrity 對局完整性篩選。
 * @property ai AI 座位篩選。
 * @property fromDate 包含的本地日期下界，格式為 ISO 日期。
 * @property throughDate 包含的本地日期上界，格式為 ISO 日期。
 * @property ownRankMin 查詢玩家可接受的最小名次文字。
 * @property ownRankMax 查詢玩家可接受的最大名次文字。
 */
internal data class HistoryBrowseFilterInput(
    val ruleId: String = "",
    val outcome: HistoryOutcomeFilterDto? = null,
    val integrity: HistoryIntegrityFilterDto? = null,
    val ai: HistoryAiFilterDto? = null,
    val fromDate: String = "",
    val throughDate: String = "",
    val ownRankMin: String = "",
    val ownRankMax: String = "",
)

/** 歷史瀏覽篩選欄位。 */
internal enum class HistoryBrowseFilterField {
    /** 規則模組欄位。 */
    RULE,

    /** 日期下界欄位。 */
    FROM_DATE,

    /** 日期上界欄位。 */
    THROUGH_DATE,

    /** 名次下界欄位。 */
    MIN_RANK,

    /** 名次上界欄位。 */
    MAX_RANK,
}

/** 歷史瀏覽篩選欄位的穩定驗證錯誤。 */
internal enum class HistoryBrowseFilterError {
    /** 規則識別碼不是合法的 namespaced ID。 */
    INVALID_RULE,

    /** 日期不是合法的 ISO 日期或無法轉換。 */
    INVALID_DATE,

    /** 名次不是正整數。 */
    INVALID_RANK,

    /** 下界大於上界。 */
    REVERSED_RANGE,
}

/** 歷史瀏覽篩選解析結果。
 */
internal sealed interface HistoryBrowseFilterResult {
    /** 通過驗證並可送出的篩選條件。
     *
     * @property filters DTO 篩選條件。
     */
    data class Valid(val filters: HistoryQueryFiltersDto) : HistoryBrowseFilterResult

    /** 含有欄位錯誤的篩選條件。
     *
     * @property errors 欄位與錯誤的對應表。
     */
    data class Invalid(val errors: Map<HistoryBrowseFilterField, HistoryBrowseFilterError>) : HistoryBrowseFilterResult
}

/** 驗證並轉換歷史瀏覽篩選表單。
 */
internal object HistoryBrowseFilterValidation {
    /** 驗證表單並建立線路 DTO。
     *
     * @param input 使用者輸入的文字篩選條件。
     * @param zone 日期文字所使用的時區。
     * @param scope 查詢的資料範圍；全部對局不套用名次欄位。
     * @return 通過驗證的 DTO，或每個失敗欄位的穩定錯誤。
     */
    fun parse(
        input: HistoryBrowseFilterInput,
        zone: ZoneId,
        scope: HistoryQueryScopeDto,
    ): HistoryBrowseFilterResult {
        val errors = linkedMapOf<HistoryBrowseFilterField, HistoryBrowseFilterError>()
        val ruleId = input.ruleId.trim().takeUnless { it.isEmpty() }
        if (ruleId != null && !NamespacedId.isValid(ruleId)) errors[HistoryBrowseFilterField.RULE] = HistoryBrowseFilterError.INVALID_RULE

        val fromDate = parseDate(input.fromDate, zone, errors, HistoryBrowseFilterField.FROM_DATE)
        val throughDate = parseDate(input.throughDate, zone, errors, HistoryBrowseFilterField.THROUGH_DATE, exclusiveNextDay = true)
        if (fromDate != null && throughDate != null && fromDate >= throughDate) {
            errors[HistoryBrowseFilterField.FROM_DATE] = HistoryBrowseFilterError.REVERSED_RANGE
            errors[HistoryBrowseFilterField.THROUGH_DATE] = HistoryBrowseFilterError.REVERSED_RANGE
        }
        val minimum = if (scope == HistoryQueryScopeDto.ALL) null else parseRank(input.ownRankMin, errors, HistoryBrowseFilterField.MIN_RANK)
        val maximum = if (scope == HistoryQueryScopeDto.ALL) null else parseRank(input.ownRankMax, errors, HistoryBrowseFilterField.MAX_RANK)
        if (scope != HistoryQueryScopeDto.ALL && minimum != null && maximum != null && minimum > maximum) {
            errors[HistoryBrowseFilterField.MIN_RANK] = HistoryBrowseFilterError.REVERSED_RANGE
            errors[HistoryBrowseFilterField.MAX_RANK] = HistoryBrowseFilterError.REVERSED_RANGE
        }
        if (errors.isNotEmpty()) return HistoryBrowseFilterResult.Invalid(errors)
        return HistoryBrowseFilterResult.Valid(
            HistoryQueryFiltersDto(
                ruleId = ruleId,
                outcome = input.outcome,
                integrity = input.integrity,
                ai = input.ai,
                endedAtFromEpochMillis = fromDate,
                endedAtBeforeEpochMillis = throughDate,
                ownRankMin = minimum,
                ownRankMax = maximum,
            ),
        )
    }

    /** 解析本地日期並轉換為時區中的 epoch milliseconds。
     *
     * @param value 日期文字。
     * @param zone 日期使用的時區。
     * @param errors 驗證錯誤輸出表。
     * @param field 發生錯誤的欄位。
     * @param exclusiveNextDay 是否將日期轉換為下一日的排他上界。
     * @return 轉換後的 epoch milliseconds，空白值或錯誤時為 `null`。
     */
    private fun parseDate(
        value: String,
        zone: ZoneId,
        errors: MutableMap<HistoryBrowseFilterField, HistoryBrowseFilterError>,
        field: HistoryBrowseFilterField,
        exclusiveNextDay: Boolean = false,
    ): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return try {
            val date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
            val effectiveDate = if (exclusiveNextDay) date.plusDays(1) else date
            effectiveDate.atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            errors[field] = HistoryBrowseFilterError.INVALID_DATE
            null
        } catch (_: ArithmeticException) {
            errors[field] = HistoryBrowseFilterError.INVALID_DATE
            null
        }
    }

    /** 解析正整數名次。
     *
     * @param value 名次文字。
     * @param errors 驗證錯誤輸出表。
     * @param field 發生錯誤的欄位。
     * @return 正整數名次，空白值或錯誤時為 `null`。
     */
    private fun parseRank(
        value: String,
        errors: MutableMap<HistoryBrowseFilterField, HistoryBrowseFilterError>,
        field: HistoryBrowseFilterField,
    ): Int? {
        val text = value.trim()
        if (text.isEmpty()) return null
        val rank = text.toIntOrNull()
        if (rank == null || rank <= 0 || !text.all { it in '0'..'9' }) {
            errors[field] = HistoryBrowseFilterError.INVALID_RANK
            return null
        }
        return rank
    }
}
