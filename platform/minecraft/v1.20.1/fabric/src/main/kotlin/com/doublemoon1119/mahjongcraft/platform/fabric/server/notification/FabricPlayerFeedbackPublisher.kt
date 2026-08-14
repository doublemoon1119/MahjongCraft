package com.doublemoon1119.mahjongcraft.platform.fabric.server.notification

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import net.minecraft.text.Text
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 使用 Minecraft 1.20.1 Fabric API 呈現一次性玩家回饋。
 *
 * 目前所有回饋均轉成本地化 chat 訊息。每個 exhaustive `when` 分支可執行零到多個版本特有效果，
 * 不要求回饋與 translation key 一對一。若其他版本能使用新的 bar、title、音效或多種效果並行，
 * 可以在自己的實作中重新解釋同一個 [MinecraftPlayerFeedback]，不必讓共用模型列出所有版本 UI API
 * 的聯集。
 */
@Single(binds = [MinecraftPlayerFeedbackPublisher::class])
class FabricPlayerFeedbackPublisher(
    private val serverHolder: FabricServerHolder,
) : MinecraftPlayerFeedbackPublisher {
    /** 切換至 server thread，並在玩家仍在線時傳送目前版本選定的回饋。 */
    override fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback) {
        val server = serverHolder.current() ?: return
        server.execute {
            val player = serverHolder.findPlayer(playerId) ?: return@execute
            when (feedback) {
                MinecraftPlayerFeedback.GameAlreadyStarted ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_ALREADY_STARTED), true)
                MinecraftPlayerFeedback.GameCreated ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_CREATED))
                MinecraftPlayerFeedback.GameJoined ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_JOINED))
                MinecraftPlayerFeedback.PlayerNotInGame ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.PLAYER_NOT_IN_GAME), true)
                MinecraftPlayerFeedback.GameLeaveDeniedWhilePlaying ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_LEAVE_DENIED_WHILE_PLAYING), true)
                MinecraftPlayerFeedback.GameDissolved ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_DISSOLVED))
                MinecraftPlayerFeedback.GameLeft ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_LEFT))
                MinecraftPlayerFeedback.GameLeaveFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_LEAVE_FAILED), true)
                MinecraftPlayerFeedback.PlayerAlreadyInGame ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.PLAYER_ALREADY_IN_GAME), true)
                MinecraftPlayerFeedback.GameJoinFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_JOIN_FAILED), true)
                MinecraftPlayerFeedback.ReadyToggled ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.READY_TOGGLED))
                MinecraftPlayerFeedback.NotGameHost ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.NOT_GAME_HOST), true)
                MinecraftPlayerFeedback.NotAllPlayersReady ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.NOT_ALL_PLAYERS_READY), true)
                MinecraftPlayerFeedback.GameStartFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_START_FAILED), true)
                MinecraftPlayerFeedback.TableNotReachable ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.TABLE_NOT_REACHABLE), true)
            }
        }
    }
}
