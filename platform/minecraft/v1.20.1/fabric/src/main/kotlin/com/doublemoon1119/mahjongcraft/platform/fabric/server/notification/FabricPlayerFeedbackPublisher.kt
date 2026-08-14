package com.doublemoon1119.mahjongcraft.platform.fabric.server.notification

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.resolveDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
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
    private val aiStrategyDisplayNames: AiStrategyDisplayNameRegistry,
) : MinecraftPlayerFeedbackPublisher {
    /** 切換至 server thread，並在玩家仍在線時傳送目前版本選定的回饋。 */
    override fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback) {
        val server = serverHolder.current() ?: return
        server.execute {
            val player = serverHolder.findPlayer(playerId) ?: return@execute
            when (feedback) {
                MinecraftPlayerFeedback.GameAlreadyStarted ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_ALREADY_STARTED), true)
                is MinecraftPlayerFeedback.GameCreated ->
                    player.sendMessage(gameCreatedMessage(feedback.location))
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
                is MinecraftPlayerFeedback.ReadyToggled ->
                    player.sendMessage(readyToggledMessage(feedback.isReady))
                MinecraftPlayerFeedback.HostReadyNotRequired ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.HOST_READY_NOT_REQUIRED), true)
                MinecraftPlayerFeedback.NotGameHost ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.NOT_GAME_HOST), true)
                MinecraftPlayerFeedback.InvalidPlayerCount ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.INVALID_PLAYER_COUNT), true)
                MinecraftPlayerFeedback.NotAllPlayersReady ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.NOT_ALL_PLAYERS_READY), true)
                MinecraftPlayerFeedback.GameStartFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_START_FAILED), true)
                MinecraftPlayerFeedback.TableNotReachable ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.TABLE_NOT_REACHABLE), true)
                is MinecraftPlayerFeedback.AiAdded ->
                    player.sendMessage(aiAddedMessage(feedback.strategyKey))
                MinecraftPlayerFeedback.AddAiFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.ADD_AI_FAILED), true)
                MinecraftPlayerFeedback.GameFull ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_FULL), true)
                MinecraftPlayerFeedback.PlayerKicked ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.PLAYER_KICKED))
                MinecraftPlayerFeedback.KickedFromGame ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.KICKED_FROM_GAME), true)
                MinecraftPlayerFeedback.CannotKickSelf ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.CANNOT_KICK_SELF), true)
                MinecraftPlayerFeedback.KickFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.KICK_FAILED), true)
            }
        }
    }

    /**
     * 建立「已建立麻將遊戲」訊息；[location] 存在時整句可以 hover 顯示座標，主世界以外的維度會
     * 額外多顯示一行維度 ID（多數桌子都在主世界，省略維度可以維持提示簡潔）。
     */
    private fun gameCreatedMessage(location: TableLocation?): MutableText {
        val message = Text.translatable(MinecraftMessageKeys.GAME_CREATED)
        if (location == null) return message

        var hoverText: MutableText = Text.literal("${location.x}, ${location.y}, ${location.z}")
        if (location.dimensionId != OVERWORLD_DIMENSION_ID) {
            hoverText = hoverText.append("\n").append(Text.literal(location.dimensionId))
        }
        return message.styled { it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)) }
    }

    /** 建立「已新增麻將機器人」訊息，策略名稱與 `kick` 補全 tooltip 使用同一套顯示名稱解析。 */
    private fun aiAddedMessage(strategyKey: String): MutableText = Text.translatable(
        MinecraftMessageKeys.AI_ADDED,
        aiStrategyDisplayNames.resolveDisplayText(strategyKey),
    )

    /** 組合準備狀態切換訊息：前綴 + 切換前狀態 → 切換後狀態，「準備」亮綠、「尚未準備」亮紅。 */
    private fun readyToggledMessage(isReady: Boolean): MutableText {
        val fromKey = if (isReady) MinecraftMessageKeys.READY_STATE_NOT_READY else MinecraftMessageKeys.READY_STATE_READY
        val fromColor = if (isReady) Formatting.RED else Formatting.GREEN
        val toKey = if (isReady) MinecraftMessageKeys.READY_STATE_READY else MinecraftMessageKeys.READY_STATE_NOT_READY
        val toColor = if (isReady) Formatting.GREEN else Formatting.RED

        return Text.translatable(MinecraftMessageKeys.READY_TOGGLE_PREFIX)
            .append(Text.translatable(fromKey).formatted(fromColor))
            .append(Text.literal(" → "))
            .append(Text.translatable(toKey).formatted(toColor))
    }

    private companion object {
        /** 主世界的 dimension registry identifier；hover 顯示位置時，這個維度不額外標示。 */
        const val OVERWORLD_DIMENSION_ID: String = "minecraft:overworld"
    }
}
