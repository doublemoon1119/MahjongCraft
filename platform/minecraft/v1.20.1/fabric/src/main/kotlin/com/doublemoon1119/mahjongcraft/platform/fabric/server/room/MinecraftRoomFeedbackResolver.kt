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
}
