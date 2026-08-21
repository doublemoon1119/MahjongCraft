package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** Minecraft 玩家可見訊息使用的 translation key 單一來源。 */
object MinecraftMessageKeys {
    /** 所有 MahjongCraft 玩家訊息 key 的共用前綴。 */
    private const val PREFIX = MinecraftModMetadata.MOD_ID + ".message."

    /** 對局已開始，玩家無法中途加入。 */
    const val GAME_ALREADY_STARTED = PREFIX + "game_already_started"

    /** 已建立麻將遊戲。 */
    const val GAME_CREATED = PREFIX + "game_created"

    /** [GAME_CREATED] 訊息句尾可 hover 顯示座標的 `[位置]` 標籤文字。 */
    const val GAME_CREATED_LOCATION_LABEL = PREFIX + "game_created_location_label"

    /** 已加入麻將遊戲。 */
    const val GAME_JOINED = PREFIX + "game_joined"

    /** 玩家不在指定麻將遊戲中。 */
    const val PLAYER_NOT_IN_GAME = PREFIX + "player_not_in_game"

    /** 對局進行中禁止主動離開。 */
    const val GAME_LEAVE_DENIED_WHILE_PLAYING = PREFIX + "game_leave_denied_while_playing"

    /** 房主已解散麻將遊戲。 */
    const val GAME_DISSOLVED = PREFIX + "game_dissolved"

    /** 玩家已離開麻將遊戲。 */
    const val GAME_LEFT = PREFIX + "game_left"

    /** 離開麻將遊戲失敗。 */
    const val GAME_LEAVE_FAILED = PREFIX + "game_leave_failed"

    /** 玩家已參與另一場麻將遊戲。 */
    const val PLAYER_ALREADY_IN_GAME = PREFIX + "player_already_in_game"

    /** 加入麻將遊戲失敗。 */
    const val GAME_JOIN_FAILED = PREFIX + "game_join_failed"

    /** 準備狀態切換訊息的前綴，後面接切換前後的準備狀態文字。 */
    const val READY_TOGGLE_PREFIX = PREFIX + "ready_toggle_prefix"

    /** 「準備」狀態文字，用於準備狀態切換訊息中上色顯示。 */
    const val READY_STATE_READY = PREFIX + "ready_state_ready"

    /** 「尚未準備」狀態文字，用於準備狀態切換訊息中上色顯示。 */
    const val READY_STATE_NOT_READY = PREFIX + "ready_state_not_ready"

    /** 遊戲主持人不參與準備機制。 */
    const val HOST_READY_NOT_REQUIRED = PREFIX + "host_ready_not_required"

    /** 只有遊戲主持人可以開始對局。 */
    const val NOT_GAME_HOST = PREFIX + "not_game_host"

    /** 目前人數不符合規則限制的人數區間，無法開始對局。 */
    const val INVALID_PLAYER_COUNT = PREFIX + "invalid_player_count"

    /** 還有玩家尚未準備好，無法開始對局。 */
    const val NOT_ALL_PLAYERS_READY = PREFIX + "not_all_players_ready"

    /** 開始遊戲失敗。 */
    const val GAME_START_FAILED = PREFIX + "game_start_failed"

    /** 指定的麻將桌不存在，或已超出目前可互動的範圍。 */
    const val TABLE_NOT_REACHABLE = PREFIX + "table_not_reachable"

    /** 已新增 AI 玩家，帶策略顯示名稱參數（`%s`），措辭比照 [KICK_CANDIDATE_AI_LABEL] 的稱呼方式。 */
    const val AI_ADDED = PREFIX + "ai_added"

    /** 新增 AI 玩家失敗。 */
    const val ADD_AI_FAILED = PREFIX + "add_ai_failed"

    /** 遊戲人數已滿，無法再新增 AI 玩家。 */
    const val GAME_FULL = PREFIX + "game_full"

    /** 已將指定玩家移出遊戲（房主視角）。 */
    const val PLAYER_KICKED = PREFIX + "player_kicked"

    /** 已被遊戲主持人移出遊戲（被踢玩家視角）。 */
    const val KICKED_FROM_GAME = PREFIX + "kicked_from_game"

    /** 房主不能將自己移出遊戲。 */
    const val CANNOT_KICK_SELF = PREFIX + "cannot_kick_self"

    /** 將玩家移出遊戲失敗。 */
    const val KICK_FAILED = PREFIX + "kick_failed"

    /**
     * 已更換 AI 策略，依序帶序號、舊策略顯示名稱、新策略顯示名稱三個參數（各一個 `%s`），措辭比照
     * [READY_TOGGLE_PREFIX] 準備狀態切換訊息的「舊狀態 → 新狀態」呈現方式。
     */
    const val AI_STRATEGY_CHANGED = PREFIX + "ai_strategy_changed"

    /**
     * 指定的新策略與目前策略相同，操作本身仍算成功（冪等），只是換一句不會出現「同一個策略 → 同一個
     * 策略」這種容易讓人誤以為系統異常的呈現方式。依序帶序號、策略顯示名稱兩個參數（各一個 `%s`）。
     */
    const val AI_STRATEGY_UNCHANGED = PREFIX + "ai_strategy_unchanged"

    /** 目標玩家不是 AI，無法更換策略。 */
    const val TARGET_NOT_AI = PREFIX + "target_not_ai"

    /** 更換 AI 策略失敗。 */
    const val CHANGE_AI_STRATEGY_FAILED = PREFIX + "change_ai_strategy_failed"

    /**
     * `kick` 指令 Tab 補全時，AI 候選項目的 tooltip 文字，依序帶序號與策略顯示名稱兩個參數（各一個
     * `%s`）。與其他 key 不同：其他 key 都是玩家操作「結果」的一次性回饋，這個 key 是指令輸入階段
     * 候選項目的說明文字。
     */
    const val KICK_CANDIDATE_AI_LABEL = PREFIX + "kick_candidate_ai_label"

    /**
     * 內建隨機出牌 AI 策略的顯示名稱。刻意不直接叫「隨機」——容易讓人誤以為是「難度隨機」，而非
     * 「出牌動作隨機」。與 [KICK_CANDIDATE_AI_LABEL] 同樣不是操作結果回饋，是候選項目說明文字用的
     * 顯示名稱；只涵蓋內建策略，未登記顯示名稱的第三方策略 key 直接顯示原始字串。
     */
    const val AI_STRATEGY_RANDOM = PREFIX + "ai_strategy_random"

    /**
     * 已變更遊戲設定，依序帶舊設定、新設定兩個可互動文字參數（各一個 `%s`），措辭比照
     * [READY_TOGGLE_PREFIX] 準備狀態切換訊息的「舊狀態 → 新狀態」呈現方式。
     */
    const val GAME_CONFIG_CHANGED = PREFIX + "game_config_changed"

    /**
     * 提供的新設定與目前設定相同，操作本身仍算成功（冪等）。帶一個目前設定的可互動文字參數（一個
     * `%s`），比照 [AI_STRATEGY_UNCHANGED] 不呈現成容易誤解的「同一設定 → 同一設定」。
     */
    const val GAME_CONFIG_UNCHANGED = PREFIX + "game_config_unchanged"

    /** 提供的 JSON 無法解析成合法的遊戲設定。 */
    const val INVALID_GAME_CONFIG = PREFIX + "invalid_game_config"

    /** 變更遊戲設定失敗。 */
    const val CHANGE_GAME_CONFIG_FAILED = PREFIX + "change_game_config_failed"

    /** [GAME_CONFIG_CHANGED]／[GAME_CONFIG_UNCHANGED]／[SHOW_GAME_CONFIG] 中可互動文字本身顯示的簡短標籤。 */
    const val GAME_CONFIG_LABEL = PREFIX + "game_config_label"

    /**
     * 顯示所在麻將遊戲目前設定，帶一個可互動文字參數（一個 `%s`，見 [GAME_CONFIG_LABEL]）；點擊該文字
     * 會開啟設定編輯畫面。
     */
    const val SHOW_GAME_CONFIG = PREFIX + "show_game_config"

    /** 設定編輯畫面的標題。 */
    const val GAME_CONFIG_SCREEN_TITLE = PREFIX + "game_config_screen_title"

    /** 設定編輯畫面「套用」按鈕：送出後畫面保持開啟。 */
    const val GAME_CONFIG_SCREEN_APPLY = PREFIX + "game_config_screen_apply"

    /** 設定編輯畫面「確認」按鈕：送出後關閉畫面。 */
    const val GAME_CONFIG_SCREEN_CONFIRM = PREFIX + "game_config_screen_confirm"

    // ── 麻將牌顯示文字（見 MahjongTileDisplayText.kt） ──────────────────────

    /** 萬子數牌顯示文字，帶一個數值參數（一個 `%s`）。 */
    const val TILE_SUIT_CHARACTER = PREFIX + "tile_suit_character"

    /** 筒子數牌顯示文字，帶一個數值參數（一個 `%s`）。 */
    const val TILE_SUIT_DOT = PREFIX + "tile_suit_dot"

    /** 條子數牌顯示文字，帶一個數值參數（一個 `%s`）。 */
    const val TILE_SUIT_BAMBOO = PREFIX + "tile_suit_bamboo"

    /** 東風牌顯示文字。 */
    const val TILE_HONOR_EAST = PREFIX + "tile_honor_east"

    /** 南風牌顯示文字。 */
    const val TILE_HONOR_SOUTH = PREFIX + "tile_honor_south"

    /** 西風牌顯示文字。 */
    const val TILE_HONOR_WEST = PREFIX + "tile_honor_west"

    /** 北風牌顯示文字。 */
    const val TILE_HONOR_NORTH = PREFIX + "tile_honor_north"

    /** 中（紅中）顯示文字。 */
    const val TILE_HONOR_RED = PREFIX + "tile_honor_red"

    /** 發（發財）顯示文字。 */
    const val TILE_HONOR_GREEN = PREFIX + "tile_honor_green"

    /** 白（白板）顯示文字。 */
    const val TILE_HONOR_WHITE = PREFIX + "tile_honor_white"

    /** 赤五萬完整顯示文字，供 `TileDisplayNameRegistry` 內建登記使用。 */
    const val TILE_RED_FIVE_CHARACTER = PREFIX + "tile_red_five_character"

    /** 赤五筒完整顯示文字，供 `TileDisplayNameRegistry` 內建登記使用。 */
    const val TILE_RED_FIVE_DOT = PREFIX + "tile_red_five_dot"

    /** 赤五條完整顯示文字，供 `TileDisplayNameRegistry` 內建登記使用。 */
    const val TILE_RED_FIVE_BAMBOO = PREFIX + "tile_red_five_bamboo"

    /** 日本麻將規則模組顯示名稱，供 `RuleModuleDisplayNameRegistry` 內建登記使用。 */
    const val RULE_MODULE_RIICHI = PREFIX + "rule_module_riichi"

    /** 台灣麻將規則模組顯示名稱，供 `RuleModuleDisplayNameRegistry` 內建登記使用。 */
    const val RULE_MODULE_TAIWAN = PREFIX + "rule_module_taiwan"

    // ── 對局動作顯示文字（見 GameActionDisplayText.kt） ──────────────────────

    /** 打出，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_DISCARD = PREFIX + "game_action_discard"

    /** 立直，不帶參數（實際捨牌另外顯示，見 [GAME_ACTION_DISCARD]）。 */
    const val GAME_ACTION_RIICHI = PREFIX + "game_action_riichi"

    /** 自摸，不帶參數。 */
    const val GAME_ACTION_TSUMO = PREFIX + "game_action_tsumo"

    /** 吃，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_CHI = PREFIX + "game_action_chi"

    /** 碰，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_PON = PREFIX + "game_action_pon"

    /** 明槓（大明槓），帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_KAN_OPEN = PREFIX + "game_action_kan_open"

    /** 暗槓，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_KAN_CLOSED = PREFIX + "game_action_kan_closed"

    /** 加槓（小明槓），帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_KAN_ADDED = PREFIX + "game_action_kan_added"

    /** 榮和，帶一個牌面顯示文字參數（一個 `%s`）。 */
    const val GAME_ACTION_RON = PREFIX + "game_action_ron"

    /** 過（不執行任何鳴牌或胡牌動作），不帶參數。 */
    const val GAME_ACTION_PASS = PREFIX + "game_action_pass"

    /** 九種九牌，不帶參數。 */
    const val GAME_ACTION_KYUUSHU_KYUUHAI = PREFIX + "game_action_kyuushu_kyuuhai"

    /** 其他規則專屬流局原因的通用 fallback 顯示文字，不帶參數。 */
    const val GAME_ACTION_EXHAUSTIVE_DRAW = PREFIX + "game_action_exhaustive_draw"

    /**
     * 回合結束廣播的標題，帶一個動作顯示文字參數（一個 `%s`，見上方 `GAME_ACTION_*`）——目前是
     * client 端聊天訊息占位呈現，之後若換成 GUI/HUD 只需要換掉呼叫端，這個 key 不受影響。
     */
    const val ROUND_RESULT_BROADCAST = PREFIX + "round_result_broadcast"

    /**
     * 回合結束廣播內每一位玩家的一行，帶玩家名稱、回合前名次、回合後名次、名次變化符號
     * （`↑`/`↓`/`→`）、回合前分數、回合後分數共六個參數（六個 `%s`）——固定列出所有玩家，不只是
     * 分數有變化的人（自己分數沒變，也可能因為別人分數變了而被擠掉名次），供排行榜動畫使用的名次
     * 升降跟分數增減兩種資料都在同一行裡。
     */
    const val ROUND_RESULT_PLAYER_LINE = PREFIX + "round_result_player_line"

    /** [GameAction.MatchEnded] 的顯示文字，不帶參數。 */
    const val GAME_ACTION_MATCH_ENDED = PREFIX + "game_action_match_ended"

    /**
     * 對局結束廣播的標題，不帶參數——目前是 client 端聊天訊息占位呈現，之後若換成 GUI/HUD 只需要
     * 換掉呼叫端，這個 key 不受影響。
     */
    const val MATCH_RESULT_BROADCAST = PREFIX + "match_result_broadcast"

    /** 對局結束排名清單內每一行，帶名次、玩家名稱、最終分數三個參數（三個 `%s`）。 */
    const val RANKING_LINE = PREFIX + "ranking_line"

    /**
     * [GameAction.DiceRolled] 的顯示文字，不帶參數——
     * 實際點數由 [DICE_ROLLED_BROADCAST] 承載，這裡只是滿足 `GameActionDisplayText` 窮舉 `when`。
     */
    const val GAME_ACTION_DICE_ROLLED = PREFIX + "game_action_dice_rolled"

    /**
     * 擲骰結果廣播，帶一個已格式化的骰子點數清單參數（一個 `%s`，例如「5、5」）——目前是 client 端
     * 聊天訊息占位呈現，之後若換成 HUD 只需要換掉呼叫端，這個 key 不受影響。
     */
    const val DICE_ROLLED_BROADCAST = PREFIX + "dice_rolled_broadcast"

    // ── 對局階段指令回饋 ──────────────────────────────────────────────────

    /** 已成功執行對局操作，帶一個動作顯示文字參數（一個 `%s`，見上方 `GAME_ACTION_*`）。 */
    const val GAME_ACTION_PERFORMED = PREFIX + "game_action_performed"

    /** 還沒輪到該玩家的回合。 */
    const val NOT_YOUR_TURN = PREFIX + "not_your_turn"

    /** 玩家已逾時，後續操作交由伺服器自動處理。 */
    const val FORCED_AUTO_PLAY_ACTIVE = PREFIX + "forced_auto_play_active"

    /** 該動作在目前桌況下不合法。 */
    const val ILLEGAL_GAME_ACTION = PREFIX + "illegal_game_action"

    /** 牌山已摸盡。 */
    const val WALL_EXHAUSTED = PREFIX + "wall_exhausted"

    /** 目前規則不支援這個動作。 */
    const val UNSUPPORTED_GAME_ACTION = PREFIX + "unsupported_game_action"

    /** 桌面正在播放呈現動畫（例如擲骰），暫時無法送出操作。 */
    const val TABLE_ANIMATION_BUSY = PREFIX + "table_animation_busy"

    // ── 手牌查詢指令（`/mahjongcraft game hand`） ──────────────────────────

    /** 手牌列表標題。 */
    const val HAND_TITLE = PREFIX + "hand_title"

    /** 副露列表標題。 */
    const val HAND_MELDS_TITLE = PREFIX + "hand_melds_title"

    /** 目前可執行的特殊動作列表標題。 */
    const val HAND_LEGAL_ACTIONS_TITLE = PREFIX + "hand_legal_actions_title"

    /** 輪到自己回合、已摸牌，但沒有特殊動作可用時的提示，指引玩家改用 `discard`。 */
    const val HAND_NO_LEGAL_ACTIONS = PREFIX + "hand_no_legal_actions"

    /** 有資格回應捨牌／搶槓、但實際上沒有任何合法回應（連過牌都不需要）時的提示。 */
    const val HAND_NO_RESPONSE_AVAILABLE = PREFIX + "hand_no_response_available"

    /** 還沒輪到自己、也沒有資格回應時的提示，避免誤以為隨時都能 `discard`。 */
    const val HAND_WAITING = PREFIX + "hand_waiting"

    /**
     * 輪到自己回合、伺服器已代為摸牌的主動通知，帶一個摸到的牌面顯示文字參數（一個 `%s`）——沒有這則
     * 訊息玩家不會知道輪到自己了。
     */
    const val YOUR_TURN = PREFIX + "your_turn"

    // ── 牌面輔助標籤指令（`/mahjongcraft_client label toggle`，純 client-only） ─────

    // ── 桌面中央局況顯示（見 `MahjongRoundInfoEntityRenderer`） ────────────────

    /** 局況顯示標題行，依序帶場風顯示文字（一個 `%s`，見上方 `TILE_HONOR_*`）與場風內局數（一個 `%d`）。 */
    const val ROUND_INFO_TITLE = PREFIX + "round_info_title"

    /**
     * 局況顯示標題行的本場數，帶一個參數（一個 `%d`）——對應 `TableState.comboCount`（日麻：本場數；
     * 台麻：連幾），key 命名跟這個 domain 欄位對齊，不是照抄「repeat counter」這種字面翻譯。
     */
    const val ROUND_INFO_COMBO_COUNT = PREFIX + "round_info_combo_count"

    /** 局況顯示牌山剩餘張數，帶一個張數參數（一個 `%d`）。 */
    const val ROUND_INFO_WALL_REMAINING = PREFIX + "round_info_wall_remaining"

    /** 牌面角落輔助標籤（給非中文圈玩家看的數字/字母）切換訊息的前綴，後面接切換前後的狀態文字。 */
    const val TILE_LABELS_TOGGLE_PREFIX = PREFIX + "tile_labels_toggle_prefix"

    /** 「開啟」狀態文字，用於牌面輔助標籤切換訊息中上色顯示。 */
    const val TILE_LABELS_STATE_ON = PREFIX + "tile_labels_state_on"

    /** 「關閉」狀態文字，用於牌面輔助標籤切換訊息中上色顯示。 */
    const val TILE_LABELS_STATE_OFF = PREFIX + "tile_labels_state_off"

    /** Minecraft 語系資源必須提供的全部玩家回饋 key。 */
    val ALL: Set<String> = setOf(
        GAME_ALREADY_STARTED,
        GAME_CREATED,
        GAME_CREATED_LOCATION_LABEL,
        GAME_JOINED,
        PLAYER_NOT_IN_GAME,
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
        TABLE_NOT_REACHABLE,
        AI_ADDED,
        ADD_AI_FAILED,
        GAME_FULL,
        PLAYER_KICKED,
        KICKED_FROM_GAME,
        CANNOT_KICK_SELF,
        KICK_FAILED,
        KICK_CANDIDATE_AI_LABEL,
        AI_STRATEGY_RANDOM,
        AI_STRATEGY_CHANGED,
        AI_STRATEGY_UNCHANGED,
        TARGET_NOT_AI,
        CHANGE_AI_STRATEGY_FAILED,
        GAME_CONFIG_CHANGED,
        GAME_CONFIG_UNCHANGED,
        INVALID_GAME_CONFIG,
        CHANGE_GAME_CONFIG_FAILED,
        GAME_CONFIG_LABEL,
        SHOW_GAME_CONFIG,
        GAME_CONFIG_SCREEN_TITLE,
        GAME_CONFIG_SCREEN_APPLY,
        GAME_CONFIG_SCREEN_CONFIRM,
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
        TILE_RED_FIVE_CHARACTER,
        TILE_RED_FIVE_DOT,
        TILE_RED_FIVE_BAMBOO,
        RULE_MODULE_RIICHI,
        RULE_MODULE_TAIWAN,
        GAME_ACTION_DISCARD,
        GAME_ACTION_RIICHI,
        GAME_ACTION_TSUMO,
        GAME_ACTION_CHI,
        GAME_ACTION_PON,
        GAME_ACTION_KAN_OPEN,
        GAME_ACTION_KAN_CLOSED,
        GAME_ACTION_KAN_ADDED,
        GAME_ACTION_RON,
        GAME_ACTION_PASS,
        GAME_ACTION_KYUUSHU_KYUUHAI,
        GAME_ACTION_EXHAUSTIVE_DRAW,
        ROUND_RESULT_BROADCAST,
        ROUND_RESULT_PLAYER_LINE,
        GAME_ACTION_MATCH_ENDED,
        MATCH_RESULT_BROADCAST,
        RANKING_LINE,
        GAME_ACTION_DICE_ROLLED,
        DICE_ROLLED_BROADCAST,
        GAME_ACTION_PERFORMED,
        NOT_YOUR_TURN,
        FORCED_AUTO_PLAY_ACTIVE,
        ILLEGAL_GAME_ACTION,
        WALL_EXHAUSTED,
        UNSUPPORTED_GAME_ACTION,
        TABLE_ANIMATION_BUSY,
        HAND_TITLE,
        HAND_MELDS_TITLE,
        HAND_LEGAL_ACTIONS_TITLE,
        HAND_NO_LEGAL_ACTIONS,
        HAND_NO_RESPONSE_AVAILABLE,
        HAND_WAITING,
        YOUR_TURN,
        ROUND_INFO_TITLE,
        ROUND_INFO_COMBO_COUNT,
        ROUND_INFO_WALL_REMAINING,
        TILE_LABELS_TOGGLE_PREFIX,
        TILE_LABELS_STATE_ON,
        TILE_LABELS_STATE_OFF,
    )
}
