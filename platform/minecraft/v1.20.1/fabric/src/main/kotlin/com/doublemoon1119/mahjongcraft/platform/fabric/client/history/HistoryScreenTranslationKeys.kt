package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryAiFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys

/** 結果篩選選項的顯示文字翻譯鍵。 */
internal val HistoryOutcomeFilterDto.translationKey: String
    get() = when (this) {
        HistoryOutcomeFilterDto.COMPLETED -> MinecraftHistoryScreenKeys.FILTER_OUTCOME_COMPLETED
        HistoryOutcomeFilterDto.INTERRUPTED -> MinecraftHistoryScreenKeys.FILTER_OUTCOME_INTERRUPTED
    }

/** 完整性篩選選項的顯示文字翻譯鍵。 */
internal val HistoryIntegrityFilterDto.translationKey: String
    get() = when (this) {
        HistoryIntegrityFilterDto.COMPLETE -> MinecraftHistoryScreenKeys.FILTER_INTEGRITY_COMPLETE
        HistoryIntegrityFilterDto.INCOMPLETE -> MinecraftHistoryScreenKeys.FILTER_INTEGRITY_INCOMPLETE
    }

/** AI 篩選選項的顯示文字翻譯鍵。 */
internal val HistoryAiFilterDto.translationKey: String
    get() = when (this) {
        HistoryAiFilterDto.CONTAINS_AI -> MinecraftHistoryScreenKeys.FILTER_AI_CONTAINS_AI
        HistoryAiFilterDto.NO_AI -> MinecraftHistoryScreenKeys.FILTER_AI_NO_AI
    }

/** 紀錄範圍的顯示文字翻譯鍵。 */
internal val HistoryQueryScopeDto.translationKey: String
    get() = when (this) {
        HistoryQueryScopeDto.OWN -> MinecraftHistoryScreenKeys.SCOPE_OWN
        HistoryQueryScopeDto.ALL -> MinecraftHistoryScreenKeys.SCOPE_ALL
        HistoryQueryScopeDto.STRESS_TEST -> MinecraftHistoryScreenKeys.SCOPE_STRESS_TEST
    }

/** 排序欄位的顯示文字翻譯鍵。 */
internal val HistorySortFieldDto.translationKey: String
    get() = when (this) {
        HistorySortFieldDto.ENDED_AT -> MinecraftHistoryScreenKeys.SORT_ENDED_AT
        HistorySortFieldDto.DURATION -> MinecraftHistoryScreenKeys.SORT_DURATION
        HistorySortFieldDto.OWN_RANK -> MinecraftHistoryScreenKeys.SORT_OWN_RANK
        HistorySortFieldDto.OWN_SCORE -> MinecraftHistoryScreenKeys.SORT_OWN_SCORE
    }

/** 排序方向的顯示文字翻譯鍵。 */
internal val HistorySortDirectionDto.translationKey: String
    get() = when (this) {
        HistorySortDirectionDto.ASC -> MinecraftHistoryScreenKeys.DIRECTION_ASC
        HistorySortDirectionDto.DESC -> MinecraftHistoryScreenKeys.DIRECTION_DESC
    }

/** 篩選欄位錯誤的顯示文字翻譯鍵。 */
internal val HistoryBrowseFilterError.translationKey: String
    get() = when (this) {
        HistoryBrowseFilterError.INVALID_RULE -> MinecraftHistoryScreenKeys.FILTER_ERROR_INVALID_RULE
        HistoryBrowseFilterError.INVALID_DATE -> MinecraftHistoryScreenKeys.FILTER_ERROR_INVALID_DATE
        HistoryBrowseFilterError.INVALID_RANK -> MinecraftHistoryScreenKeys.FILTER_ERROR_INVALID_RANK
        HistoryBrowseFilterError.INVALID_MATCH_ID -> MinecraftHistoryScreenKeys.FILTER_ERROR_INVALID_MATCH_ID
        HistoryBrowseFilterError.INVALID_PLAYER_NAME -> MinecraftHistoryScreenKeys.FILTER_ERROR_INVALID_PLAYER_NAME
        HistoryBrowseFilterError.REVERSED_RANGE -> MinecraftHistoryScreenKeys.FILTER_ERROR_REVERSED_RANGE
    }

/** 查詢失敗原因的顯示文字翻譯鍵。 */
internal val HistoryBrowseFailure.translationKey: String
    get() = when (this) {
        HistoryBrowseFailure.QUERY_DISABLED -> MinecraftHistoryScreenKeys.FAILURE_QUERY_DISABLED
        HistoryBrowseFailure.ACCESS_DENIED -> MinecraftHistoryScreenKeys.FAILURE_ACCESS_DENIED
        HistoryBrowseFailure.INVALID_REQUEST -> MinecraftHistoryScreenKeys.FAILURE_INVALID_REQUEST
        HistoryBrowseFailure.NOT_AVAILABLE -> MinecraftHistoryScreenKeys.FAILURE_NOT_AVAILABLE
        HistoryBrowseFailure.BUSY -> MinecraftHistoryScreenKeys.FAILURE_BUSY
        HistoryBrowseFailure.RATE_LIMITED -> MinecraftHistoryScreenKeys.FAILURE_RATE_LIMITED
        HistoryBrowseFailure.SERVER_TIMEOUT -> MinecraftHistoryScreenKeys.FAILURE_SERVER_TIMEOUT
        HistoryBrowseFailure.DISCONNECTED -> MinecraftHistoryScreenKeys.FAILURE_DISCONNECTED
        HistoryBrowseFailure.CONTENT_TOO_LARGE -> MinecraftHistoryScreenKeys.FAILURE_CONTENT_TOO_LARGE
        HistoryBrowseFailure.SEND_FAILED -> MinecraftHistoryScreenKeys.FAILURE_SEND_FAILED
        HistoryBrowseFailure.CLIENT_TIMEOUT -> MinecraftHistoryScreenKeys.FAILURE_CLIENT_TIMEOUT
    }

/** 保存狀態提示的翻譯鍵；已保存不需要提示，為 null。 */
internal val HistoryArchiveStatusDto.translationKey: String?
    get() = when (this) {
        HistoryArchiveStatusDto.SAVED -> null
        HistoryArchiveStatusDto.PENDING -> MinecraftHistoryScreenKeys.ARCHIVE_STATUS_PENDING
        HistoryArchiveStatusDto.EXCLUDED -> MinecraftHistoryScreenKeys.ARCHIVE_STATUS_EXCLUDED
        HistoryArchiveStatusDto.DISABLED -> MinecraftHistoryScreenKeys.ARCHIVE_STATUS_DISABLED
        HistoryArchiveStatusDto.FAILED -> MinecraftHistoryScreenKeys.ARCHIVE_STATUS_FAILED
        HistoryArchiveStatusDto.MISSING -> MinecraftHistoryScreenKeys.ARCHIVE_STATUS_MISSING
        HistoryArchiveStatusDto.DENIED -> MinecraftHistoryScreenKeys.ARCHIVE_STATUS_DENIED
    }

/** 文字篩選欄位的用途說明翻譯鍵；規則欄位以選單選擇，沒有文字輸入說明，為 null。 */
internal val HistoryBrowseFilterField.inputDescriptionKey: String?
    get() = when (this) {
        HistoryBrowseFilterField.RULE -> null
        HistoryBrowseFilterField.PLAYER_NAME -> MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_PLAYER_NAME
        HistoryBrowseFilterField.MATCH_ID -> MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_MATCH_ID
        HistoryBrowseFilterField.FROM_DATE -> MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_FROM_DATE
        HistoryBrowseFilterField.THROUGH_DATE -> MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_THROUGH_DATE
        HistoryBrowseFilterField.MIN_RANK -> MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_MIN_RANK
        HistoryBrowseFilterField.MAX_RANK -> MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_MAX_RANK
    }

/** 文字篩選欄位的輸入範例翻譯鍵；規則欄位以選單選擇，沒有輸入範例，為 null。 */
internal val HistoryBrowseFilterField.inputExampleKey: String?
    get() = when (this) {
        HistoryBrowseFilterField.RULE -> null
        HistoryBrowseFilterField.PLAYER_NAME -> MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_PLAYER_NAME
        HistoryBrowseFilterField.MATCH_ID -> MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_MATCH_ID
        HistoryBrowseFilterField.FROM_DATE -> MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_FROM_DATE
        HistoryBrowseFilterField.THROUGH_DATE -> MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_THROUGH_DATE
        HistoryBrowseFilterField.MIN_RANK -> MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_MIN_RANK
        HistoryBrowseFilterField.MAX_RANK -> MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_MAX_RANK
    }
