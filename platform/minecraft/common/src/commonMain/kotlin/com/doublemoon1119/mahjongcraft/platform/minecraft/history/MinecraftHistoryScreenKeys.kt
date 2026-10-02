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

    /** 卡片開啟對局摘要提示。 */
    const val CARD_OPEN_HINT = "mahjongcraft.history_screen.card_open_hint"

    /** 對局摘要標題。 */
    const val SUMMARY_TITLE = "mahjongcraft.history_screen.summary.title"

    /** 對局摘要基本資料區段。 */
    const val SUMMARY_BASIC = "mahjongcraft.history_screen.summary.basic"

    /** 對局摘要排名區段。 */
    const val SUMMARY_RANKING = "mahjongcraft.history_screen.summary.ranking"

    /** 對局摘要局索引標籤。 */
    const val SUMMARY_ROUND_INDEX = "mahjongcraft.history_screen.summary.round_index"

    /** 對局摘要對局識別碼標籤。 */
    const val SUMMARY_MATCH_ID = "mahjongcraft.history_screen.summary.match_id"

    /** 摘要所用規則的標籤。 */
    const val SUMMARY_RULE = "mahjongcraft.history_screen.summary.rule"

    /** 點擊複製對局 ID 的提示。 */
    const val SUMMARY_COPY_MATCH_ID = "mahjongcraft.history_screen.summary.copy_match_id"

    /** 複製成功的短暫提示。 */
    const val SUMMARY_MATCH_ID_COPIED = "mahjongcraft.history_screen.summary.match_id_copied"

    /** 對局摘要開始時間標籤。 */
    const val SUMMARY_STARTED_AT = "mahjongcraft.history_screen.summary.started_at"

    /** 對局摘要結束時間標籤。 */
    const val SUMMARY_ENDED_AT = "mahjongcraft.history_screen.summary.ended_at"

    /** 對局摘要時長標籤。 */
    const val SUMMARY_DURATION = "mahjongcraft.history_screen.summary.duration"

    /** 對局摘要結果標籤。 */
    const val SUMMARY_OUTCOME = "mahjongcraft.history_screen.summary.outcome"

    /** 對局摘要完整性標籤。 */
    const val SUMMARY_INTEGRITY = "mahjongcraft.history_screen.summary.integrity"

    /** 對局摘要局數標籤。 */
    const val SUMMARY_ROUND_COUNT = "mahjongcraft.history_screen.summary.round_count"

    /** 對局摘要結果不可用提示。 */
    const val SUMMARY_RESULTS_UNAVAILABLE = "mahjongcraft.history_screen.summary.results_unavailable"

    /** 對局摘要沒有局資料提示。 */
    const val SUMMARY_ROUNDS_EMPTY = "mahjongcraft.history_screen.summary.rounds_empty"

    /** 對局摘要單列格式。 */
    const val SUMMARY_ROUND_ROW = "mahjongcraft.history_screen.summary.round_row"

    /** 對局摘要排名欄標題。 */
    const val SUMMARY_RANK_HEADER = "mahjongcraft.history_screen.summary.rank_header"

    /** 對局摘要玩家欄標題。 */
    const val SUMMARY_PLAYER_HEADER = "mahjongcraft.history_screen.summary.player_header"

    /** 對局摘要分數欄標題。 */
    const val SUMMARY_SCORE_HEADER = "mahjongcraft.history_screen.summary.score_header"

    /** 對局摘要完整性提示。 */
    const val SUMMARY_INTEGRITY_TOOLTIP = "mahjongcraft.history_screen.summary.integrity_tooltip"

    /** 規則設定標題。 */
    const val RULE_SETTINGS_TITLE = "mahjongcraft.history_screen.rule_settings.title"

    /** 規則設定入口提示。 */
    const val RULE_SETTINGS_OPEN_HINT = "mahjongcraft.history_screen.rule_settings.open_hint"

    /** 規則設定唯讀提示。 */
    const val RULE_SETTINGS_READ_ONLY = "mahjongcraft.history_screen.rule_settings.read_only"

    /** 規則設定資料不可用提示。 */
    const val RULE_SETTINGS_UNAVAILABLE = "mahjongcraft.history_screen.rule_settings.unavailable"

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
