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
    )
}
