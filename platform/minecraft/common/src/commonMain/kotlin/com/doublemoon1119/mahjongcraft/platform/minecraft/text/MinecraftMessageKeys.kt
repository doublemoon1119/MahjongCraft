package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/** Minecraft 玩家可見訊息使用的 translation key 單一來源。 */
object MinecraftMessageKeys {
    /** 暫時排除名單達到容量上限。 */
    const val ROOM_EXCLUSION_LIST_FULL = "mahjongcraft.room.exclusion_list_full"

    /** 已入座測試角色已建立，由正式掃描加入。 */
    const val DEBUG_SEATED_PARTICIPANT_CREATED = "mahjongcraft.message.debug.seated_participant.created"

    /** 測試需要房主等待室與可用家具座位。 */
    const val DEBUG_SEATED_PARTICIPANT_UNAVAILABLE = "mahjongcraft.message.debug.seated_participant.unavailable"

    /** 暫時排除尚未解除。 */
    const val ROOM_PARTICIPANT_EXCLUDED = "mahjongcraft.message.room_participant_excluded"

    /** 房間或固定席位不允許本次操作。 */
    const val ROOM_LAYOUT_CONFLICT = "mahjongcraft.message.room_layout_conflict"

    /** 開局時無法確認外部角色已正確入座。 */
    const val SEATED_PARTICIPANT_UNAVAILABLE = "mahjongcraft.message.seated_participant_unavailable"

    /** 加分牌、籌碼或點棒等規則附加物件無法完整呈現。 */
    const val TABLE_RULE_PROPS_UNAVAILABLE = "mahjongcraft.message.table_rule_props_unavailable"

    /** 對局已開始，玩家無法中途加入。 */
    const val GAME_ALREADY_STARTED = "mahjongcraft.message.game_already_started"

    /** 已建立麻將遊戲。 */
    const val GAME_CREATED = "mahjongcraft.message.game_created"

    /** [GAME_CREATED] 訊息句尾可 hover 顯示座標的 `[位置]` 標籤文字。 */
    const val GAME_CREATED_LOCATION_LABEL = "mahjongcraft.message.game_created_location_label"

    /** 已加入麻將遊戲。 */
    const val GAME_JOINED = "mahjongcraft.message.game_joined"

    /** 玩家不在指定麻將遊戲中。 */
    const val PLAYER_NOT_IN_GAME = "mahjongcraft.message.player_not_in_game"

    /** 玩家目前不屬於任何麻將遊戲。 */
    const val PLAYER_NOT_IN_ANY_GAME = "mahjongcraft.message.player_not_in_any_game"

    /** Debug 決策畫面文字卡片的標題。 */
    const val DEBUG_DECISION_TEXT_CARD_LABEL = "mahjongcraft.message.debug.decision.text_card.label"

    /** Debug 決策畫面文字卡片的內文。 */
    const val DEBUG_DECISION_TEXT_CARD_BODY = "mahjongcraft.message.debug.decision.text_card.body"

    /** Debug 決策畫面文字卡片的提示文字。 */
    const val DEBUG_DECISION_TEXT_CARD_HINT = "mahjongcraft.message.debug.decision.text_card.hint"

    /** Debug 決策畫面文字卡片的說明文字。 */
    const val DEBUG_DECISION_TEXT_CARD_DESCRIPTION = "mahjongcraft.message.debug.decision.text_card.description"

    /** 開發用自動拔北三人日麻的規則名稱。 */
    const val DEBUG_RULE_MODULE_AUTO_NORTH_RIICHI = "mahjongcraft.message.debug.rule_module.auto_north_riichi"

    /** 開發用和牌者坐莊、每局換位四人日麻的規則名稱。 */
    const val DEBUG_RULE_MODULE_WINNER_DEALER_RIICHI = "mahjongcraft.message.debug.rule_module.winner_dealer_riichi"

    /** 開發用可宣告不聽四人日麻的規則名稱。 */
    const val DEBUG_RULE_MODULE_NOTEN_DECLARATION_RIICHI = "mahjongcraft.message.debug.rule_module.noten_declaration_riichi"

    /** 開發用可宣告不聽規則中，流局時公開聽牌的選項名稱。 */
    const val DEBUG_DECISION_REVEAL_TENPAI_LABEL = "mahjongcraft.message.debug.decision.reveal_tenpai.label"

    /** 開發用可宣告不聽規則中，流局時公開聽牌的選項說明。 */
    const val DEBUG_DECISION_REVEAL_TENPAI_DESCRIPTION = "mahjongcraft.message.debug.decision.reveal_tenpai.description"

    /** 開發用可宣告不聽規則中，流局時宣告不聽的選項與動作名稱。 */
    const val DEBUG_DECISION_DECLARE_NOTEN_LABEL = "mahjongcraft.message.debug.decision.declare_noten.label"

    /** 開發用可宣告不聽規則中，流局時宣告不聽的選項說明。 */
    const val DEBUG_DECISION_DECLARE_NOTEN_DESCRIPTION = "mahjongcraft.message.debug.decision.declare_noten.description"

    /** 對局進行中禁止主動離開。 */
    const val GAME_LEAVE_DENIED_WHILE_PLAYING = "mahjongcraft.message.game_leave_denied_while_playing"

    /** 房主已解散麻將遊戲。 */
    const val GAME_DISSOLVED = "mahjongcraft.message.game_dissolved"

    /** 玩家已離開麻將遊戲。 */
    const val GAME_LEFT = "mahjongcraft.message.game_left"

    /** 離開麻將遊戲失敗。 */
    const val GAME_LEAVE_FAILED = "mahjongcraft.message.game_leave_failed"

    /** 玩家已參與另一場麻將遊戲。 */
    const val PLAYER_ALREADY_IN_GAME = "mahjongcraft.message.player_already_in_game"

    /** 加入麻將遊戲失敗。 */
    const val GAME_JOIN_FAILED = "mahjongcraft.message.game_join_failed"

    /** 準備狀態切換訊息的前綴，後面接切換前後的準備狀態文字。 */
    const val READY_TOGGLE_PREFIX = "mahjongcraft.message.ready_toggle_prefix"

    /** 「準備」狀態文字，用於準備狀態切換訊息中上色顯示。 */
    const val READY_STATE_READY = "mahjongcraft.message.ready_state_ready"

    /** 「尚未準備」狀態文字，用於準備狀態切換訊息中上色顯示。 */
    const val READY_STATE_NOT_READY = "mahjongcraft.message.ready_state_not_ready"

    /** 遊戲主持人不參與準備機制。 */
    const val HOST_READY_NOT_REQUIRED = "mahjongcraft.message.host_ready_not_required"

    /** 只有遊戲主持人可以開始對局。 */
    const val NOT_GAME_HOST = "mahjongcraft.message.not_game_host"

    /** 目前人數不符合規則限制的人數區間，無法開始對局。 */
    const val INVALID_PLAYER_COUNT = "mahjongcraft.message.invalid_player_count"

    /** 還有玩家尚未準備好，無法開始對局。 */
    const val NOT_ALL_PLAYERS_READY = "mahjongcraft.message.not_all_players_ready"

    /** 開始遊戲失敗。 */
    const val GAME_START_FAILED = "mahjongcraft.message.game_start_failed"

    /** 房間設定的規則目前無法使用。 */
    const val RULE_UNAVAILABLE = "mahjongcraft.message.rule_unavailable"

    /** 送來的房間設定無效，未套用。 */
    const val GAME_CONFIG_INVALID = "mahjongcraft.message.game_config_invalid"

    /** 指定的 AI 策略沒有登記。 */
    const val AI_STRATEGY_NOT_FOUND = "mahjongcraft.message.ai_strategy_not_found"

    /** 指定的麻將桌不存在，或已超出目前可互動的範圍。 */
    const val TABLE_NOT_REACHABLE = "mahjongcraft.message.table_not_reachable"

    /** 部分成員離線導致無法開始對局，帶一個離線成員名單參數（一個 `%s`，已用語系慣例的分隔符號串接）。 */
    const val START_BLOCKED_OFFLINE_PLAYERS = "mahjongcraft.message.start_blocked_offline_players"

    /** 部分成員距離桌子太遠導致無法開始對局，帶一個成員名單參數（一個 `%s`，已用語系慣例的分隔符號串接）。 */
    const val START_BLOCKED_DISTANT_PLAYERS = "mahjongcraft.message.start_blocked_distant_players"

    /** 桌面上方有方塊擋住擺牌，無法開始對局。 */
    const val START_BLOCKED_TABLE_SPACE = "mahjongcraft.message.start_blocked_table_space"

    /** 已新增 AI 玩家，帶策略顯示名稱參數（`%s`），措辭比照 [KICK_CANDIDATE_AI_LABEL] 的稱呼方式。 */
    const val AI_ADDED = "mahjongcraft.message.ai_added"

    /** 新增 AI 玩家失敗。 */
    const val ADD_AI_FAILED = "mahjongcraft.message.add_ai_failed"

    /** 遊戲人數已滿，無法再新增 AI 玩家。 */
    const val GAME_FULL = "mahjongcraft.message.game_full"

    /** 已將指定玩家移出遊戲（房主視角）。 */
    const val PLAYER_KICKED = "mahjongcraft.message.player_kicked"

    /** 已被遊戲主持人移出遊戲（被踢玩家視角）。 */
    const val KICKED_FROM_GAME = "mahjongcraft.message.kicked_from_game"

    /** 房主不能將自己移出遊戲。 */
    const val CANNOT_KICK_SELF = "mahjongcraft.message.cannot_kick_self"

    /** 將玩家移出遊戲失敗。 */
    const val KICK_FAILED = "mahjongcraft.message.kick_failed"

    /**
     * 已更換 AI 策略，依序帶序號、舊策略顯示名稱、新策略顯示名稱三個參數（各一個 `%s`），措辭比照
     * [READY_TOGGLE_PREFIX] 準備狀態切換訊息的「舊狀態 → 新狀態」呈現方式。
     */
    const val AI_STRATEGY_CHANGED = "mahjongcraft.message.ai_strategy_changed"

    /** 指定的新策略與目前策略相同，操作仍成功（冪等）；依序帶序號與策略顯示名稱兩個參數（各一個 `%s`）。 */
    const val AI_STRATEGY_UNCHANGED = "mahjongcraft.message.ai_strategy_unchanged"

    /** 目標玩家不是 AI，無法更換策略。 */
    const val TARGET_NOT_AI = "mahjongcraft.message.target_not_ai"

    /** 結算排行中包牌責任者的付款原因。 */
    const val PLAYER_INDICATOR_PAO = "mahjongcraft.message.player_indicator_pao"

    /** 更換 AI 策略失敗。 */
    const val CHANGE_AI_STRATEGY_FAILED = "mahjongcraft.message.change_ai_strategy_failed"

    /** `kick` 指令補全中 AI 候選項目的提示文字，依序帶序號與策略顯示名稱兩個參數（各一個 `%s`）。 */
    const val KICK_CANDIDATE_AI_LABEL = "mahjongcraft.message.kick_candidate_ai_label"

    /** 內建隨機出牌 AI 策略的顯示名稱；未登記顯示名稱的策略使用其識別字串。 */
    const val AI_STRATEGY_RANDOM = "mahjongcraft.message.ai_strategy_random"

    /** 內建初級 AI 策略的顯示名稱，用途同 [AI_STRATEGY_RANDOM]。 */
    const val AI_STRATEGY_BEGINNER = "mahjongcraft.message.ai_strategy_beginner"

    /** 內建中級 AI 策略的顯示名稱，用途同 [AI_STRATEGY_RANDOM]。 */
    const val AI_STRATEGY_INTERMEDIATE = "mahjongcraft.message.ai_strategy_intermediate"

    /** 內建高級 AI 策略的顯示名稱，用途同 [AI_STRATEGY_RANDOM]。 */
    const val AI_STRATEGY_ADVANCED = "mahjongcraft.message.ai_strategy_advanced"

    /**
     * 已變更遊戲設定，依序帶舊設定、新設定兩個可互動文字參數（各一個 `%s`），措辭比照
     * [READY_TOGGLE_PREFIX] 準備狀態切換訊息的「舊狀態 → 新狀態」呈現方式。
     */
    const val GAME_CONFIG_CHANGED = "mahjongcraft.message.game_config_changed"

    /** 提供的新設定與目前設定相同，操作仍成功（冪等）；帶一個目前設定的可互動文字參數（`%s`）。 */
    const val GAME_CONFIG_UNCHANGED = "mahjongcraft.message.game_config_unchanged"

    /** 變更遊戲設定失敗。 */
    const val CHANGE_GAME_CONFIG_FAILED = "mahjongcraft.message.change_game_config_failed"

    /** [GAME_CONFIG_CHANGED]／[GAME_CONFIG_UNCHANGED]／[SHOW_GAME_CONFIG] 中可互動文字本身顯示的簡短標籤。 */
    const val GAME_CONFIG_LABEL = "mahjongcraft.message.game_config_label"

    /**
     * 顯示所在麻將遊戲目前設定，帶一個可互動文字參數（一個 `%s`，見 [GAME_CONFIG_LABEL]）；點擊該文字
     * 會開啟設定編輯畫面。
     */
    const val SHOW_GAME_CONFIG = "mahjongcraft.message.show_game_config"

    // ── 麻將牌顯示文字（見 MahjongTileDisplayText.kt） ──────────────────────

    /** 萬子數牌顯示文字，帶一個數值參數（一個 `%s`）。 */
    const val TILE_SUIT_CHARACTER = "mahjongcraft.message.tile_suit_character"

    /** 筒子數牌顯示文字，帶一個數值參數（一個 `%s`）。 */
    const val TILE_SUIT_DOT = "mahjongcraft.message.tile_suit_dot"

    /** 條子數牌顯示文字，帶一個數值參數（一個 `%s`）。 */
    const val TILE_SUIT_BAMBOO = "mahjongcraft.message.tile_suit_bamboo"

    /** 東風牌顯示文字。 */
    const val TILE_HONOR_EAST = "mahjongcraft.message.tile_honor_east"

    /** 南風牌顯示文字。 */
    const val TILE_HONOR_SOUTH = "mahjongcraft.message.tile_honor_south"

    /** 西風牌顯示文字。 */
    const val TILE_HONOR_WEST = "mahjongcraft.message.tile_honor_west"

    /** 北風牌顯示文字。 */
    const val TILE_HONOR_NORTH = "mahjongcraft.message.tile_honor_north"

    /** 中（紅中）顯示文字。 */
    const val TILE_HONOR_RED = "mahjongcraft.message.tile_honor_red"

    /** 發（發財）顯示文字。 */
    const val TILE_HONOR_GREEN = "mahjongcraft.message.tile_honor_green"

    /** 白（白板）顯示文字。 */
    const val TILE_HONOR_WHITE = "mahjongcraft.message.tile_honor_white"

    // ── 對局動作顯示文字（見 GameActionDisplayText.kt） ──────────────────────

    /** 打出，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_DISCARD = "mahjongcraft.message.game_action_discard"

    /** 自摸，不帶參數。 */
    const val GAME_ACTION_TSUMO = "mahjongcraft.message.game_action_tsumo"

    /** 吃，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_CHI = "mahjongcraft.message.game_action_chi"

    /** 碰，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_PON = "mahjongcraft.message.game_action_pon"

    /** 明槓（大明槓），帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_KAN_OPEN = "mahjongcraft.message.game_action_kan_open"

    /** 暗槓，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_KAN_CLOSED = "mahjongcraft.message.game_action_kan_closed"

    /** 加槓（小明槓），帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_KAN_ADDED = "mahjongcraft.message.game_action_kan_added"

    /** 榮和，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_RON = "mahjongcraft.message.game_action_ron"

    /** 過（不執行任何鳴牌或胡牌動作），不帶參數。 */
    const val GAME_ACTION_PASS = "mahjongcraft.message.game_action_pass"

    /** 其他規則專屬流局原因的通用 fallback 顯示文字，不帶參數。 */
    const val GAME_ACTION_EXHAUSTIVE_DRAW = "mahjongcraft.message.game_action_exhaustive_draw"

    /** 一般荒牌流局的結算標題，不帶參數。 */
    const val EXHAUSTIVE_DRAW_REASON_NORMAL = "mahjongcraft.message.exhaustive_draw_reason.normal"

    /** round-result 訊息末尾可 hover 顯示完整結算內容的標籤。 */
    const val ROUND_RESULT_DETAILS_LABEL = "mahjongcraft.message.round_result_details_label"

    /** 回合結束廣播標題，帶一個動作顯示文字參數（`%s`，見上方 `GAME_ACTION_*`）。 */
    const val ROUND_RESULT_BROADCAST = "mahjongcraft.message.round_result_broadcast"

    /** 和牌後本局繼續時的結算廣播標題，帶一個結果顯示文字參數（一個 `%s`）。 */
    const val CONTINUING_WIN_RESULT_BROADCAST = "mahjongcraft.message.continuing_win_result_broadcast"

    /**
     * 回合結束廣播內每一位玩家的一行，帶玩家名稱、回合前名次、回合後名次、名次變化符號
     * （`↑`/`↓`/`→`）、回合前分數、回合後分數共六個參數（六個 `%s`）——固定列出所有玩家，不只是
     * 分數有變化的人（自己分數沒變，也可能因為別人分數變了而被擠掉名次），供排行榜動畫使用的名次
     * 升降跟分數增減兩種資料都在同一行裡。
     */
    const val ROUND_RESULT_PLAYER_LINE = "mahjongcraft.message.round_result_player_line"

    /** 流局結算展示中需要公開牌面的流局宣告狀態。 */
    const val EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_DRAW_DECLARATION = "mahjongcraft.message.exhaustive_draw_settlement_status.draw_declaration"

    /** 流局結算排行榜中的東風位。 */
    const val EXHAUSTIVE_DRAW_SETTLEMENT_WIND_EAST = "mahjongcraft.message.exhaustive_draw_settlement_wind.east"

    /** 流局結算排行榜中的南風位。 */
    const val EXHAUSTIVE_DRAW_SETTLEMENT_WIND_SOUTH = "mahjongcraft.message.exhaustive_draw_settlement_wind.south"

    /** 流局結算排行榜中的西風位。 */
    const val EXHAUSTIVE_DRAW_SETTLEMENT_WIND_WEST = "mahjongcraft.message.exhaustive_draw_settlement_wind.west"

    /** 流局結算排行榜中的北風位。 */
    const val EXHAUSTIVE_DRAW_SETTLEMENT_WIND_NORTH = "mahjongcraft.message.exhaustive_draw_settlement_wind.north"

    /** [GameAction.MatchEnded] 的顯示文字，不帶參數。 */
    const val GAME_ACTION_MATCH_ENDED = "mahjongcraft.message.game_action_match_ended"

    /** 對局結束廣播的標題，不帶參數。 */
    const val MATCH_RESULT_BROADCAST = "mahjongcraft.message.match_result_broadcast"

    /** 終局結算面板的標題，不帶參數。 */
    const val MATCH_SETTLEMENT_TITLE = "mahjongcraft.message.match_settlement.title"

    /** 終局頒獎面板的名次文字，帶一個名次參數（`%s`）。 */
    const val MATCH_SETTLEMENT_RANK = "mahjongcraft.message.match_settlement.rank"

    /** 對局結束排名清單內每一行，帶名次、玩家名稱、最終分數三個參數（三個 `%s`）。 */
    const val RANKING_LINE = "mahjongcraft.message.ranking_line"

    /** [GameAction.DiceRolled] 的一般動作顯示文字，不帶參數。 */
    const val GAME_ACTION_DICE_ROLLED = "mahjongcraft.message.game_action_dice_rolled"

    /** 擲骰結果 3D 面板的合計文字，帶一個整數參數。 */
    const val DICE_ROLL_TOTAL = "mahjongcraft.message.dice_roll.total"

    // ── 對局階段指令回饋 ──────────────────────────────────────────────────

    /** 還沒輪到該玩家的回合。 */
    const val NOT_YOUR_TURN = "mahjongcraft.message.not_your_turn"

    /** 玩家已逾時，後續操作交由伺服器自動處理。 */
    const val FORCED_AUTO_PLAY_ACTIVE = "mahjongcraft.message.forced_auto_play_active"

    /** 該動作在目前桌況下不合法。 */
    const val ILLEGAL_GAME_ACTION = "mahjongcraft.message.illegal_game_action"

    /** 牌山已摸盡。 */
    const val WALL_EXHAUSTED = "mahjongcraft.message.wall_exhausted"

    /** 目前規則不支援這個動作。 */
    const val UNSUPPORTED_GAME_ACTION = "mahjongcraft.message.unsupported_game_action"

    /** 桌面正在播放呈現動畫（例如擲骰），暫時無法送出操作。 */
    const val TABLE_ANIMATION_BUSY = "mahjongcraft.message.table_animation_busy"

    /** 對局提前終止，帶一個本地化終止原因文字參數（`%s`）。 */
    const val GAME_ABORTED = "mahjongcraft.message.game_aborted"

    /** 規則缺少和牌或流局結算。 */
    const val GAME_ABORT_REASON_SETTLEMENT = "mahjongcraft.message.game_abort_reason_settlement"

    /** 規則無法決定後續局位。 */
    const val GAME_ABORT_REASON_PROGRESSION = "mahjongcraft.message.game_abort_reason_progression"

    /** 伺服器無法完成合法代打。 */
    const val GAME_ABORT_REASON_AUTOMATIC = "mahjongcraft.message.game_abort_reason_automatic"

    /** 規則執行失敗。 */
    const val GAME_ABORT_REASON_RULE = "mahjongcraft.message.game_abort_reason_rule"

    /** 找不到參與遊戲的 AI 玩家時顯示的中止原因。 */
    const val GAME_ABORT_REASON_AI_PLAYER = "mahjongcraft.message.game_abort_reason_ai_player"

    /** 未知中止原因的通用訊息。 */
    const val GAME_ABORT_REASON_UNKNOWN = "mahjongcraft.message.game_abort_reason_unknown"

    // ── 手牌查詢指令（`/mahjongcraft game hand`） ──────────────────────────

    /** 手牌列表標題。 */
    const val HAND_TITLE = "mahjongcraft.message.hand_title"

    /** 副露列表標題。 */
    const val HAND_MELDS_TITLE = "mahjongcraft.message.hand_melds_title"

    /** 目前可執行的特殊動作列表標題。 */
    const val HAND_LEGAL_ACTIONS_TITLE = "mahjongcraft.message.hand_legal_actions_title"

    /** 輪到自己回合、已摸牌，但沒有特殊動作可用時的提示，指引玩家改用 `discard`。 */
    const val HAND_NO_LEGAL_ACTIONS = "mahjongcraft.message.hand_no_legal_actions"

    /** 有資格回應捨牌／搶槓、但實際上沒有任何合法回應（連過牌都不需要）時的提示。 */
    const val HAND_NO_RESPONSE_AVAILABLE = "mahjongcraft.message.hand_no_response_available"

    /** 還沒輪到自己、也沒有資格回應時的提示，避免誤以為隨時都能 `discard`。 */
    const val HAND_WAITING = "mahjongcraft.message.hand_waiting"

    // ── 牌面輔助標籤指令（`/mahjongcraft_client label toggle`，純 client-only） ─────

    // ── 桌面中央局況顯示（見 `MahjongRoundInfoEntityRenderer`） ────────────────

    /** 局況顯示牌山剩餘張數，帶一個張數參數（一個 `%d`）。 */
    const val ROUND_INFO_WALL_REMAINING = "mahjongcraft.message.round_info_wall_remaining"

    /** 牌面角落輔助標籤（給非中文圈玩家看的數字/字母）切換訊息的前綴，後面接切換前後的狀態文字。 */
    const val TILE_LABELS_TOGGLE_PREFIX = "mahjongcraft.message.tile_labels_toggle_prefix"

    /** 「開啟」狀態文字，用於牌面輔助標籤切換訊息中上色顯示。 */
    const val TILE_LABELS_STATE_ON = "mahjongcraft.message.tile_labels_state_on"

    /** 「關閉」狀態文字，用於牌面輔助標籤切換訊息中上色顯示。 */
    const val TILE_LABELS_STATE_OFF = "mahjongcraft.message.tile_labels_state_off"

    /** 自動整理手牌切換訊息的前綴，後面接切換前後的狀態文字。 */
    const val HAND_SORT_TOGGLE_PREFIX = "mahjongcraft.message.hand_sort_toggle_prefix"

    /** 「開啟」狀態文字，用於自動整理手牌切換訊息中上色顯示。 */
    const val HAND_SORT_STATE_ON = "mahjongcraft.message.hand_sort_state_on"

    /** 「關閉」狀態文字，用於自動整理手牌切換訊息中上色顯示。 */
    const val HAND_SORT_STATE_OFF = "mahjongcraft.message.hand_sort_state_off"

    /** 自動操作已套用，帶控制名稱與狀態參數。 */
    const val AUTOMATIC_CONTROL_UPDATED = "mahjongcraft.message.automatic_control_updated"

    /** 自動操作原本已是要求狀態，帶控制名稱與狀態參數。 */
    const val AUTOMATIC_CONTROL_UNCHANGED = "mahjongcraft.message.automatic_control_unchanged"

    /** 自動操作請求正在等待伺服器確認，帶控制名稱參數。 */
    const val AUTOMATIC_CONTROL_WAITING = "mahjongcraft.message.automatic_control_waiting"

    /** 已有另一筆自動操作更新等待伺服器確認。 */
    const val AUTOMATIC_CONTROL_PENDING = "mahjongcraft.message.automatic_control_pending"

    /** 目前沒有可調整的本局自動操作。 */
    const val AUTOMATIC_CONTROL_UNAVAILABLE = "mahjongcraft.message.automatic_control_unavailable"

    /** 目前規則不支援指定自動操作，帶控制名稱參數。 */
    const val AUTOMATIC_CONTROL_UNSUPPORTED = "mahjongcraft.message.automatic_control_unsupported"

    /** 自動操作請求未能送出。 */
    const val AUTOMATIC_CONTROL_SEND_FAILED = "mahjongcraft.message.automatic_control_send_failed"

    /** 永久偏好已保存，但無法同步至伺服器。 */
    const val AUTOMATIC_CONTROL_PREFERENCE_SYNC_FAILED = "mahjongcraft.message.automatic_control_preference_sync_failed"

    /** 伺服器拒絕自動操作更新。 */
    const val AUTOMATIC_CONTROL_REJECTED = "mahjongcraft.message.automatic_control_rejected"

    /** 自動操作資料已過期。 */
    const val AUTOMATIC_CONTROL_STALE = "mahjongcraft.message.automatic_control_stale"

    /** 自動操作更新尚未收到確認，正在重新同步。 */
    const val AUTOMATIC_CONTROL_RESYNCING = "mahjongcraft.message.automatic_control_resyncing"

    /** 重新同步確認自動操作更新沒有生效。 */
    const val AUTOMATIC_CONTROL_NOT_APPLIED = "mahjongcraft.message.automatic_control_not_applied"

    /** 無法確認自動操作更新是否生效。 */
    const val AUTOMATIC_CONTROL_UNCONFIRMED = "mahjongcraft.message.automatic_control_unconfirmed"

    /** 決策提交尚未收到確認，正在重新同步。 */
    const val DECISION_CONFIRMATION_RESYNCING = "mahjongcraft.message.decision_confirmation_resyncing"

    /** 決策提交沒有收到確認，玩家可以重新選擇。 */
    const val DECISION_CONFIRMATION_RETRY = "mahjongcraft.message.decision_confirmation_retry"

    /** 自動操作狀態文字「開啟」。 */
    const val AUTOMATIC_CONTROL_STATE_ON = "mahjongcraft.message.automatic_control_state_on"

    /** 自動操作狀態文字「關閉」。 */
    const val AUTOMATIC_CONTROL_STATE_OFF = "mahjongcraft.message.automatic_control_state_off"

    /** 麻將功能因存檔資料無法讀取而停用，請通知管理員。 */
    const val DATA_UNAVAILABLE = "mahjongcraft.message.data_unavailable"

    /** 進入世界時向管理員列出無法讀取的麻將資料。 */
    const val DATA_PROBLEM_HEADER = "mahjongcraft.message.data_problem_header"

    /** 房間、對局與待寫牌譜無法讀取，麻將功能已停用。 */
    const val DATA_PROBLEM_AUTHORITATIVE_STATE = "mahjongcraft.message.data_problem_authoritative_state"

    /** 桌子位置索引無法讀取，已改從世界重新建立。 */
    const val DATA_PROBLEM_TABLE_LOCATIONS = "mahjongcraft.message.data_problem_table_locations"

    /** 牌譜資料庫無法開啟，已暫停牌譜寫入與查閱。 */
    const val DATA_PROBLEM_HISTORY_DATABASE = "mahjongcraft.message.data_problem_history_database"

    /** 角色資料無法讀取，已保留原區段並停用角色修改。 */
    const val DATA_PROBLEM_AI_CHARACTERS = "mahjongcraft.message.data_problem_ai_characters"

    /** 可能缺少提供某型別的擴充；參數為擴充的命名空間。 */
    const val DATA_CAUSE_MISSING_EXTENSION = "mahjongcraft.message.data_cause_missing_extension"

    /** 資料由較新版本保存；參數為資料版本與目前支援的版本。 */
    const val DATA_CAUSE_NEWER_FORMAT = "mahjongcraft.message.data_cause_newer_format"

    /** 無法判斷原因。 */
    const val DATA_CAUSE_UNREADABLE = "mahjongcraft.message.data_cause_unreadable"

    /** 無法讀取的檔案路徑；參數為相對於世界資料夾的路徑。 */
    const val DATA_FILE = "mahjongcraft.message.data_file"

    /** 無法讀取且未被變更的檔案路徑；參數為相對於世界資料夾的路徑。 */
    const val DATA_FILE_UNCHANGED = "mahjongcraft.message.data_file_unchanged"

    /** 指向檔案路徑時說明點擊可複製。 */
    const val DATA_FILE_COPY_HINT = "mahjongcraft.message.data_file_copy_hint"

    /** 指向後顯示原因細節的標籤文字。 */
    const val DATA_DETAIL_LABEL = "mahjongcraft.message.data_detail_label"

    /** 缺少擴充時的原因細節；參數為存檔中的型別識別碼。 */
    const val DATA_CAUSE_MISSING_EXTENSION_DETAIL = "mahjongcraft.message.data_cause_missing_extension_detail"

    /** 無法判斷原因時，接在錯誤摘要後的可能原因說明。 */
    const val DATA_CAUSE_UNREADABLE_DETAIL = "mahjongcraft.message.data_cause_unreadable_detail"

    /** 確定無法恢復時的最後手段與會失去的資料。 */
    const val DATA_RECOVERY_LAST_RESORT = "mahjongcraft.message.data_recovery_last_resort"

    /** Minecraft 語系資源必須提供的全部玩家回饋 key。 */
    val ALL: Set<String> = setOf(
        DEBUG_SEATED_PARTICIPANT_CREATED,
        DEBUG_SEATED_PARTICIPANT_UNAVAILABLE,
        ROOM_PARTICIPANT_EXCLUDED,
        ROOM_LAYOUT_CONFLICT,
        SEATED_PARTICIPANT_UNAVAILABLE,
        TABLE_RULE_PROPS_UNAVAILABLE,
        DATA_FILE,
        DATA_FILE_UNCHANGED,
        DATA_FILE_COPY_HINT,
        DATA_DETAIL_LABEL,
        DATA_CAUSE_MISSING_EXTENSION_DETAIL,
        DATA_CAUSE_UNREADABLE_DETAIL,
        DATA_UNAVAILABLE,
        DATA_PROBLEM_HEADER,
        DATA_PROBLEM_AUTHORITATIVE_STATE,
        DATA_PROBLEM_TABLE_LOCATIONS,
        DATA_PROBLEM_HISTORY_DATABASE,
        DATA_PROBLEM_AI_CHARACTERS,
        DATA_CAUSE_MISSING_EXTENSION,
        DATA_CAUSE_NEWER_FORMAT,
        DATA_CAUSE_UNREADABLE,
        DATA_RECOVERY_LAST_RESORT,
        GAME_ALREADY_STARTED,
        GAME_CREATED,
        GAME_CREATED_LOCATION_LABEL,
        GAME_JOINED,
        PLAYER_NOT_IN_GAME,
        PLAYER_NOT_IN_ANY_GAME,
        DEBUG_DECISION_TEXT_CARD_LABEL,
        DEBUG_DECISION_TEXT_CARD_BODY,
        DEBUG_DECISION_TEXT_CARD_HINT,
        DEBUG_DECISION_TEXT_CARD_DESCRIPTION,
        DEBUG_RULE_MODULE_AUTO_NORTH_RIICHI,
        DEBUG_RULE_MODULE_WINNER_DEALER_RIICHI,
        DEBUG_RULE_MODULE_NOTEN_DECLARATION_RIICHI,
        DEBUG_DECISION_REVEAL_TENPAI_LABEL,
        DEBUG_DECISION_REVEAL_TENPAI_DESCRIPTION,
        DEBUG_DECISION_DECLARE_NOTEN_LABEL,
        DEBUG_DECISION_DECLARE_NOTEN_DESCRIPTION,
        GAME_LEAVE_DENIED_WHILE_PLAYING,
        GAME_DISSOLVED,
        GAME_LEFT,
        GAME_LEAVE_FAILED,
        PLAYER_ALREADY_IN_GAME,
        GAME_JOIN_FAILED,
        READY_TOGGLE_PREFIX,
        READY_STATE_READY,
        READY_STATE_NOT_READY,
        HOST_READY_NOT_REQUIRED,
        NOT_GAME_HOST,
        INVALID_PLAYER_COUNT,
        NOT_ALL_PLAYERS_READY,
        GAME_START_FAILED,
        RULE_UNAVAILABLE,
        GAME_CONFIG_INVALID,
        AI_STRATEGY_NOT_FOUND,
        TABLE_NOT_REACHABLE,
        START_BLOCKED_OFFLINE_PLAYERS,
        START_BLOCKED_DISTANT_PLAYERS,
        START_BLOCKED_TABLE_SPACE,
        AI_ADDED,
        ADD_AI_FAILED,
        GAME_FULL,
        PLAYER_KICKED,
        KICKED_FROM_GAME,
        CANNOT_KICK_SELF,
        KICK_FAILED,
        KICK_CANDIDATE_AI_LABEL,
        AI_STRATEGY_RANDOM,
        AI_STRATEGY_BEGINNER,
        AI_STRATEGY_INTERMEDIATE,
        AI_STRATEGY_ADVANCED,
        AI_STRATEGY_CHANGED,
        AI_STRATEGY_UNCHANGED,
        TARGET_NOT_AI,
        PLAYER_INDICATOR_PAO,
        CHANGE_AI_STRATEGY_FAILED,
        GAME_CONFIG_CHANGED,
        GAME_CONFIG_UNCHANGED,
        CHANGE_GAME_CONFIG_FAILED,
        GAME_CONFIG_LABEL,
        SHOW_GAME_CONFIG,
        TILE_SUIT_CHARACTER,
        TILE_SUIT_DOT,
        TILE_SUIT_BAMBOO,
        TILE_HONOR_EAST,
        TILE_HONOR_SOUTH,
        TILE_HONOR_WEST,
        TILE_HONOR_NORTH,
        TILE_HONOR_RED,
        TILE_HONOR_GREEN,
        TILE_HONOR_WHITE,
        GAME_ACTION_DISCARD,
        GAME_ACTION_TSUMO,
        GAME_ACTION_CHI,
        GAME_ACTION_PON,
        GAME_ACTION_KAN_OPEN,
        GAME_ACTION_KAN_CLOSED,
        GAME_ACTION_KAN_ADDED,
        GAME_ACTION_RON,
        GAME_ACTION_PASS,
        GAME_ACTION_EXHAUSTIVE_DRAW,
        EXHAUSTIVE_DRAW_REASON_NORMAL,
        ROUND_RESULT_DETAILS_LABEL,
        ROUND_RESULT_BROADCAST,
        CONTINUING_WIN_RESULT_BROADCAST,
        ROUND_RESULT_PLAYER_LINE,
        EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_DRAW_DECLARATION,
        EXHAUSTIVE_DRAW_SETTLEMENT_WIND_EAST,
        EXHAUSTIVE_DRAW_SETTLEMENT_WIND_SOUTH,
        EXHAUSTIVE_DRAW_SETTLEMENT_WIND_WEST,
        EXHAUSTIVE_DRAW_SETTLEMENT_WIND_NORTH,
        GAME_ACTION_MATCH_ENDED,
        MATCH_RESULT_BROADCAST,
        MATCH_SETTLEMENT_TITLE,
        MATCH_SETTLEMENT_RANK,
        RANKING_LINE,
        GAME_ACTION_DICE_ROLLED,
        DICE_ROLL_TOTAL,
        NOT_YOUR_TURN,
        FORCED_AUTO_PLAY_ACTIVE,
        ILLEGAL_GAME_ACTION,
        WALL_EXHAUSTED,
        UNSUPPORTED_GAME_ACTION,
        TABLE_ANIMATION_BUSY,
        GAME_ABORTED,
        GAME_ABORT_REASON_SETTLEMENT,
        GAME_ABORT_REASON_PROGRESSION,
        GAME_ABORT_REASON_AUTOMATIC,
        GAME_ABORT_REASON_RULE,
        GAME_ABORT_REASON_AI_PLAYER,
        GAME_ABORT_REASON_UNKNOWN,
        HAND_TITLE,
        HAND_MELDS_TITLE,
        HAND_LEGAL_ACTIONS_TITLE,
        HAND_NO_LEGAL_ACTIONS,
        HAND_NO_RESPONSE_AVAILABLE,
        HAND_WAITING,
        ROUND_INFO_WALL_REMAINING,
        TILE_LABELS_TOGGLE_PREFIX,
        TILE_LABELS_STATE_ON,
        TILE_LABELS_STATE_OFF,
        HAND_SORT_TOGGLE_PREFIX,
        HAND_SORT_STATE_ON,
        HAND_SORT_STATE_OFF,
        AUTOMATIC_CONTROL_UPDATED,
        AUTOMATIC_CONTROL_UNCHANGED,
        AUTOMATIC_CONTROL_WAITING,
        AUTOMATIC_CONTROL_PENDING,
        AUTOMATIC_CONTROL_UNAVAILABLE,
        AUTOMATIC_CONTROL_UNSUPPORTED,
        AUTOMATIC_CONTROL_SEND_FAILED,
        AUTOMATIC_CONTROL_PREFERENCE_SYNC_FAILED,
        AUTOMATIC_CONTROL_REJECTED,
        AUTOMATIC_CONTROL_STALE,
        AUTOMATIC_CONTROL_RESYNCING,
        AUTOMATIC_CONTROL_NOT_APPLIED,
        AUTOMATIC_CONTROL_UNCONFIRMED,
        DECISION_CONFIRMATION_RESYNCING,
        DECISION_CONFIRMATION_RETRY,
        AUTOMATIC_CONTROL_STATE_ON,
        AUTOMATIC_CONTROL_STATE_OFF,
    )
}
