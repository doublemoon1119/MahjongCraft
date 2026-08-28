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
        is GameError.UnsupportedAction,
        is GameError.RoundPreparationUnavailable,
        is GameError.InvalidRoundPreparationSubmission,
        -> MinecraftPlayerFeedback.UnsupportedGameAction
        // 只會由伺服器內部的 ReturnToRoomUseCase 觸發，不會出現在任何玩家指令的回饋路徑上；
        // 這裡沒有專屬訊息，退回同一種通用「動作不支援」回饋只是為了滿足窮舉。
        is GameError.MatchNotOver -> MinecraftPlayerFeedback.UnsupportedGameAction
    }
}
