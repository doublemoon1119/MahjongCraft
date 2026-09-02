package com.doublemoon1119.mahjongcraft.platform.fabric.server.notification

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.client.room.FabricOpenRoomConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.resolveDisplayText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.bracketedInteractiveLabel
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.GameTurnStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import kotlinx.coroutines.launch
import net.minecraft.text.ClickEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
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
    private val gameActionDisplayNames: GameActionDisplayNameRegistry,
    private val exhaustiveDrawReasonDisplayNames: ExhaustiveDrawReasonDisplayNameRegistry,
    private val tileDisplayNames: TileDisplayNameRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val tileEmojiRegistry: TileEmojiRegistry,
    private val gameConfigFormatter: FabricGameConfigTextFormatter,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : MinecraftPlayerFeedbackPublisher {
    /** 記錄包含大量 hover 資訊的正式回饋原始資料。 */
    private val logger = LoggerFactory.getLogger(FabricPlayerFeedbackPublisher::class.java)

    /** 切換至 server thread，並在玩家仍在線時傳送目前版本選定的回饋。 */
    override fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) {
            val player = serverHolder.findPlayer(playerId) ?: return@launch
            when (feedback) {
                MinecraftPlayerFeedback.GameAlreadyStarted ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_ALREADY_STARTED), true)
                is MinecraftPlayerFeedback.GameCreated -> {
                    feedback.location?.let { location ->
                        logger.debug(
                            "Game-created hover playerId={} dimension={} position=({}, {}, {})",
                            playerId,
                            location.dimensionId,
                            location.x,
                            location.y,
                            location.z,
                        )
                    }
                    player.sendMessage(gameCreatedMessage(feedback.location))
                }
                MinecraftPlayerFeedback.GameJoined ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.GAME_JOINED))
                MinecraftPlayerFeedback.PlayerNotInGame ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.PLAYER_NOT_IN_GAME), true)
                MinecraftPlayerFeedback.PlayerNotInAnyGame ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.PLAYER_NOT_IN_ANY_GAME), true)
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
                is MinecraftPlayerFeedback.AiStrategyChanged ->
                    player.sendMessage(aiStrategyChangedMessage(feedback))
                MinecraftPlayerFeedback.TargetNotAi ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.TARGET_NOT_AI), true)
                MinecraftPlayerFeedback.ChangeAiStrategyFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.CHANGE_AI_STRATEGY_FAILED), true)
                is MinecraftPlayerFeedback.GameConfigChanged -> {
                    logger.debug(
                        "Game-config-changed hover playerId={} oldConfig={} newConfig={}",
                        playerId,
                        feedback.oldConfigJson,
                        feedback.newConfigJson,
                    )
                    player.sendMessage(gameConfigChangedMessage(feedback))
                }
                is MinecraftPlayerFeedback.GameConfigUnchanged -> {
                    logger.debug("Game-config-unchanged hover playerId={} config={}", playerId, feedback.configJson)
                    player.sendMessage(gameConfigUnchangedMessage(feedback))
                }
                MinecraftPlayerFeedback.InvalidGameConfig ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.INVALID_GAME_CONFIG), true)
                MinecraftPlayerFeedback.ChangeGameConfigFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.CHANGE_GAME_CONFIG_FAILED), true)
                is MinecraftPlayerFeedback.ShowGameConfig -> {
                    logger.debug("Game-config-show hover playerId={} config={}", playerId, feedback.configJson)
                    player.sendMessage(showGameConfigMessage(feedback))
                }
                MinecraftPlayerFeedback.NotYourTurn ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.NOT_YOUR_TURN), true)
                MinecraftPlayerFeedback.ForcedAutoPlayActive ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.FORCED_AUTO_PLAY_ACTIVE), true)
                MinecraftPlayerFeedback.IllegalGameAction ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.ILLEGAL_GAME_ACTION), true)
                MinecraftPlayerFeedback.WallExhausted ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.WALL_EXHAUSTED), true)
                MinecraftPlayerFeedback.UnsupportedGameAction ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.UNSUPPORTED_GAME_ACTION), true)
                MinecraftPlayerFeedback.TableAnimationBusy ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.TABLE_ANIMATION_BUSY), true)
                is MinecraftPlayerFeedback.ShowHand ->
                    player.sendMessage(showHandMessage(feedback))
                is MinecraftPlayerFeedback.YourTurn ->
                    player.sendMessage(
                        Text.translatable(
                            MinecraftMessageKeys.YOUR_TURN,
                            feedback.drawnTile.toDisplayText(tileDisplayNames, tileAssetRegistry, tileEmojiRegistry),
                        ),
                        true,
                    )
            }
        }
    }

    /**
     * 建立「已建立麻將遊戲」訊息；[location] 存在時句尾附上可 hover 顯示座標的 `[位置]` 標籤，主世界
     * 以外的維度會額外多顯示一行維度 ID（多數桌子都在主世界，省略維度可以維持提示簡潔）。
     */
    private fun gameCreatedMessage(location: TableLocation?): MutableText {
        val message = Text.translatable(MinecraftMessageKeys.GAME_CREATED)
        if (location == null) return message

        var hoverText: MutableText = Text.literal("${location.x}, ${location.y}, ${location.z}")
        if (location.dimensionId != OVERWORLD_DIMENSION_ID) {
            hoverText = hoverText.append("\n").append(Text.literal(location.dimensionId))
        }
        return message.append(Text.literal(" "))
            .append(bracketedInteractiveLabel(Text.translatable(MinecraftMessageKeys.GAME_CREATED_LOCATION_LABEL), hoverText))
    }

    /** 建立「已新增 AI 玩家」訊息，策略名稱與 `kick` 補全 tooltip 使用同一套顯示名稱解析。 */
    private fun aiAddedMessage(strategyKey: String): MutableText = Text.translatable(
        MinecraftMessageKeys.AI_ADDED,
        aiStrategyDisplayNames.resolveDisplayText(strategyKey),
    )

    /**
     * 建立「已更換 AI 策略」訊息，格式為「舊策略 → 新策略」，比照使用者對「舊 → 新」類訊息的配色慣例：
     * 舊值紅色、新值綠色。新舊策略相同時改用 [MinecraftMessageKeys.AI_STRATEGY_UNCHANGED]——這個操作
     * 本身仍是成功的冪等操作，只是換一句不會出現「同一個策略 → 同一個策略」這種容易讓人誤以為系統
     * 異常的說法，此時維持無色。
     */
    private fun aiStrategyChangedMessage(feedback: MinecraftPlayerFeedback.AiStrategyChanged): MutableText {
        if (feedback.oldStrategyKey == feedback.newStrategyKey) {
            return Text.translatable(
                MinecraftMessageKeys.AI_STRATEGY_UNCHANGED,
                feedback.aiSequence,
                aiStrategyDisplayNames.resolveDisplayText(feedback.newStrategyKey),
            )
        }
        return Text.translatable(
            MinecraftMessageKeys.AI_STRATEGY_CHANGED,
            feedback.aiSequence,
            aiStrategyDisplayNames.resolveDisplayText(feedback.oldStrategyKey).copy().formatted(Formatting.RED),
            aiStrategyDisplayNames.resolveDisplayText(feedback.newStrategyKey).copy().formatted(Formatting.GREEN),
        )
    }

    /** 建立「已變更遊戲設定」訊息；實際的「舊值 → 新值」差異位於可互動標籤的 hover。 */
    private fun gameConfigChangedMessage(feedback: MinecraftPlayerFeedback.GameConfigChanged): MutableText = Text.translatable(
        MinecraftMessageKeys.GAME_CONFIG_CHANGED,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftMessageKeys.GAME_CONFIG_LABEL),
            gameConfigFormatter.changes(feedback.oldConfigJson, feedback.newConfigJson),
            ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_ROOM_CONFIG_SCREEN_COMMAND),
            Formatting.AQUA,
        ),
    )

    /** 建立「新設定與目前設定相同」訊息，比照 [aiStrategyChangedMessage] 對新舊相同時的特殊處理，維持無色。 */
    private fun gameConfigUnchangedMessage(feedback: MinecraftPlayerFeedback.GameConfigUnchanged): MutableText = Text.translatable(
        MinecraftMessageKeys.GAME_CONFIG_UNCHANGED,
        gameConfigText(feedback.configJson),
    )

    /**
     * 建立「顯示目前遊戲設定」訊息；可互動文字點擊後透過 [ClickEvent.Action.RUN_COMMAND] 觸發
     * client-only 指令開啟設定編輯畫面（見 [FabricOpenRoomConfigScreenCommand]），
     * 不是複製 JSON。
     */
    private fun showGameConfigMessage(feedback: MinecraftPlayerFeedback.ShowGameConfig): MutableText = Text.translatable(
        MinecraftMessageKeys.SHOW_GAME_CONFIG,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftMessageKeys.GAME_CONFIG_LABEL),
            gameConfigHoverText(feedback.configJson),
            ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_ROOM_CONFIG_SCREEN_COMMAND),
        ),
    )

    /** 將設定轉成可開啟 RoomScreen 的互動標籤，hover 顯示本地化完整內容。 */
    private fun gameConfigText(configJson: String, color: Formatting = Formatting.AQUA): MutableText = bracketedInteractiveLabel(
        Text.translatable(MinecraftMessageKeys.GAME_CONFIG_LABEL),
        gameConfigHoverText(configJson),
        ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_ROOM_CONFIG_SCREEN_COMMAND),
        color,
    )

    /** 將設定轉成本地化 hover 文字，與 RoomScreen 共用宣告式設定 schema。 */
    private fun gameConfigHoverText(configJson: String): MutableText = gameConfigFormatter.full(configJson)

    /**
     * 建立 `/mahjongcraft game hand` 的手牌畫面：手牌列表、副露（有的話）、目前可執行的特殊動作
     * （沒有的話顯示 [MinecraftMessageKeys.HAND_NO_LEGAL_ACTIONS] 提示玩家改用 `discard`）。
     */
    private fun showHandMessage(feedback: MinecraftPlayerFeedback.ShowHand): MutableText {
        val message = Text.translatable(MinecraftMessageKeys.HAND_TITLE)
        feedback.standingTiles.forEach { tile ->
            message.append(Text.literal(" ")).append(tile.toDisplayText(tileDisplayNames, tileAssetRegistry, tileEmojiRegistry))
        }

        if (feedback.melds.isNotEmpty()) {
            message.append(Text.literal("\n")).append(Text.translatable(MinecraftMessageKeys.HAND_MELDS_TITLE))
            feedback.melds.forEach { meld ->
                message.append(Text.literal(" ["))
                meld.tiles.forEachIndexed { index, tile ->
                    if (index > 0) message.append(Text.literal(" "))
                    message.append(tile.tile.toDisplayText(tileDisplayNames, tileAssetRegistry, tileEmojiRegistry))
                }
                message.append(Text.literal("]"))
            }
        }

        message.append(Text.literal("\n")).append(Text.translatable(MinecraftMessageKeys.HAND_LEGAL_ACTIONS_TITLE))
        if (feedback.legalActions.isEmpty()) {
            val emptyKey = when (feedback.turnStatus) {
                GameTurnStatus.OWN_TURN -> MinecraftMessageKeys.HAND_NO_LEGAL_ACTIONS
                GameTurnStatus.AWAITING_RESPONSE -> MinecraftMessageKeys.HAND_NO_RESPONSE_AVAILABLE
                GameTurnStatus.WAITING -> MinecraftMessageKeys.HAND_WAITING
            }
            message.append(Text.literal(" ")).append(Text.translatable(emptyKey))
        } else {
            feedback.legalActions.forEachIndexed { index, (action, referenceTile) ->
                message.append(Text.literal(" ${index + 1}:"))
                    .append(
                        action.toDisplayText(
                            referenceTile,
                            gameActionDisplayNames,
                            tileDisplayNames,
                            tileAssetRegistry,
                            tileEmojiRegistry,
                            exhaustiveDrawReasonDisplayNames,
                        ),
                    )
            }
        }
        return message
    }

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

        /**
         * 開啟房間規則設定編輯畫面的 client-only 指令；純粹當點擊觸發器用，不是給玩家手動輸入。根節點用
         * 麻將牌字元 `🀇` 而非英文字串的理由見 [FabricOpenRoomConfigScreenCommand] 的類別 KDoc。
         */
        const val OPEN_ROOM_CONFIG_SCREEN_COMMAND: String = "/🀇 open_room_config_screen"
    }
}
