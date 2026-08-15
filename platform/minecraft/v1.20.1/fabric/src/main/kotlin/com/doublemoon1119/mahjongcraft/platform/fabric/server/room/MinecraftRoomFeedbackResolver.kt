package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback

/** 將 Minecraft Room 互動結果轉換成不含呈現方式的玩家回饋。 */
internal object MinecraftRoomFeedbackResolver {
    /** 將加入 Room 的 [error] 轉成可呈現的回饋。 */
    fun joinError(error: RoomError): MinecraftPlayerFeedback = when (error) {
        is RoomError.PlayerAlreadyInAnotherGame -> MinecraftPlayerFeedback.PlayerAlreadyInGame
        else -> MinecraftPlayerFeedback.GameJoinFailed
    }

    /** 依離開者是否為 [wasHost] 回傳解散或一般離開回饋。 */
    fun successfulLeave(wasHost: Boolean): MinecraftPlayerFeedback = if (wasHost) {
        MinecraftPlayerFeedback.GameDissolved
    } else {
        MinecraftPlayerFeedback.GameLeft
    }

    /** 將切換準備狀態的 [error] 轉成可呈現的回饋。 */
    fun readyError(error: RoomError): MinecraftPlayerFeedback = when (error) {
        is RoomError.RoomNotFound, is RoomError.PlayerNotInRoom -> MinecraftPlayerFeedback.PlayerNotInGame
        else -> MinecraftPlayerFeedback.GameJoinFailed
    }

    /** 將開始遊戲的 [error] 轉成可呈現的回饋。 */
    fun startError(error: RoomError): MinecraftPlayerFeedback = when (error) {
        is RoomError.NotHost -> MinecraftPlayerFeedback.NotGameHost
        is RoomError.RoomPlayerCountInvalid -> MinecraftPlayerFeedback.InvalidPlayerCount
        is RoomError.RoomNotReadyToStart -> MinecraftPlayerFeedback.NotAllPlayersReady
        else -> MinecraftPlayerFeedback.GameStartFailed
    }

    /** 將新增 AI 玩家的 [error] 轉成可呈現的回饋。 */
    fun addAiError(error: RoomError): MinecraftPlayerFeedback = when (error) {
        is RoomError.NotHost -> MinecraftPlayerFeedback.NotGameHost
        is RoomError.RoomIsFull -> MinecraftPlayerFeedback.GameFull
        else -> MinecraftPlayerFeedback.AddAiFailed
    }

    /** 將剔除玩家的 [error] 轉成可呈現的回饋。 */
    fun kickError(error: RoomError): MinecraftPlayerFeedback = when (error) {
        is RoomError.NotHost -> MinecraftPlayerFeedback.NotGameHost
        is RoomError.PlayerNotInRoom -> MinecraftPlayerFeedback.PlayerNotInGame
        is RoomError.HostCannotKickSelf -> MinecraftPlayerFeedback.CannotKickSelf
        else -> MinecraftPlayerFeedback.KickFailed
    }

    /** 將更換 AI 策略的 [error] 轉成可呈現的回饋。 */
    fun changeAiStrategyError(error: RoomError): MinecraftPlayerFeedback = when (error) {
        is RoomError.NotHost -> MinecraftPlayerFeedback.NotGameHost
        is RoomError.PlayerNotInRoom -> MinecraftPlayerFeedback.PlayerNotInGame
        is RoomError.NotAiPlayer -> MinecraftPlayerFeedback.TargetNotAi
        else -> MinecraftPlayerFeedback.ChangeAiStrategyFailed
    }
}
