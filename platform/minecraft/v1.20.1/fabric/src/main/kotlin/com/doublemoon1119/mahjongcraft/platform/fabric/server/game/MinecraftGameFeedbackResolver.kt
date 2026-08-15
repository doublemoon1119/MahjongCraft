package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback

/** 將 Minecraft 對局操作結果轉換成不含呈現方式的玩家回饋。 */
internal object MinecraftGameFeedbackResolver {
    /** 將對局操作的 [error] 轉成可呈現的回饋。 */
    fun actionError(error: GameError): MinecraftPlayerFeedback = when (error) {
        is GameError.GameNotFound, is GameError.PlayerNotInGame -> MinecraftPlayerFeedback.PlayerNotInGame
        is GameError.ForcedAutoPlayActive -> MinecraftPlayerFeedback.ForcedAutoPlayActive
        is GameError.NotPlayersTurn -> MinecraftPlayerFeedback.NotYourTurn
        is GameError.IllegalAction -> MinecraftPlayerFeedback.IllegalGameAction
        is GameError.WallExhausted -> MinecraftPlayerFeedback.WallExhausted
        is GameError.UnsupportedAction -> MinecraftPlayerFeedback.UnsupportedGameAction
    }
}
