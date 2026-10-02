package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/** 歷史列表、篩選與保存狀態提示共用的集中式翻譯鍵。 */
object MinecraftHistoryScreenKeys {
    /** 歷史列表標題。 */
    const val TITLE = "mahjongcraft.history_screen.title"

    /** 篩選子頁標題。 */
    const val FILTERS = "mahjongcraft.history_screen.filters"

    /** 篩選頁返回按鈕。 */
    const val BACK = "mahjongcraft.history_screen.back"

    /** 篩選頁重設按鈕。 */
    const val RESET = "mahjongcraft.history_screen.reset"

    /** 篩選規則欄位。 */
    const val FILTER_RULE = "mahjongcraft.history_screen.filter.rule"

    /** 篩選開始日期欄位。 */
    const val FILTER_FROM = "mahjongcraft.history_screen.filter.from"

    /** 篩選結束日期欄位。 */
    const val FILTER_THROUGH = "mahjongcraft.history_screen.filter.through"

    /** 篩選最低名次欄位。 */
    const val FILTER_MIN_RANK = "mahjongcraft.history_screen.filter.min_rank"

    /** 篩選最高名次欄位。 */
    const val FILTER_MAX_RANK = "mahjongcraft.history_screen.filter.max_rank"

    /** 結果篩選欄位。 */
    const val FILTER_OUTCOME = "mahjongcraft.history_screen.filter.outcome"

    /** 完整性篩選欄位。 */
    const val FILTER_INTEGRITY = "mahjongcraft.history_screen.filter.integrity"

    /** AI 篩選欄位。 */
    const val FILTER_AI = "mahjongcraft.history_screen.filter.ai"

    /** 篩選規則選擇按鈕。 */
    const val FILTER_RULE_SELECT = "mahjongcraft.history_screen.filter.rule_select"

    /** 篩選不限值。 */
    const val FILTER_ANY = "mahjongcraft.history_screen.filter.any"

    /** 已完成結果。 */
    const val OUTCOME_COMPLETED = "mahjongcraft.history_screen.outcome.completed"

    /** 已中斷結果。 */
    const val OUTCOME_INTERRUPTED = "mahjongcraft.history_screen.outcome.interrupted"

    /** 篩選日期格式提示。 */
    const val FILTER_DATE_HINT = "mahjongcraft.history_screen.filter.date_hint"

    /** 篩選錯誤鍵前綴。 */
    const val FILTER_ERROR_PREFIX = "mahjongcraft.history_screen.filter.error."

    /** 選項 tooltip 說明格式。 */
    const val TOOLTIP_DESCRIPTION = "mahjongcraft.history_screen.tooltip.description"

    /** 選項 tooltip 目前值格式。 */
    const val TOOLTIP_CURRENT = "mahjongcraft.history_screen.tooltip.current"

    /** 選項 tooltip 可用選項標題。 */
    const val TOOLTIP_OPTIONS = "mahjongcraft.history_screen.tooltip.options"

    /** 篩選按鈕 tooltip。 */
    const val TOOLTIP_FILTERS = "mahjongcraft.history_screen.tooltip.filters"

    /** 未套用任何篩選條件。 */
    const val FILTER_NONE = "mahjongcraft.history_screen.filter.none"

    /** 玩家名稱篩選欄位。 */
    const val FILTER_PLAYER_NAME = "mahjongcraft.history_screen.filter.player_name"

    /** 對局 UUID 篩選欄位。 */
    const val FILTER_MATCH_ID = "mahjongcraft.history_screen.filter.match_id"

    /** 篩選欄位用途說明鍵前綴。 */
    const val FILTER_INPUT_DESCRIPTION_PREFIX = "mahjongcraft.history_screen.filter.input_description."

    /** 篩選欄位輸入範例鍵前綴。 */
    const val FILTER_INPUT_EXAMPLE_PREFIX = "mahjongcraft.history_screen.filter.input_example."

    /** 篩選欄位輸入範例標籤。 */
    const val FILTER_INPUT_EXAMPLE_LABEL = "mahjongcraft.history_screen.filter.input.example_label"

    /** 歷史紀錄完整狀態。 */
    const val INTEGRITY_COMPLETE = "mahjongcraft.history_screen.filter.integrity.complete"

    /** 歷史紀錄不完整狀態。 */
    const val INTEGRITY_INCOMPLETE = "mahjongcraft.history_screen.filter.integrity.incomplete"

    /** 未知值文字。 */
    const val UNKNOWN = "mahjongcraft.history_screen.unknown"

    /** 等待列表結果。 */
    const val LOADING = "mahjongcraft.history_screen.loading"

    /** 空列表提示。 */
    const val EMPTY = "mahjongcraft.history_screen.empty"

    /** 查詢失敗訊息前綴。 */
    const val FAILURE_PREFIX = "mahjongcraft.history_screen.failure."

    /** 保存狀態提示前綴。 */
    const val ARCHIVE_STATUS_PREFIX = "mahjongcraft.history_screen.archive_status."

    /** 重試按鈕。 */
    const val RETRY = "mahjongcraft.history_screen.retry"

    /** 關閉按鈕。 */
    const val CLOSE = "mahjongcraft.history_screen.close"

    /** 重新整理按鈕。 */
    const val REFRESH = "mahjongcraft.history_screen.refresh"

    /** 重新整理 tooltip。 */
    const val REFRESH_TOOLTIP = "mahjongcraft.history_screen.tooltip.refresh"

    /** 查詢尚未完成時的操作說明。 */
    const val QUERY_LOADING_TOOLTIP = "mahjongcraft.history_screen.tooltip.query_loading"

    /** 最短查詢間隔尚未經過時的操作說明。 */
    const val QUERY_COOLDOWN_TOOLTIP = "mahjongcraft.history_screen.tooltip.query_cooldown"

    /** 上一頁按鈕。 */
    const val PREVIOUS = "mahjongcraft.history_screen.previous"

    /** 下一頁按鈕。 */
    const val NEXT = "mahjongcraft.history_screen.next"

    /** 頁碼格式。 */
    const val PAGE = "mahjongcraft.history_screen.page"

    /** 當前頁實際包含的紀錄索引範圍。 */
    const val PAGE_RANGE = "mahjongcraft.history_screen.page_range"

    /** 卡片 tooltip。 */
    const val CARD_TOOLTIP = "mahjongcraft.history_screen.card_tooltip"

    /** 對局結果格式。 */
    const val RESULT = "mahjongcraft.history_screen.result"

    /** 對局局數格式。 */
    const val ROUNDS = "mahjongcraft.history_screen.rounds"

    /** 對局未知玩家文字。 */
    const val PLAYER_UNKNOWN = "mahjongcraft.history_screen.player_unknown"

    /** 結算聊天訊息開啟歷史提示。 */
    const val OPEN_HINT = "mahjongcraft.history_screen.open_hint"

    /** 保存狀態查詢不可用。 */
    const val ARCHIVE_UNAVAILABLE = "mahjongcraft.history_screen.archive_status.unavailable"

    /** 紀錄範圍鍵前綴。 */
    const val SCOPE_PREFIX = "mahjongcraft.history_screen.scope."

    /** 紀錄範圍 tooltip 標題。 */
    const val SCOPE_TITLE = "mahjongcraft.history_screen.scope"

    /** 排序欄位鍵前綴。 */
    const val SORT_PREFIX = "mahjongcraft.history_screen.sort."

    /** 排序欄位 tooltip 標題。 */
    const val SORT_TITLE = "mahjongcraft.history_screen.sort"

    /** 排序方向鍵前綴。 */
    const val DIRECTION_PREFIX = "mahjongcraft.history_screen.direction."

    /** 排序方向 tooltip 標題。 */
    const val DIRECTION_TITLE = "mahjongcraft.history_screen.direction"
}
