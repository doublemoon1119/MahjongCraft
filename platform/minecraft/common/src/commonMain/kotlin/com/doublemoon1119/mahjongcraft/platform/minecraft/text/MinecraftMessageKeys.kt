package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** Minecraft 玩家可見訊息使用的 translation key 單一來源。 */
object MinecraftMessageKeys {
    /** 所有 MahjongCraft 玩家訊息 key 的共用前綴。 */
    private const val PREFIX = MinecraftModMetadata.MOD_ID + ".message."

    /** 對局已開始，玩家無法中途加入。 */
    const val GAME_ALREADY_STARTED = PREFIX + "game_already_started"

    /** 已建立麻將遊戲。 */
    const val GAME_CREATED = PREFIX + "game_created"

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

    /** 已新增 AI 玩家，帶策略顯示名稱參數（`%s`），措辭比照 [KICK_CANDIDATE_AI_LABEL] 的機器人稱呼。 */
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

    /** Minecraft 語系資源必須提供的全部玩家回饋 key。 */
    val ALL: Set<String> = setOf(
        GAME_ALREADY_STARTED,
        GAME_CREATED,
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
    )
}
