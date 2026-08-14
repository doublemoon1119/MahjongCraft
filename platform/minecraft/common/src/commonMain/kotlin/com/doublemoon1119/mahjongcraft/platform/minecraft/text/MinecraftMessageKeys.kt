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
    )
}
