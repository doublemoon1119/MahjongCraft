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

    /** 規則設定頁開啟規則一覽的按鈕。 */
    const val RULE_CATALOGUE = "mahjongcraft.history_screen.rule_settings.rule_catalogue"

    /** 規則設定頁開啟規則一覽按鈕的提示。 */
    const val RULE_CATALOGUE_TOOLTIP = "mahjongcraft.history_screen.rule_settings.rule_catalogue_tooltip"

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

    /** 摘要局列可點擊開啟單局紀錄的提示。 */
    const val ROUND_OPEN_HINT = "mahjongcraft.history_screen.round_open_hint"

    /** 單局事件畫面標題。 */
    const val ROUND_TITLE = "mahjongcraft.history_screen.round.title"

    /** 單局牌面狀態畫面標題。 */
    const val STATE_TITLE = "mahjongcraft.history_screen.state.title"

    /** 初始牌面狀態標籤。 */
    const val STATE_INITIAL = "mahjongcraft.history_screen.state.initial"

    /** 交易完成後牌面狀態標籤。 */
    const val STATE_AFTER_TRANSACTION = "mahjongcraft.history_screen.state.after_transaction"

    /** 開啟交易完成後完整牌面。 */
    const val STATE_OPEN = "mahjongcraft.history_screen.state.open"

    /** 開啟交易完成後完整牌面提示。 */
    const val STATE_OPEN_TOOLTIP = "mahjongcraft.history_screen.state.open_tooltip"

    /** 開啟初始牌面。 */
    const val STATE_INITIAL_OPEN = "mahjongcraft.history_screen.state.initial_open"

    /** 手牌區標籤。 */
    const val STATE_HAND = "mahjongcraft.history_screen.state.hand"

    /** 保存順序的可點擊狀態文字。 */
    const val STATE_SORT_ORIGINAL = "mahjongcraft.history_screen.state.sort_original"

    /** 理牌順序的可點擊狀態文字。 */
    const val STATE_SORT_SORTED = "mahjongcraft.history_screen.state.sort_sorted"

    /** 點擊後切換目標的操作提示。 */
    const val STATE_SORT_TOOLTIP = "mahjongcraft.history_screen.state.sort_tooltip"

    /** 無法解析規則牌序時的保留順序提示。 */
    const val STATE_SORT_UNAVAILABLE = "mahjongcraft.history_screen.state.sort_unavailable"

    /** 保存順序的狀態名稱。 */
    const val STATE_SORT_MODE_ORIGINAL = "mahjongcraft.history_screen.state.sort_mode_original"

    /** 理牌順序的狀態名稱。 */
    const val STATE_SORT_MODE_SORTED = "mahjongcraft.history_screen.state.sort_mode_sorted"

    /** 牌河標題與累計捨牌張數。 */
    const val STATE_DISCARDS_COUNT = "mahjongcraft.history_screen.state.discards_count"

    /** 牌河張數包含被鳴走捨牌的說明。 */
    const val STATE_DISCARDS_COUNT_TOOLTIP = "mahjongcraft.history_screen.state.discards_count_tooltip"

    /** 副露區標籤。 */
    const val STATE_MELDS = "mahjongcraft.history_screen.state.melds"

    /** 牌河區標籤。 */
    const val STATE_DISCARDS = "mahjongcraft.history_screen.state.discards"

    /** 活牌區標籤。 */
    const val STATE_WALL = "mahjongcraft.history_screen.state.wall"

    /** 保留牌區標籤。 */
    const val STATE_RESERVED = "mahjongcraft.history_screen.state.reserved"

    /** 和牌手牌標籤。 */
    const val STATE_WINNING_HAND = "mahjongcraft.history_screen.state.winning_hand"

    /** 在事件明細展開完整和牌手牌的入口。 */
    const val STATE_LOAD_WINNING_HAND = "mahjongcraft.history_screen.state.load_winning_hand"

    /** 展開和牌手牌所需查詢與呈現位置的提示。 */
    const val STATE_LOAD_WINNING_HAND_TOOLTIP = "mahjongcraft.history_screen.state.load_winning_hand_tooltip"

    /** 未記錄完整和牌牌組提示。 */
    const val STATE_WINNING_HAND_UNAVAILABLE = "mahjongcraft.history_screen.state.winning_hand_unavailable"

    /** 局內牌索引標籤。 */
    const val STATE_TILE_INDEX = "mahjongcraft.history_screen.state.tile_index"

    /** 已被取走標籤。 */
    const val STATE_TAKEN = "mahjongcraft.history_screen.state.taken"

    /** 牌牆展開區塊與兩區剩餘數量。 */
    const val STATE_WALL_SECTION = "mahjongcraft.history_screen.state.wall_section"

    /** 展開牌牆區塊的操作提示。 */
    const val STATE_WALL_EXPAND_TOOLTIP = "mahjongcraft.history_screen.state.wall_expand_tooltip"

    /** 收合牌牆區塊的操作提示。 */
    const val STATE_WALL_COLLAPSE_TOOLTIP = "mahjongcraft.history_screen.state.wall_collapse_tooltip"

    /** 空內容標籤。 */
    const val STATE_EMPTY = "mahjongcraft.history_screen.state.empty"

    /** 目前回合標籤。 */
    const val STATE_CURRENT = "mahjongcraft.history_screen.state.current"

    /** 莊家標籤。 */
    const val STATE_DEALER = "mahjongcraft.history_screen.state.dealer"

    /** 單局序號。 */
    const val ROUND_NUMBER = "mahjongcraft.history_screen.round.number"

    /** 開局交易標記。 */
    const val ROUND_OPENING = "mahjongcraft.history_screen.round.opening"

    /** 單局交易標題，參數為顯示索引與時間。 */
    const val ROUND_TRANSACTION = "mahjongcraft.history_screen.round.transaction"

    /** 單局事件中的直接牌列標籤。 */
    const val ROUND_DIRECT_TILES = "mahjongcraft.history_screen.round.direct_tiles"

    /** 單局事件中的新公開牌列標籤。 */
    const val ROUND_REVEALED_TILES = "mahjongcraft.history_screen.round.revealed_tiles"

    /** 單局事件中的玩家座位標籤。 */
    const val ROUND_SEAT = "mahjongcraft.history_screen.round.seat"

    /** 單局事件中的結算分數列。 */
    const val ROUND_SCORE = "mahjongcraft.history_screen.round.score"

    /** 單局結算原因。 */
    const val ROUND_OUTCOME_REASON = "mahjongcraft.history_screen.round.outcome_reason"

    /** 單局結算分類。 */
    const val ROUND_OUTCOME_CLASSIFICATION = "mahjongcraft.history_screen.round.outcome_classification"

    /** 單局結算受益玩家。 */
    const val ROUND_BENEFICIARY = "mahjongcraft.history_screen.round.beneficiary"

    /** 單局結算責任玩家。 */
    const val ROUND_RESPONSIBLE = "mahjongcraft.history_screen.round.responsible"

    /** 單局回應事件格式。 */
    const val ROUND_REACTION = "mahjongcraft.history_screen.round.reaction"

    /** 和牌後續流程已處理事件。 */
    const val ROUND_EVENT_WIN_CONTINUATION = "mahjongcraft.history_screen.round.event.win_continuation"

    /** 返回房間事件。 */
    const val ROUND_EVENT_RETURNED_TO_ROOM = "mahjongcraft.history_screen.round.event.returned_to_room"

    /** 對局開始事件。 */
    const val ROUND_EVENT_MATCH_STARTED = "mahjongcraft.history_screen.round.event.match_started"

    /** 本局開始事件。 */
    const val ROUND_EVENT_ROUND_STARTED = "mahjongcraft.history_screen.round.event.round_started"

    /** 遊戲開始操作。 */
    const val ROUND_ACTION_GAME_STARTED = "mahjongcraft.history_screen.round.action.game_started"

    /** 本局開始操作。 */
    const val ROUND_ACTION_ROUND_STARTED = "mahjongcraft.history_screen.round.action.round_started"

    /** 對局結束操作。 */
    const val ROUND_ACTION_MATCH_ENDED = "mahjongcraft.history_screen.round.action.match_ended"

    /** 擲骰操作。 */
    const val ROUND_ACTION_DICE_ROLLED = "mahjongcraft.history_screen.round.action.dice_rolled"

    /** 回應處理完成。 */
    const val ROUND_REACTION_NONE = "mahjongcraft.history_screen.round.reaction.none"

    /** 單局準備步驟格式。 */
    const val ROUND_PREPARATION = "mahjongcraft.history_screen.round.preparation"

    /** 準備步驟格式。 */
    const val ROUND_PREPARATION_STEP = "mahjongcraft.history_screen.round.preparation.step"

    /** 準備完成。 */
    const val ROUND_PREPARATION_NONE = "mahjongcraft.history_screen.round.preparation.none"

    /** 接續準備步驟。 */
    const val ROUND_PREPARATION_NEXT = "mahjongcraft.history_screen.round.preparation.next"

    /** 準備步驟開始。 */
    const val ROUND_PREPARATION_STARTED = "mahjongcraft.history_screen.round.preparation.started"

    /** 玩家已提交準備操作。 */
    const val ROUND_PREPARATION_SUBMITTED = "mahjongcraft.history_screen.round.preparation.submitted"

    /** 單局完成事件格式。 */
    const val ROUND_COMPLETION = "mahjongcraft.history_screen.round.completion"

    /** 和牌結算事件格式。 */
    const val ROUND_WIN_SETTLEMENT = "mahjongcraft.history_screen.round.win_settlement"

    /** 單局規則效果事件格式。 */
    const val ROUND_RULE_EFFECT = "mahjongcraft.history_screen.round.rule_effect"

    /** 其他事件。 */
    const val ROUND_UNKNOWN_FACT = "mahjongcraft.history_screen.round.unknown_fact"

    /** 整場結束格式。 */
    const val ROUND_MATCH_COMPLETION = "mahjongcraft.history_screen.round.match_completion"

    /** 已完成預定賽程。 */
    const val ROUND_OUTCOME_SCHEDULE_COMPLETED = "mahjongcraft.history_screen.round.outcome.schedule_completed"

    /** 其他結算原因。 */
    const val ROUND_OUTCOME_OTHER = "mahjongcraft.history_screen.round.outcome.other"

    /** 已達終局點數。 */
    const val ROUND_OUTCOME_TARGET_SCORE_REACHED = "mahjongcraft.history_screen.round.outcome.target_score_reached"

    /** 已達延長賽上限。 */
    const val ROUND_OUTCOME_EXTRA_ROUND_LIMIT_REACHED = "mahjongcraft.history_screen.round.outcome.extra_round_limit_reached"

    /** 最後莊家符合終局條件。 */
    const val ROUND_OUTCOME_DEALER_TOP_FINISH = "mahjongcraft.history_screen.round.outcome.dealer_top_finish"

    /** 玩家分數達到終止門檻。 */
    const val ROUND_OUTCOME_PLAYER_BUSTED = "mahjongcraft.history_screen.round.outcome.player_busted"

    /** 和牌。 */
    const val ROUND_CLASSIFICATION_WIN = "mahjongcraft.history_screen.round.classification.win"

    /** 流局。 */
    const val ROUND_CLASSIFICATION_EXHAUSTIVE_DRAW = "mahjongcraft.history_screen.round.classification.exhaustive_draw"

    /** 途中流局。 */
    const val ROUND_CLASSIFICATION_ABORTIVE_DRAW = "mahjongcraft.history_screen.round.classification.abortive_draw"

    /** 擴充規則結算。 */
    const val ROUND_CLASSIFICATION_EXTENSION = "mahjongcraft.history_screen.round.classification.extension"

    /** 其他結算。 */
    const val ROUND_CLASSIFICATION_OTHER = "mahjongcraft.history_screen.round.classification.other"

    /** 其他操作。 */
    const val ROUND_ACTION_OTHER = "mahjongcraft.history_screen.round.action.other"

    /** 沒有語意事實提示。 */
    const val ROUND_EMPTY_FACTS = "mahjongcraft.history_screen.round.empty_facts"

    /** 單局沒有事件提示。 */
    const val ROUND_NO_EVENTS = "mahjongcraft.history_screen.round.no_events"

    /** 單局尚未取得結算提示。 */
    const val ROUND_NO_OUTCOME_YET = "mahjongcraft.history_screen.round.no_outcome_yet"

    /** 單局沒有結算提示。 */
    const val ROUND_NO_OUTCOME = "mahjongcraft.history_screen.round.no_outcome"

    /** 單局頁面範圍格式。 */
    const val ROUND_PAGE_RANGE = "mahjongcraft.history_screen.round.page_range"

    /** 和牌明細標題。 */
    const val ROUND_WIN_DETAILS = "mahjongcraft.history_screen.round.win_details"

    /** 未記錄和牌明細。 */
    const val ROUND_WIN_DETAILS_UNAVAILABLE = "mahjongcraft.history_screen.round.win_details_unavailable"

    /** 單局交易時間格式。 */
    const val ROUND_TIMING = "mahjongcraft.history_screen.round.timing"

    /** 自摸結算名稱。 */
    const val ROUND_OUTCOME_TSUMO = "mahjongcraft.history_screen.round.outcome.tsumo"

    /** 榮和結算名稱。 */
    const val ROUND_OUTCOME_RON = "mahjongcraft.history_screen.round.outcome.ron"

    /** 流局滿貫結算名稱。 */
    const val ROUND_OUTCOME_NAGASHI_MANGAN = "mahjongcraft.history_screen.round.outcome.nagashi_mangan"

    /** 中立槓牌動作名稱。 */
    const val ROUND_ACTION_KAN = "mahjongcraft.history_screen.round.action.kan"

    /** 中立摸牌動作名稱。 */
    const val ROUND_ACTION_DRAW = "mahjongcraft.history_screen.round.action.draw"

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
