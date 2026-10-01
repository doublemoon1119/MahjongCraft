package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史查詢可用的資料範圍。 */
@Serializable
enum class HistoryQueryScopeDto {
    /** 只查詢連線玩家參與的對局。 */
    @SerialName("own")
    OWN,

    /** 查詢伺服器允許公開的全部對局。 */
    @SerialName("all")
    ALL,
}

/** 歷史清單的排序欄位。 */
@Serializable
enum class HistorySortFieldDto {
    /** 依對局結束時間排序。 */
    @SerialName("ended_at")
    ENDED_AT,

    /** 依對局持續時間排序。 */
    @SerialName("duration")
    DURATION,

    /** 依查詢玩家的最終名次排序。 */
    @SerialName("own_rank")
    OWN_RANK,

    /** 依查詢玩家的最終分數排序。 */
    @SerialName("own_score")
    OWN_SCORE,
}

/** 歷史清單的排序方向。 */
@Serializable
enum class HistorySortDirectionDto {
    /** 由小到大排序。 */
    @SerialName("asc")
    ASC,

    /** 由大到小排序。 */
    @SerialName("desc")
    DESC,
}

/** 歷史對局的結束狀態篩選。 */
@Serializable
enum class HistoryOutcomeFilterDto {
    /** 只顯示正常完成的對局。 */
    @SerialName("completed")
    COMPLETED,

    /** 只顯示已確認中止的對局。 */
    @SerialName("interrupted")
    INTERRUPTED,
}

/** 歷史資料完整性篩選。 */
@Serializable
enum class HistoryIntegrityFilterDto {
    /** 只顯示可完整重播的對局。 */
    @SerialName("complete")
    COMPLETE,

    /** 只顯示只能提供部分結果的對局。 */
    @SerialName("incomplete")
    INCOMPLETE,
}

/** AI 座位存在與否的篩選。 */
@Serializable
enum class HistoryAiFilterDto {
    /** 只顯示包含 AI 座位的對局。 */
    @SerialName("contains")
    CONTAINS_AI,

    /** 只顯示沒有 AI 座位的對局。 */
    @SerialName("none")
    NO_AI,
}

/** 歷史查詢對外穩定錯誤代碼。 */
@Serializable
enum class HistoryQueryErrorCodeDto {
    /** 伺服器停用查詢。 */
    @SerialName("query_disabled")
    QUERY_DISABLED,

    /** 查詢範圍沒有權限。 */
    @SerialName("access_denied")
    ACCESS_DENIED,

    /** 請求參數無效。 */
    @SerialName("invalid_request")
    INVALID_REQUEST,

    /** 對局不存在、未公開或已清理。 */
    @SerialName("not_available")
    NOT_AVAILABLE,

    /** 查詢資源忙碌。 */
    @SerialName("busy")
    BUSY,

    /** 查詢頻率超過限制。 */
    @SerialName("rate_limited")
    RATE_LIMITED,

    /** 查詢逾時。 */
    @SerialName("timeout")
    TIMEOUT,

    /** 目前 session 不可查詢。 */
    @SerialName("disconnected")
    DISCONNECTED,

    /** 回應超過傳輸大小限制。 */
    @SerialName("content_too_large")
    CONTENT_TOO_LARGE,
}

@Serializable
/** 歷史清單的複合篩選條件。
 *
 * @property ruleId 規則模組的 namespaced ID；null 表示不限制規則。
 * @property outcome 對局結果篩選；null 表示不限制結果。
 * @property integrity 對局完整性篩選；null 表示不限制完整性。
 * @property ai AI 座位篩選；null 表示不限制 AI。
 * @property endedAtFromEpochMillis 結束時間的 UTC 下界，包含此時間。
 * @property endedAtBeforeEpochMillis 結束時間的 UTC 上界，不包含此時間。
 * @property ownRankMin 查詢玩家可接受的最小名次。
 * @property ownRankMax 查詢玩家可接受的最大名次。
 */
data class HistoryQueryFiltersDto(val ruleId: String? = null, val outcome: HistoryOutcomeFilterDto? = null, val integrity: HistoryIntegrityFilterDto? = null, val ai: HistoryAiFilterDto? = null, val endedAtFromEpochMillis: Long? = null, val endedAtBeforeEpochMillis: Long? = null, val ownRankMin: Int? = null, val ownRankMax: Int? = null)

/** 可驗證的歷史 keyset cursor 傳輸內容。
 *
 * @property numericSortValue 上一頁最後一筆的數值排序值。
 * @property nullBucket 上一頁最後一筆是否位於 null 排序桶。
 * @property matchId 上一頁最後一筆的對局 UUID 字串。
 * @property scope 產生 cursor 時使用的查詢範圍。
 * @property sortField 產生 cursor 時使用的排序欄位。
 * @property sortDirection 產生 cursor 時使用的排序方向。
 * @property filters 產生 cursor 時使用的完整篩選條件。
 */
@Serializable
data class HistoryQueryCursorDto(
    val numericSortValue: Long?,
    val nullBucket: Boolean,
    val matchId: String,
    val scope: HistoryQueryScopeDto,
    val sortField: HistorySortFieldDto,
    val sortDirection: HistorySortDirectionDto,
    val filters: HistoryQueryFiltersDto,
)

/** 歷史清單查詢的 C2S 請求。
 *
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property scope 要查詢的資料範圍。
 * @property sortField 清單排序欄位。
 * @property sortDirection 清單排序方向。
 * @property filters 清單篩選條件。
 * @property pageSize 每頁要求的資料筆數。
 * @property cursor 前一頁回覆提供的不透明 keyset cursor。
 */
@Serializable
data class HistoryListRequestDto(
    val requestId: String,
    val scope: HistoryQueryScopeDto = HistoryQueryScopeDto.OWN,
    val sortField: HistorySortFieldDto = HistorySortFieldDto.ENDED_AT,
    val sortDirection: HistorySortDirectionDto = HistorySortDirectionDto.DESC,
    val filters: HistoryQueryFiltersDto = HistoryQueryFiltersDto(),
    val pageSize: Int = 20,
    val cursor: String? = null,
)

/** 歷史清單中可安全公開的參與者資訊。
 *
 * @property seatIndex 牌桌座位索引。
 * @property playerId 玩家 UUID 字串；AI 座位也使用穩定識別碼。
 * @property aiStrategyId AI 策略識別碼；真人座位為 null。
 */
@Serializable
data class HistoryParticipantSummaryDto(
    val seatIndex: Int,
    val playerId: String,
    val aiStrategyId: String? = null,
)

/** 歷史清單中的單筆對局摘要。
 *
 * @property matchId 對局識別碼。
 * @property ruleId 使用的規則模組識別碼。
 * @property startedAtEpochMillis 對局開始 UTC epoch milliseconds。
 * @property endedAtEpochMillis 對局結束 UTC epoch milliseconds。
 * @property outcome 對局結果；未知時為 null。
 * @property integrity 歷史資料的完整性狀態。
 * @property integrityDiagnostic 完整性不足時的穩定診斷摘要。
 * @property durationMillis 對局持續時間（毫秒）。
 * @property participants 參與者清單。
 * @property roundCount 可用的回合數。
 * @property resultsAvailable 是否保存完整 Replay；單局查閱仍須驗證內容與 codec，不限制安全摘要查詢。
 * @property results 可取得的終局結果。
 */
@Serializable
data class HistoryMatchSummaryDto(
    val matchId: String,
    val ruleId: String?,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val outcome: HistoryOutcomeFilterDto?,
    val integrity: HistoryIntegrityFilterDto,
    val integrityDiagnostic: String? = null,
    val durationMillis: Long? = null,
    val participants: List<HistoryParticipantSummaryDto>,
    val roundCount: Int?,
    val resultsAvailable: Boolean,
    val results: List<HistoryResultSummaryDto> = emptyList(),
)

/** 歷史對局中可公開的玩家終局結果。
 *
 * @property playerId 玩家 UUID 字串。
 * @property finalScore 終局分數；資料不足時為 null。
 * @property finalRank 依規則模組比較器計算的終局名次；無法計算時為 null。
 */
@Serializable
data class HistoryResultSummaryDto(
    val playerId: String,
    val finalScore: Int?,
    val finalRank: Int?,
)

/** 歷史對局中可公開的單局索引。
 *
 * @property roundNumber 局序號。
 * @property startedAtEpochMillis 該局開始 UTC epoch milliseconds。
 * @property endedAtEpochMillis 該局結束 UTC epoch milliseconds。
 */
@Serializable
data class HistoryRoundSummaryDto(
    val roundNumber: Int,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
)

/** 單場歷史摘要及其局級索引。
 *
 * @property summary 對局摘要。
 * @property rounds 已知的局級摘要。
 */
@Serializable
data class HistoryMatchDetailDto(
    val summary: HistoryMatchSummaryDto,
    val rounds: List<HistoryRoundSummaryDto>,
)

/** 歷史清單的 S2C 回覆。
 *
 * @property requestId 對應請求的識別碼。
 * @property entries 可安全顯示的對局摘要。
 * @property nextCursor 下一頁 keyset cursor；null 表示沒有下一頁。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryListResponseDto(
    val requestId: String,
    val entries: List<HistoryMatchSummaryDto>,
    val nextCursor: String? = null,
    val errorCode: HistoryQueryErrorCodeDto? = null,
)

/** 單場歷史摘要查詢的 C2S 請求。
 *
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property matchId 要查詢的對局識別碼。
 * @property scope 要查詢的資料範圍。
 */
@Serializable
data class HistorySummaryRequestDto(
    val requestId: String,
    val matchId: String,
    val scope: HistoryQueryScopeDto = HistoryQueryScopeDto.OWN,
)

/** 單場歷史摘要查詢的 S2C 回覆。
 *
 * @property requestId 對應請求的識別碼。
 * @property detail 對局摘要及局級索引；不可提供時為 null。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistorySummaryResponseDto(
    val requestId: String,
    val detail: HistoryMatchDetailDto? = null,
    val errorCode: HistoryQueryErrorCodeDto? = null,
)
