package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

import com.doublemoon1119.mahjongcraft.flow.common.error.ApplicationError
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** 可查詢歷史的權限範圍。 */
enum class HistoryQueryScope {
    /** 只查詢目前使用者參與的對局。 */
    OWN,

    /** 查詢伺服器上所有已公開的對局。 */
    ALL,
}

/** 歷史清單的排序欄位。 */
enum class HistorySortField {
    /** 依結束時間排序。 */
    ENDED_AT,

    /** 依對局持續時間排序。 */
    DURATION,

    /** 依目前使用者的終局名次排序。 */
    OWN_RANK,

    /** 依目前使用者的終局分數排序。 */
    OWN_SCORE,
}

/** 歷史清單的排序方向。 */
enum class HistorySortDirection {
    /** 小值在前。 */
    ASC,

    /** 大值在前。 */
    DESC,
}

/** 對局的結束狀態篩選。 */
enum class HistoryOutcomeFilter {
    /** 只顯示正常完成的對局。 */
    COMPLETED,

    /** 只顯示有可靠終止證據的中斷對局。 */
    INTERRUPTED,
}

/** 對局完整性篩選。 */
enum class HistoryIntegrityFilter {
    /** 只顯示可完整重播的對局。 */
    COMPLETE,

    /** 只顯示缺少部分內容、但仍有終止摘要的對局。 */
    INCOMPLETE,
}

/** AI 參與狀態篩選。 */
enum class HistoryAiFilter {
    /** 只顯示包含 AI 的對局。 */
    CONTAINS_AI,

    /** 只顯示沒有 AI 的對局。 */
    NO_AI,
}

/** 可信任的歷史查詢發起者資訊。
 *
 * @property principalId 發起查詢的玩家 UUID。
 * @property isAdministrator 發起者目前是否擁有管理員權限。
 */
data class HistoryQueryAccess(
    val principalId: Uuid,
    val isAdministrator: Boolean,
)

/** 伺服器目前允許的歷史查詢政策。
 *
 * @property queryEnabled 是否允許任何歷史查詢。
 * @property allowAdministratorQuery 是否允許管理員使用所有對局查詢範圍。
 */
data class HistoryQueryPolicy(
    val queryEnabled: Boolean = true,
    val allowAdministratorQuery: Boolean = true,
)

/** 終局名次範圍；上下界均包含。
 *
 * @property minimum 最小名次。
 * @property maximum 最大名次。
 */
data class HistoryRankRange(
    val minimum: Int? = null,
    val maximum: Int? = null,
)

/** 歷史清單的條件篩選。
 *
 * @property ruleId 規則模組 ID。
 * @property outcome 結束狀態。
 * @property integrity 完整性狀態。
 * @property ai AI 參與狀態。
 * @property endedAtFromEpochMillis 結束時間下界。
 * @property endedAtBeforeEpochMillis 結束時間上界。
 * @property ownRank 目前使用者的終局名次範圍。
 * @property playerName 參與者名稱的大小寫不敏感部分比對文字。
 * @property matchId 指定對局 UUID 的文字篩選；null 表示不限制對局。
 */
data class HistoryQueryFilters(
    val ruleId: String? = null,
    val outcome: HistoryOutcomeFilter? = null,
    val integrity: HistoryIntegrityFilter? = null,
    val ai: HistoryAiFilter? = null,
    val endedAtFromEpochMillis: Long? = null,
    val endedAtBeforeEpochMillis: Long? = null,
    val ownRank: HistoryRankRange? = null,
    val playerName: String? = null,
    val matchId: String? = null,
) {
    init {
        require(playerName == null || playerName.length <= MAX_HISTORY_PLAYER_NAME_LENGTH) {
            "History player name filter exceeds its length limit"
        }
        require(matchId == null || matchId.length <= MAX_HISTORY_MATCH_ID_LENGTH) {
            "History match ID filter exceeds its length limit"
        }
    }
}

/** 用於延續 keyset 分頁的排序值。
 *
 * @property numericValue 非 null 排序值。
 * @property nullBucket null 排序值所在的排序桶。
 */
data class HistorySortValue(
    val numericValue: Long?,
    val nullBucket: Boolean,
) {
    init {
        require(nullBucket == (numericValue == null)) { "History cursor null bucket does not match sort value" }
    }
}

/** 不透明但可驗證的歷史清單游標內容。
 *
 * @property sortValue 上一頁最後一筆的排序值。
 * @property matchId 上一頁最後一筆的 UUID。
 * @property scope 產生游標時使用的查詢範圍。
 * @property sortField 產生游標時使用的排序欄位。
 * @property sortDirection 產生游標時使用的排序方向。
 * @property filters 產生游標時使用的篩選條件。
 */
data class HistoryQueryCursor(
    val sortValue: HistorySortValue,
    val matchId: Uuid,
    val scope: HistoryQueryScope,
    val sortField: HistorySortField,
    val sortDirection: HistorySortDirection,
    val filters: HistoryQueryFilters,
)

/** 歷史清單查詢要求。
 *
 * @property scope 查詢範圍。
 * @property sortField 排序欄位。
 * @property sortDirection 排序方向。
 * @property filters 條件篩選。
 * @property pageSize 每頁筆數。
 * @property cursor 延續上一頁的游標。
 */
data class HistoryListRequest(
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
    val sortField: HistorySortField = HistorySortField.ENDED_AT,
    val sortDirection: HistorySortDirection = HistorySortDirection.DESC,
    val filters: HistoryQueryFilters = HistoryQueryFilters(),
    val pageSize: Int = DEFAULT_HISTORY_PAGE_SIZE,
    val cursor: HistoryQueryCursor? = null,
) {
    init {
        require(pageSize in MIN_HISTORY_PAGE_SIZE..MAX_HISTORY_PAGE_SIZE) {
            "History page size must be between $MIN_HISTORY_PAGE_SIZE and $MAX_HISTORY_PAGE_SIZE"
        }
    }
}

/** 歷史摘要查詢要求。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 */
data class HistorySummaryRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
)

/** 歷史對局規則設定查詢要求。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 */
data class HistoryRuleSettingsRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
)

/** 歷史單局事件查詢要求。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 * @property roundNumber 欲讀取的局序號。
 * @property startTransactionIndex 事件頁的起始交易索引。
 * @property limit 要求的交易數量。
 */
data class HistoryRoundEventsRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
    val roundNumber: Int,
    val startTransactionIndex: Int = 0,
    val limit: Int = DEFAULT_HISTORY_REPLAY_PAGE_SIZE,
)

/** 歷史單局桌況查詢要求。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 * @property roundNumber 欲讀取的局序號。
 * @property position 欲重建的局內位置。
 */
data class HistoryRoundStateRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
    val roundNumber: Int,
    val position: HistoryRoundPosition = HistoryRoundPosition.Initial,
)

/** 歷史對局的參與者摘要。
 *
 * @property seatIndex 座位索引。
 * @property playerId 玩家 UUID。
 * @property aiStrategyId AI 策略 ID。
 */
data class HistoryParticipantSummary(
    val seatIndex: Int,
    val playerId: Uuid,
    val aiStrategyId: String?,
)

/** 歷史對局中可公開的終局結果。
 *
 * @property playerId 玩家 UUID。
 * @property finalScore 終局分數。
 * @property finalRank 依規則比較器計算的終局名次。
 */
data class HistoryResultSummary(
    val playerId: Uuid,
    val finalScore: Int?,
    val finalRank: Int?,
)

/** 歷史清單與摘要共用的對局中繼資料。
 *
 * @property matchId 對局 UUID。
 * @property ruleId 規則模組 ID。
 * @property startedAtEpochMillis 開始時間。
 * @property endedAtEpochMillis 結束時間。
 * @property duration 對局持續時間。
 * @property outcome 結束狀態。
 * @property integrity 完整性狀態。
 * @property integrityDiagnostic 完整性診斷摘要。
 * @property resultsAvailable 是否保存完整重播資料；不代表已驗證其內容可解碼。
 * @property participants 參與者摘要。
 * @property roundCount 已知的完成局數。
 * @property results 可取得的終局結果。
 */
data class HistoryMatchSummary(
    val matchId: Uuid,
    val ruleId: String?,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val duration: Duration?,
    val outcome: HistoryOutcomeFilter?,
    val integrity: HistoryIntegrityFilter,
    val integrityDiagnostic: String? = null,
    val resultsAvailable: Boolean,
    val participants: List<HistoryParticipantSummary>,
    val roundCount: Int?,
    val results: List<HistoryResultSummary>,
)

/** 單局的公開摘要。
 *
 * @property roundNumber 局序號。
 * @property startedAtEpochMillis 該局開始時間。
 * @property endedAtEpochMillis 該局結束時間。
 */
data class HistoryRoundSummary(
    val roundNumber: Int,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
)

/** 對局摘要及其局級索引資料。
 *
 * @property summary 對局共用摘要。
 * @property rounds 已知的局級摘要。
 */
data class HistoryMatchDetail(
    val summary: HistoryMatchSummary,
    val rounds: List<HistoryRoundSummary>,
)

/** 歷史對局開局時採用的完整遊戲設定。
 *
 * @property config 開局時採用的遊戲規則與流程設定。
 */
data class HistoryRuleSettings(
    val config: GameConfig,
)

/** 歷史清單查詢成功結果。
 *
 * @property entries 本頁對局摘要。
 * @property nextCursor 下一頁游標。
 */
data class HistoryListPage(
    val entries: List<HistoryMatchSummary>,
    val nextCursor: HistoryQueryCursor?,
)

/** 歷史查詢的穩定錯誤代碼。 */
enum class HistoryQueryErrorCode {
    /** 伺服器停用查詢。 */
    QUERY_DISABLED,

    /** 發起者沒有該查詢範圍的權限。 */
    ACCESS_DENIED,

    /** 要求參數無效。 */
    INVALID_REQUEST,

    /** 對局不存在、未公開或已清理。 */
    NOT_AVAILABLE,

    /** 已有查詢或資料庫管理工作正在執行。 */
    BUSY,

    /** 查詢頻率超過限制。 */
    RATE_LIMITED,

    /** 查詢超過執行時間上限。 */
    TIMEOUT,

    /** 目前伺服器 session 不可查詢。 */
    DISCONNECTED,

    /** 原始資料、解析工作量或回應超過資源限制。 */
    CONTENT_TOO_LARGE,
}

/** 可預期的歷史查詢失敗。
 *
 * @property code 對外穩定錯誤代碼。
 * @property diagnosticMessage 英文診斷訊息。
 */
data class HistoryQueryError(
    val code: HistoryQueryErrorCode,
    val diagnosticMessage: String,
) : ApplicationError

/** Flow 層提供給平台適配器的歷史資料查詢邊界。 */
interface HistoryQueryRepository {
    /** 依授權範圍、條件與 keyset 游標讀取一頁摘要。
     * @param access 可信任的發起者資訊。
     * @param request 清單查詢要求。
     * @return 查詢頁或穩定錯誤。
     */
    suspend fun list(
        access: HistoryQueryAccess,
        request: HistoryListRequest,
    ): HistoryQueryResult<HistoryListPage>

    /** 讀取單一已公開對局的摘要，不解碼完整重播內容。
     * @param access 可信任的發起者資訊。
     * @param request 摘要查詢要求。
     * @return 對局詳情或穩定錯誤。
     */
    suspend fun summary(
        access: HistoryQueryAccess,
        request: HistorySummaryRequest,
    ): HistoryQueryResult<HistoryMatchDetail>

    /** 讀取單一已公開對局的開局規則設定，不回傳目前房間設定。
     * @param access 可信任的發起者資訊。
     * @param request 規則設定查詢要求。
     * @return 對局規則設定或穩定錯誤。
     */
    suspend fun ruleSettings(
        access: HistoryQueryAccess,
        request: HistoryRuleSettingsRequest,
    ): HistoryQueryResult<HistoryRuleSettings>

    /** 讀取已公開單局的有界事件頁。
     * @param access 可信任的發起者資訊。
     * @param request 單局事件查詢要求。
     * @return 事件頁或穩定錯誤。
     */
    suspend fun roundEvents(
        access: HistoryQueryAccess,
        request: HistoryRoundEventsRequest,
    ): HistoryQueryResult<HistoryRoundEvents> = HistoryQueryResult.Failure(
        HistoryQueryError(HistoryQueryErrorCode.NOT_AVAILABLE, "Round event queries are not available"),
    )

    /** 讀取已公開單局指定位置的完整桌況。
     * @param access 可信任的發起者資訊。
     * @param request 單局桌況查詢要求。
     * @return 桌況或穩定錯誤。
     */
    suspend fun roundState(
        access: HistoryQueryAccess,
        request: HistoryRoundStateRequest,
    ): HistoryQueryResult<HistoryRoundState> = HistoryQueryResult.Failure(
        HistoryQueryError(HistoryQueryErrorCode.NOT_AVAILABLE, "Round state queries are not available"),
    )
}

/** 歷史查詢 repository 的成功或失敗結果。 */
sealed interface HistoryQueryResult<out T> {
    /**
     * 查詢成功。
     *
     * @property value 已授權且經查詢限制處理的結果。
     */
    data class Success<T>(val value: T) : HistoryQueryResult<T>

    /**
     * 查詢失敗。
     *
     * @property error 穩定錯誤代碼及內部英文診斷。
     */
    data class Failure(val error: HistoryQueryError) : HistoryQueryResult<Nothing>
}

/** 預設歷史清單頁大小。 */
const val DEFAULT_HISTORY_PAGE_SIZE: Int = 20

/** 歷史清單允許的最小頁大小。 */
const val MIN_HISTORY_PAGE_SIZE: Int = 1

/** 歷史清單允許的最大頁大小。 */
const val MAX_HISTORY_PAGE_SIZE: Int = 50

/** 歷史查詢規則 ID 的最大長度。 */
const val MAX_HISTORY_RULE_ID_LENGTH: Int = 255

/** 歷史查詢玩家名稱片段的最大長度。 */
const val MAX_HISTORY_PLAYER_NAME_LENGTH: Int = 16

/** 歷史查詢對局 ID 文字的最大長度。 */
const val MAX_HISTORY_MATCH_ID_LENGTH: Int = 64

/** 歷史事件頁允許的最小交易數量。 */
const val MIN_HISTORY_REPLAY_PAGE_SIZE: Int = 1

/** 歷史事件頁允許的最大交易數量。 */
const val MAX_HISTORY_REPLAY_PAGE_SIZE: Int = 20

/** 歷史事件頁的預設交易數量。 */
const val DEFAULT_HISTORY_REPLAY_PAGE_SIZE: Int = 20
