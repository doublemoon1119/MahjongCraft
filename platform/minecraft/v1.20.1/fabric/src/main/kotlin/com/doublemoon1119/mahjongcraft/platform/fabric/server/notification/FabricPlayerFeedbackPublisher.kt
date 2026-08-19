package com.doublemoon1119.mahjongcraft.platform.fabric.server.notification

import com.akuleshov7.ktoml.Toml
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.buildMahjongDtoSerializersModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.resolveDisplayText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.GameTurnStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Provided
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
    private val tileDisplayNames: TileDisplayNameRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val tileEmojiRegistry: TileEmojiRegistry,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) : MinecraftPlayerFeedbackPublisher {

    /** 用來把設定 hover 顯示成 TOML 的 codec；序列化模組須與 [json] 共用，才認得到多型規則設定。 */
    private val toml = Toml(serializersModule = buildMahjongDtoSerializersModule(networkRegistries))

    /** 切換至 server thread，並在玩家仍在線時傳送目前版本選定的回饋。 */
    override fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) {
            val player = serverHolder.findPlayer(playerId) ?: return@launch
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
                is MinecraftPlayerFeedback.AiStrategyChanged ->
                    player.sendMessage(aiStrategyChangedMessage(feedback))
                MinecraftPlayerFeedback.TargetNotAi ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.TARGET_NOT_AI), true)
                MinecraftPlayerFeedback.ChangeAiStrategyFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.CHANGE_AI_STRATEGY_FAILED), true)
                is MinecraftPlayerFeedback.GameConfigChanged ->
                    player.sendMessage(gameConfigChangedMessage(feedback))
                is MinecraftPlayerFeedback.GameConfigUnchanged ->
                    player.sendMessage(gameConfigUnchangedMessage(feedback))
                MinecraftPlayerFeedback.InvalidGameConfig ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.INVALID_GAME_CONFIG), true)
                MinecraftPlayerFeedback.ChangeGameConfigFailed ->
                    player.sendMessage(Text.translatable(MinecraftMessageKeys.CHANGE_GAME_CONFIG_FAILED), true)
                is MinecraftPlayerFeedback.ShowGameConfig ->
                    player.sendMessage(showGameConfigMessage(feedback))
                is MinecraftPlayerFeedback.GameActionPerformed ->
                    player.sendMessage(gameActionPerformedMessage(feedback))
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
            .append(bracketedInteractiveLabel(MinecraftMessageKeys.GAME_CREATED_LOCATION_LABEL, hoverText))
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

    /** 建立「已變更遊戲設定」訊息，格式比照 [aiStrategyChangedMessage] 的「舊設定 → 新設定」配色慣例。 */
    private fun gameConfigChangedMessage(feedback: MinecraftPlayerFeedback.GameConfigChanged): MutableText = Text.translatable(
        MinecraftMessageKeys.GAME_CONFIG_CHANGED,
        gameConfigText(feedback.oldConfigJson, Formatting.RED),
        gameConfigText(feedback.newConfigJson, Formatting.GREEN),
    )

    /** 建立「新設定與目前設定相同」訊息，比照 [aiStrategyChangedMessage] 對新舊相同時的特殊處理，維持無色。 */
    private fun gameConfigUnchangedMessage(feedback: MinecraftPlayerFeedback.GameConfigUnchanged): MutableText = Text.translatable(
        MinecraftMessageKeys.GAME_CONFIG_UNCHANGED,
        gameConfigText(feedback.configJson),
    )

    /**
     * 建立「顯示目前遊戲設定」訊息；可互動文字點擊後透過 [ClickEvent.Action.RUN_COMMAND] 觸發
     * client-only 指令開啟設定編輯畫面（見
     * [com.doublemoon1119.mahjongcraft.platform.fabric.client.room.FabricOpenRoomConfigScreenCommand]），
     * 不是複製 JSON。
     */
    private fun showGameConfigMessage(feedback: MinecraftPlayerFeedback.ShowGameConfig): MutableText = Text.translatable(
        MinecraftMessageKeys.SHOW_GAME_CONFIG,
        bracketedInteractiveLabel(
            MinecraftMessageKeys.GAME_CONFIG_LABEL,
            gameConfigHoverText(feedback.configJson),
            ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_ROOM_CONFIG_SCREEN_COMMAND),
        ),
    )

    /**
     * 將一段設定 JSON 轉成可互動文字：點擊把原始 JSON 複製到剪貼簿（可以直接貼回設定編輯畫面），這
     * 部分已經定案不會再變。hover 顯示的 TOML 版本則只是暫時方案，見 [gameConfigHoverText]。[color]
     * 預設沿用一般可互動文字的 AQUA；「舊 → 新」類訊息才需要另外指定綠／紅。
     */
    private fun gameConfigText(configJson: String, color: Formatting = Formatting.AQUA): MutableText = bracketedInteractiveLabel(
        MinecraftMessageKeys.GAME_CONFIG_LABEL,
        gameConfigHoverText(configJson),
        ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, configJson),
        color,
    )

    /**
     * 依 vanilla「中括號 + 顏色 = 可 hover／可點擊」的慣例（例如成就、物品連結訊息），把 [labelKey]
     * 包成 `[標籤]`。中括號本身與翻譯文字套用同一個 [Style]，確保滑鼠移到中括號上也會觸發 hover，不會
     * 只有文字本身才有效果；[clickEvent] 省略時只有 hover，沒有點擊行為（例如 [gameCreatedMessage] 的
     * 座標標籤）；[color] 預設 AQUA，作為一般可互動文字的配色。
     */
    private fun bracketedInteractiveLabel(
        labelKey: String,
        hoverText: Text,
        clickEvent: ClickEvent? = null,
        color: Formatting = Formatting.AQUA,
    ): MutableText {
        var style = Style.EMPTY.withColor(color).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
        if (clickEvent != null) {
            style = style.withClickEvent(clickEvent)
        }
        return Text.literal("[").setStyle(style)
            .append(Text.translatable(labelKey).setStyle(style))
            .append(Text.literal("]").setStyle(style))
    }

    /**
     * 將一段設定 JSON 轉成 hover 顯示用的 TOML 版本（較適合人眼閱讀），供 [gameConfigText] 與
     * [showGameConfigMessage] 共用。目前是原始欄位名稱（例如 `baseSeconds`），不是「起始點數」這種對
     * 玩家友善的顯示名稱；TOML 轉換失敗時（理論上不會發生，因為這段 JSON 本來就是同一份程式碼序列化
     * 出來的）退回顯示原始 JSON。
     *
     * TODO: 這部分還會再調整——需要一套「設定欄位 → 翻譯後顯示名稱」的對照（GUI 顯示設定時也會用到
     *   同一套對照），屆時應該抽成共用元件，不要各自複製一份。
     */
    private fun gameConfigHoverText(configJson: String): MutableText = try {
        Text.literal(toml.encodeToString(json.decodeFromString<GameConfigDto>(configJson)))
    } catch (_: Exception) {
        Text.literal(configJson)
    }

    /** 建立「已執行對局動作」訊息，例如「已執行：打出 五筒」。 */
    private fun gameActionPerformedMessage(feedback: MinecraftPlayerFeedback.GameActionPerformed): MutableText = Text.translatable(
        MinecraftMessageKeys.GAME_ACTION_PERFORMED,
        feedback.action.toDisplayText(feedback.referenceTile, tileDisplayNames, tileAssetRegistry, tileEmojiRegistry),
    )

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
                    .append(action.toDisplayText(referenceTile, tileDisplayNames, tileAssetRegistry, tileEmojiRegistry))
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
         * 麻將牌字元 `🀇` 而非英文字串的理由見
         * [com.doublemoon1119.mahjongcraft.platform.fabric.client.room.FabricOpenRoomConfigScreenCommand]
         * 的類別 KDoc。
         */
        const val OPEN_ROOM_CONFIG_SCREEN_COMMAND: String = "/🀇 open_room_config_screen"
    }
}
