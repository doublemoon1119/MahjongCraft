package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchDetailDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto

/** 同一歷史瀏覽 session 的導航位置。 */
internal enum class HistoryBrowsePage {
    /** 對局列表。 */
    LIST,

    /** 列表的篩選條件。 */
    FILTERS,

    /** 已選對局的摘要。 */
    SUMMARY,

    /** 已選對局的規則設定。 */
    RULE_SETTINGS,

    /** 已選局的事件頁。 */
    ROUND_EVENTS,

    /** 已選局的桌況頁。 */
    ROUND_STATE,
}

/** 歷史瀏覽資料的載入結果，不將失敗當成空資料。 */
internal sealed interface HistoryBrowseStatus {
    /** 尚未查詢。 */
    data object Idle : HistoryBrowseStatus

    /** 正在等待排程或回應。 */
    data object Loading : HistoryBrowseStatus

    /** 已取得成功結果；資料集合可以為空。 */
    data object Ready : HistoryBrowseStatus

    /**
     * 無法取得此查詢結果。
     *
     * @property reason 不含內部例外或路徑的失敗種類。
     */
    data class Failed(val reason: HistoryBrowseFailure) : HistoryBrowseStatus
}

/** 可供呈現端翻譯的歷史查詢失敗種類。 */
internal enum class HistoryBrowseFailure {
    /** 伺服器停用查詢。 */
    QUERY_DISABLED,

    /** 沒有查閱權限。 */
    ACCESS_DENIED,

    /** 查詢參數無效。 */
    INVALID_REQUEST,

    /** 紀錄不存在、未公開或已清理，不能進一步區分。 */
    NOT_AVAILABLE,

    /** 伺服器仍有查詢工作。 */
    BUSY,

    /** 請求間隔過短。 */
    RATE_LIMITED,

    /** 伺服器執行查詢逾時。 */
    SERVER_TIMEOUT,

    /** 原連線或世界已失效。 */
    DISCONNECTED,

    /** 內容超過查詢上限。 */
    CONTENT_TOO_LARGE,

    /** 客戶端無法送出要求。 */
    SEND_FAILED,

    /** 客戶端等待回應超過上限。 */
    CLIENT_TIMEOUT,
}

/**
 * 將伺服器穩定錯誤碼轉為 client 呈現種類，不推測伺服器內部原因。
 *
 * @return 對應的失敗種類。
 */
internal fun HistoryQueryErrorCodeDto.toBrowseFailure(): HistoryBrowseFailure = when (this) {
    HistoryQueryErrorCodeDto.QUERY_DISABLED -> HistoryBrowseFailure.QUERY_DISABLED
    HistoryQueryErrorCodeDto.ACCESS_DENIED -> HistoryBrowseFailure.ACCESS_DENIED
    HistoryQueryErrorCodeDto.INVALID_REQUEST -> HistoryBrowseFailure.INVALID_REQUEST
    HistoryQueryErrorCodeDto.NOT_AVAILABLE -> HistoryBrowseFailure.NOT_AVAILABLE
    HistoryQueryErrorCodeDto.BUSY -> HistoryBrowseFailure.BUSY
    HistoryQueryErrorCodeDto.RATE_LIMITED -> HistoryBrowseFailure.RATE_LIMITED
    HistoryQueryErrorCodeDto.TIMEOUT -> HistoryBrowseFailure.SERVER_TIMEOUT
    HistoryQueryErrorCodeDto.DISCONNECTED -> HistoryBrowseFailure.DISCONNECTED
    HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE -> HistoryBrowseFailure.CONTENT_TOO_LARGE
}

/**
 * 最近成功列表與其導航位置；失敗時仍保留原頁資訊，不把新頁視為已完成。
 *
 * @property entries 最近成功取得的安全摘要。
 * @property pageNumber 已成功到達的瀏覽頁次，從 1 開始，不代表總頁數。
 * @property firstEntryIndex 此頁第一筆資料的 1-based 位置；空頁為 0。
 * @property nextCursor 最近成功回應提供的下一頁游標。
 * @property status 目前列表查詢結果。
 * @property scrollOffset 返回列表時保留的非負捲動位置。
 * @property selectedMatchId 最近選取的列表對局。
 */
internal data class HistoryBrowseListState(
    val entries: List<HistoryMatchSummaryDto> = emptyList(),
    val pageNumber: Int = 1,
    val firstEntryIndex: Int = 0,
    val nextCursor: String? = null,
    val status: HistoryBrowseStatus = HistoryBrowseStatus.Idle,
    val scrollOffset: Double = 0.0,
    val selectedMatchId: String? = null,
)

/**
 * 已選對局的摘要載入結果。
 *
 * @property matchId 正在查閱的對局識別碼。
 * @property detail 最近成功取得的同場摘要；失敗不偽造內容。
 * @property status 目前摘要查詢結果。
 * @property scrollOffset 返回摘要頁時保留的非負捲動位置。
 */
internal data class HistoryBrowseSummaryState(
    val matchId: String,
    val detail: HistoryMatchDetailDto? = null,
    val status: HistoryBrowseStatus = HistoryBrowseStatus.Idle,
    val scrollOffset: Double = 0.0,
)

/** 已選對局的開局規則設定載入結果。
 *
 * @property matchId 正在查閱的對局識別碼。
 * @property config 最近成功取得的完整開局設定。
 * @property status 目前規則設定查詢結果。
 */
internal data class HistoryBrowseRuleSettingsState(
    val matchId: String,
    val config: GameConfigDto? = null,
    val status: HistoryBrowseStatus = HistoryBrowseStatus.Idle,
)

/**
 * 歷史瀏覽的唯讀快照，不包含完整 Replay 或遊戲權威狀態。
 *
 * @property query 目前有效的列表查詢條件。
 * @property page 目前導航位置。
 * @property list 最近列表與位置。
 * @property summary 最近選取的單場摘要。
 * @property ruleSettings 最近選取對局的開局規則設定。
 * @property closed 最外層瀏覽已關閉或原 session 已失效，不再接受操作。
 * @property allowAll 最近一次已配對伺服器回應是否允許查閱全部對局。
 * @property rounds 各局獨立保存的導航位置；僅選取局保留呈現資料，其他局保留游標與捲動位置。
 * @property selectedRoundNumber 目前選取的局序號。
 */
internal data class HistoryBrowseState(
    val query: HistoryBrowseQuery = HistoryBrowseQuery(),
    val page: HistoryBrowsePage = HistoryBrowsePage.LIST,
    val list: HistoryBrowseListState = HistoryBrowseListState(),
    val summary: HistoryBrowseSummaryState? = null,
    val ruleSettings: HistoryBrowseRuleSettingsState? = null,
    val closed: Boolean = false,
    val allowAll: Boolean = false,
    val rounds: Map<Int, HistoryBrowseRoundState> = emptyMap(),
    val selectedRoundNumber: Int? = null,
)
