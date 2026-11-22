package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.notification

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.notification.GameConfigTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.resolveDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.bracketedInteractiveLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.toDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.BuiltInRuleWordingIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.GameTurnStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.RuleWordingRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.minecraftAbortReasonTranslationKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import kotlin.uuid.Uuid

/**
 * 使用 Minecraft 1.20.1 呈現一次性玩家回饋。
 *
 * 目前所有回饋均轉成本地化 chat 訊息。每個 exhaustive `when` 分支可執行零到多個版本特有效果，
 * 不要求回饋與 translation key 一對一。若其他版本能使用新的 bar、title、音效或多種效果並行，
 * 可以在自己的實作中重新解釋同一個 [MinecraftPlayerFeedback]，不必讓共用模型列出所有版本 UI API
 * 的聯集。
 *
 * @property serverHolder 目前伺服器與有效收件玩家。
 * @property playerIdentities 真人最後已知普通名稱來源。
 * @property aiStrategyDisplayNames AI 策略的顯示名稱註冊表。
 * @property actionVocabulary 規則動作的用語註冊表。
 * @property exhaustiveDrawReasonDisplayNames 流局原因的顯示名稱註冊表。
 * @property ruleWordings 依規則選擇共用文字說法的註冊表。
 * @property tileDisplayNames 牌種的顯示名稱註冊表。
 * @property tileAssetRegistry 牌面資產註冊表。
 * @property tileEmojiRegistry 聊天牌面圖示註冊表。
 * @property gameConfigFormatter 遊戲設定的聊天文字格式化器。
 * @property scope 玩家回饋工作的伺服器作用域。
 * @property dispatchers 伺服器主執行緒排程。
 */
class PlayerFeedbackPublisher(
    private val serverHolder: ServerHolder,
    private val playerIdentities: ServerPlayerIdentityStore,
    private val aiStrategyDisplayNames: AiStrategyDisplayNameRegistry,
    private val actionVocabulary: GameActionVocabularyRegistry,
    private val exhaustiveDrawReasonDisplayNames: ExhaustiveDrawReasonDisplayNameRegistry,
    private val ruleWordings: RuleWordingRegistry,
    private val tileDisplayNames: TileDisplayNameRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val tileEmojiRegistry: TileEmojiRegistry,
    private val gameConfigFormatter: GameConfigTextFormatter,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : MinecraftPlayerFeedbackPublisher {
    /** 記錄包含大量 hover 資訊的正式回饋原始資料。 */
    private val logger = mahjongCraftLogger(PlayerFeedbackPublisher::class)

    /**
     * 切換至 server thread，並在玩家仍在線時傳送目前版本選定的回饋。
     *
     * @param playerId 回饋目標玩家 UUID。
     * @param feedback 要呈現的一次性玩家回饋。
     */
    override fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback) {
        if (serverHolder.current() == null) return
        scope.launch(dispatchers.main) {
            val player = serverHolder.findPlayer(playerId) ?: return@launch
            when (feedback) {
                MinecraftPlayerFeedback.GameAlreadyStarted ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.GAME_ALREADY_STARTED), true)
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
                    player.sendSystemMessage(gameCreatedMessage(feedback.location))
                }
                MinecraftPlayerFeedback.GameJoined ->
                    player.sendSystemMessage(Component.translatable(MinecraftMessageKeys.GAME_JOINED))
                MinecraftPlayerFeedback.PlayerNotInGame ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.PLAYER_NOT_IN_GAME), true)
                MinecraftPlayerFeedback.PlayerNotInAnyGame ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.PLAYER_NOT_IN_ANY_GAME), true)
                MinecraftPlayerFeedback.GameLeaveDeniedWhilePlaying ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.GAME_LEAVE_DENIED_WHILE_PLAYING), true)
                MinecraftPlayerFeedback.GameDissolved ->
                    player.sendSystemMessage(Component.translatable(MinecraftMessageKeys.GAME_DISSOLVED))
                MinecraftPlayerFeedback.GameLeft ->
                    player.sendSystemMessage(Component.translatable(MinecraftMessageKeys.GAME_LEFT))
                MinecraftPlayerFeedback.GameLeaveFailed ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.GAME_LEAVE_FAILED), true)
                MinecraftPlayerFeedback.PlayerAlreadyInGame ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.PLAYER_ALREADY_IN_GAME), true)
                MinecraftPlayerFeedback.GameJoinFailed ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.GAME_JOIN_FAILED), true)
                is MinecraftPlayerFeedback.ReadyToggled ->
                    player.sendSystemMessage(readyToggledMessage(feedback.isReady))
                MinecraftPlayerFeedback.HostReadyNotRequired ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.HOST_READY_NOT_REQUIRED), true)
                MinecraftPlayerFeedback.NotGameHost ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.NOT_GAME_HOST), true)
                MinecraftPlayerFeedback.InvalidPlayerCount ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.INVALID_PLAYER_COUNT), true)
                MinecraftPlayerFeedback.NotAllPlayersReady ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.NOT_ALL_PLAYERS_READY), true)
                MinecraftPlayerFeedback.GameStartFailed ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.GAME_START_FAILED), true)
                MinecraftPlayerFeedback.RuleUnavailable ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.RULE_UNAVAILABLE), true)
                MinecraftPlayerFeedback.GameConfigInvalid ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.GAME_CONFIG_INVALID), true)
                MinecraftPlayerFeedback.AiStrategyNotFound ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.AI_STRATEGY_NOT_FOUND), true)
                MinecraftPlayerFeedback.RoomExclusionListFull -> player.displayClientMessage(Component.translatable(MinecraftMessageKeys.ROOM_EXCLUSION_LIST_FULL), true)
                MinecraftPlayerFeedback.RoomParticipantExcluded ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.ROOM_PARTICIPANT_EXCLUDED), true)
                MinecraftPlayerFeedback.RoomLayoutConflict ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.ROOM_LAYOUT_CONFLICT), true)
                MinecraftPlayerFeedback.SeatedParticipantUnavailable ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.SEATED_PARTICIPANT_UNAVAILABLE), true)
                MinecraftPlayerFeedback.TableNotReachable ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.TABLE_NOT_REACHABLE), true)
                is MinecraftPlayerFeedback.StartBlockedByPlayers ->
                    player.displayClientMessage(startBlockedByPlayersMessage(feedback), true)
                MinecraftPlayerFeedback.StartBlockedByTableSpace ->
                    player.sendSystemMessage(Component.translatable(MinecraftMessageKeys.START_BLOCKED_TABLE_SPACE).withStyle(ChatFormatting.RED))
                is MinecraftPlayerFeedback.AiAdded ->
                    player.sendSystemMessage(aiAddedMessage(feedback.strategyKey))
                MinecraftPlayerFeedback.AddAiFailed ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.ADD_AI_FAILED), true)
                MinecraftPlayerFeedback.GameFull ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.GAME_FULL), true)
                MinecraftPlayerFeedback.PlayerKicked ->
                    player.sendSystemMessage(Component.translatable(MinecraftMessageKeys.PLAYER_KICKED))
                MinecraftPlayerFeedback.KickedFromGame ->
                    player.sendSystemMessage(Component.translatable(MinecraftMessageKeys.KICKED_FROM_GAME))
                MinecraftPlayerFeedback.CannotKickSelf ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.CANNOT_KICK_SELF), true)
                MinecraftPlayerFeedback.KickFailed ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.KICK_FAILED), true)
                is MinecraftPlayerFeedback.AiStrategyChanged ->
                    player.sendSystemMessage(aiStrategyChangedMessage(feedback))
                MinecraftPlayerFeedback.TargetNotAi ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.TARGET_NOT_AI), true)
                MinecraftPlayerFeedback.ChangeAiStrategyFailed ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.CHANGE_AI_STRATEGY_FAILED), true)
                is MinecraftPlayerFeedback.GameConfigChanged -> {
                    logger.debug(
                        "Game-config-changed hover playerId={} oldConfig={} newConfig={}",
                        playerId,
                        feedback.oldConfigJson,
                        feedback.newConfigJson,
                    )
                    player.sendSystemMessage(gameConfigChangedMessage(feedback))
                }
                is MinecraftPlayerFeedback.GameConfigUnchanged -> {
                    logger.debug("Game-config-unchanged hover playerId={} config={}", playerId, feedback.configJson)
                    player.sendSystemMessage(gameConfigUnchangedMessage(feedback))
                }
                MinecraftPlayerFeedback.ChangeGameConfigFailed ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.CHANGE_GAME_CONFIG_FAILED), true)
                is MinecraftPlayerFeedback.ShowGameConfig -> {
                    logger.debug("Game-config-show hover playerId={} config={}", playerId, feedback.configJson)
                    player.sendSystemMessage(showGameConfigMessage(feedback))
                }
                MinecraftPlayerFeedback.NotYourTurn ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.NOT_YOUR_TURN), true)
                MinecraftPlayerFeedback.ForcedAutoPlayActive ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.FORCED_AUTO_PLAY_ACTIVE), true)
                MinecraftPlayerFeedback.IllegalGameAction ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.ILLEGAL_GAME_ACTION), true)
                is MinecraftPlayerFeedback.WallExhausted -> {
                    val key = requireNotNull(ruleWordings.find(feedback.ruleModuleId, BuiltInRuleWordingIds.WALL_EXHAUSTED)) { "Wall exhausted has a built-in wording" }
                    player.displayClientMessage(Component.translatable(key), true)
                }
                MinecraftPlayerFeedback.UnsupportedGameAction ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.UNSUPPORTED_GAME_ACTION), true)
                MinecraftPlayerFeedback.TableAnimationBusy ->
                    player.displayClientMessage(Component.translatable(MinecraftMessageKeys.TABLE_ANIMATION_BUSY), true)
                is MinecraftPlayerFeedback.MatchAborted ->
                    player.sendSystemMessage(matchAbortedMessage(feedback.reasonId))
                is MinecraftPlayerFeedback.ShowHand ->
                    player.sendSystemMessage(showHandMessage(feedback))
            }
        }
    }

    /**
     * 建立「已建立麻將遊戲」訊息；[location] 存在時句尾附上可 hover 顯示座標的 `[位置]` 標籤，主世界
     * 以外的維度會額外多顯示一行維度 ID（多數桌子都在主世界，省略維度可以維持提示簡潔）。
     *
     * @param location 可選的桌子位置；為 null 時只顯示建立成功文字。
     * @return 可供玩家閱讀的建立成功訊息。
     */
    private fun gameCreatedMessage(location: TableLocation?): MutableComponent {
        val message = Component.translatable(MinecraftMessageKeys.GAME_CREATED)
        if (location == null) return message

        var hoverText: MutableComponent = Component.literal("${location.x}, ${location.y}, ${location.z}")
        if (location.dimensionId != OVERWORLD_DIMENSION_ID) {
            hoverText = hoverText.append("\n").append(Component.literal(location.dimensionId))
        }
        return message.append(Component.literal(" "))
            .append(bracketedInteractiveLabel(Component.translatable(MinecraftMessageKeys.GAME_CREATED_LOCATION_LABEL), hoverText))
    }

    /**
     * 建立「已新增 AI 玩家」訊息，策略名稱與 `kick` 補全 tooltip 使用同一套顯示名稱解析。
     *
     * @param strategyKey AI 策略註冊 key。
     * @return 含策略顯示名稱的新增訊息。
     */
    private fun aiAddedMessage(strategyKey: String): MutableComponent = Component.translatable(
        MinecraftMessageKeys.AI_ADDED,
        aiStrategyDisplayNames.resolveDisplayText(strategyKey),
    )

    /**
     * 建立「部分成員離線或太遠，無法開始對局」訊息；兩種原因各自成行，只有真的有內容的那一種才會出現。
     *
     * @param feedback 開局遭阻擋的離線與距離過遠玩家 UUID。
     * @return 分別列出有問題成員的訊息。
     */
    private fun startBlockedByPlayersMessage(feedback: MinecraftPlayerFeedback.StartBlockedByPlayers): MutableComponent {
        val lines = buildList {
            if (feedback.offlinePlayerIds.isNotEmpty()) {
                add(Component.translatable(MinecraftMessageKeys.START_BLOCKED_OFFLINE_PLAYERS, joinPlayerNames(feedback.offlinePlayerIds)))
            }
            if (feedback.distantPlayerIds.isNotEmpty()) {
                add(Component.translatable(MinecraftMessageKeys.START_BLOCKED_DISTANT_PLAYERS, joinPlayerNames(feedback.distantPlayerIds)))
            }
        }
        val message = Component.empty()
        lines.forEachIndexed { index, line ->
            if (index > 0) message.append("\n")
            message.append(line)
        }
        return message
    }

    /**
     * 用逗號串接每個玩家目前已知的名稱；查不到名稱時顯示未知玩家文字。
     *
     * @param playerIds 要顯示的玩家 UUID 清單。
     * @return 以逗號分隔的名稱文字。
     */
    private fun joinPlayerNames(playerIds: List<Uuid>): String = playerIds.joinToString {
        playerIdentities.resolveKnownName(it) ?: Component.translatable(MinecraftPlayerKeys.UNKNOWN).string
    }

    /**
     * 建立「已更換 AI 策略」訊息，格式為「舊策略 → 新策略」，比照使用者對「舊 → 新」類訊息的配色慣例：
     * 舊值紅色、新值綠色。新舊策略相同時改用 [MinecraftMessageKeys.AI_STRATEGY_UNCHANGED]——這個操作
     * 本身仍是成功的冪等操作，只是換一句不會出現「同一個策略 → 同一個策略」這種容易讓人誤以為系統
     * 異常的說法，此時維持無色。
     *
     * @param feedback AI 策略變更結果與策略序號。
     * @return 本地化的新舊策略訊息。
     */
    private fun aiStrategyChangedMessage(feedback: MinecraftPlayerFeedback.AiStrategyChanged): MutableComponent {
        if (feedback.oldStrategyKey == feedback.newStrategyKey) {
            return Component.translatable(
                MinecraftMessageKeys.AI_STRATEGY_UNCHANGED,
                feedback.aiSequence,
                aiStrategyDisplayNames.resolveDisplayText(feedback.newStrategyKey),
            )
        }
        return Component.translatable(
            MinecraftMessageKeys.AI_STRATEGY_CHANGED,
            feedback.aiSequence,
            aiStrategyDisplayNames.resolveDisplayText(feedback.oldStrategyKey).copy().withStyle(ChatFormatting.RED),
            aiStrategyDisplayNames.resolveDisplayText(feedback.newStrategyKey).copy().withStyle(ChatFormatting.GREEN),
        )
    }

    /**
     * 建立「已變更遊戲設定」訊息；實際的「舊值 → 新值」差異位於可互動標籤的 hover。
     *
     * @param feedback 含舊、新設定 JSON 的回饋。
     * @return 可點擊並顯示設定差異的訊息。
     */
    private fun gameConfigChangedMessage(feedback: MinecraftPlayerFeedback.GameConfigChanged): MutableComponent = Component.translatable(
        MinecraftMessageKeys.GAME_CONFIG_CHANGED,
        bracketedInteractiveLabel(
            label = Component.translatable(MinecraftMessageKeys.GAME_CONFIG_LABEL),
            hoverText = gameConfigFormatter.changes(feedback.oldConfigJson, feedback.newConfigJson),
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_ROOM_CONFIG_SCREEN_COMMAND),
            color = ChatFormatting.AQUA,
        ),
    )

    /**
     * 建立「新設定與目前設定相同」訊息，比照 [aiStrategyChangedMessage] 對新舊相同時的特殊處理，維持無色。
     *
     * @param feedback 含目前設定 JSON 的回饋。
     * @return 顯示目前設定的訊息。
     */
    private fun gameConfigUnchangedMessage(feedback: MinecraftPlayerFeedback.GameConfigUnchanged): MutableComponent = Component.translatable(
        MinecraftMessageKeys.GAME_CONFIG_UNCHANGED,
        gameConfigText(feedback.configJson),
    )

    /**
     * 建立「顯示目前遊戲設定」訊息；可互動文字點擊後透過 [ClickEvent.Action.RUN_COMMAND] 觸發
     * client-only 指令開啟設定編輯畫面（見 [RoomConfigScreenCommand]），
     * 不是複製 JSON。
     *
     * @param feedback 含要呈現之設定 JSON 的回饋。
     * @return 可點擊開啟設定編輯畫面的訊息。
     */
    private fun showGameConfigMessage(feedback: MinecraftPlayerFeedback.ShowGameConfig): MutableComponent = Component.translatable(
        MinecraftMessageKeys.SHOW_GAME_CONFIG,
        bracketedInteractiveLabel(
            label = Component.translatable(MinecraftMessageKeys.GAME_CONFIG_LABEL),
            hoverText = gameConfigHoverText(feedback.configJson),
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_ROOM_CONFIG_SCREEN_COMMAND),
        ),
    )

    /**
     * 將設定轉成可開啟 RoomScreen 的互動標籤，hover 顯示本地化完整內容。
     *
     * @param configJson 遊戲設定 JSON。
     * @param color 互動標籤顏色。
     * @return 可開啟設定畫面的互動文字。
     */
    private fun gameConfigText(configJson: String, color: ChatFormatting = ChatFormatting.AQUA): MutableComponent = bracketedInteractiveLabel(
        label = Component.translatable(MinecraftMessageKeys.GAME_CONFIG_LABEL),
        hoverText = gameConfigHoverText(configJson),
        clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_ROOM_CONFIG_SCREEN_COMMAND),
        color = color,
    )

    /**
     * 將設定轉成本地化 hover 文字，與 RoomScreen 共用宣告式設定 schema。
     *
     * @param configJson 遊戲設定 JSON。
     * @return 完整設定的本地化 hover 文字。
     */
    private fun gameConfigHoverText(configJson: String): MutableComponent = gameConfigFormatter.full(configJson)

    /** 建立中止訊息，讓平台已知原因使用既有翻譯，未知原因則顯示通用結果文字。 */
    private fun matchAbortedMessage(reasonId: String): MutableComponent = Component.translatable(
        MinecraftMessageKeys.GAME_ABORTED,
        Component.translatable(minecraftAbortReasonTranslationKey(reasonId)),
    )

    /**
     * 建立 `/mahjongcraft game hand` 的手牌畫面：手牌列表、副露（有的話）、目前可執行的特殊動作
     * （沒有的話顯示 [MinecraftMessageKeys.HAND_NO_LEGAL_ACTIONS] 提示玩家改用 `discard`）。
     *
     * @param feedback 手牌、副露、回合狀態與合法動作資料。
     * @return 組合後的手牌及可執行動作訊息。
     */
    private fun showHandMessage(feedback: MinecraftPlayerFeedback.ShowHand): MutableComponent {
        val message = Component.translatable(MinecraftMessageKeys.HAND_TITLE)
        feedback.standingTiles.forEach { tile ->
            message.append(Component.literal(" ")).append(tile.toDisplayText(tileDisplayNames, tileAssetRegistry, tileEmojiRegistry))
        }

        if (feedback.melds.isNotEmpty()) {
            message.append(Component.literal("\n")).append(Component.translatable(MinecraftMessageKeys.HAND_MELDS_TITLE))
            feedback.melds.forEach { meld ->
                message.append(Component.literal(" ["))
                meld.forEachIndexed { index, tile ->
                    if (index > 0) message.append(Component.literal(" "))
                    message.append(tile.toDisplayText(tileDisplayNames, tileAssetRegistry, tileEmojiRegistry))
                }
                message.append(Component.literal("]"))
            }
        }

        message.append(Component.literal("\n")).append(Component.translatable(MinecraftMessageKeys.HAND_LEGAL_ACTIONS_TITLE))
        if (feedback.legalActions.isEmpty()) {
            val emptyKey = when (feedback.turnStatus) {
                GameTurnStatus.OWN_TURN -> MinecraftMessageKeys.HAND_NO_LEGAL_ACTIONS
                GameTurnStatus.AWAITING_RESPONSE -> MinecraftMessageKeys.HAND_NO_RESPONSE_AVAILABLE
                GameTurnStatus.WAITING -> MinecraftMessageKeys.HAND_WAITING
            }
            message.append(Component.literal(" ")).append(Component.translatable(emptyKey))
        } else {
            feedback.legalActions.forEachIndexed { index, (action, referenceTile) ->
                message.append(Component.literal(" ${index + 1}:"))
                    .append(
                        action.toDisplayText(
                            referenceTile = referenceTile,
                            ruleModuleId = feedback.ruleModuleId,
                            actionVocabularyRegistry = actionVocabulary,
                            displayNameRegistry = tileDisplayNames,
                            tileAssetRegistry = tileAssetRegistry,
                            tileEmojiRegistry = tileEmojiRegistry,
                            exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNames,
                        ),
                    )
            }
        }
        return message
    }

    /**
     * 組合準備狀態切換訊息：前綴 + 切換前狀態 → 切換後狀態，「準備」亮綠、「尚未準備」亮紅。
     *
     * @param isReady 切換完成後是否已準備。
     * @return 顯示狀態變化的訊息。
     */
    private fun readyToggledMessage(isReady: Boolean): MutableComponent {
        val fromKey = if (isReady) MinecraftMessageKeys.READY_STATE_NOT_READY else MinecraftMessageKeys.READY_STATE_READY
        val fromColor = if (isReady) ChatFormatting.RED else ChatFormatting.GREEN
        val toKey = if (isReady) MinecraftMessageKeys.READY_STATE_READY else MinecraftMessageKeys.READY_STATE_NOT_READY
        val toColor = if (isReady) ChatFormatting.GREEN else ChatFormatting.RED

        return Component.translatable(MinecraftMessageKeys.READY_TOGGLE_PREFIX)
            .append(Component.translatable(fromKey).withStyle(fromColor))
            .append(Component.literal(" → "))
            .append(Component.translatable(toKey).withStyle(toColor))
    }

    private companion object {
        /** 主世界的 dimension registry identifier；hover 顯示位置時，這個維度不額外標示。 */
        const val OVERWORLD_DIMENSION_ID: String = "minecraft:overworld"

        /** 開啟房間規則設定編輯畫面的 client-only 指令；點擊這個訊息或玩家自己手動輸入都能觸發，見 [RoomConfigScreenCommand] 的類別 KDoc。 */
        const val OPEN_ROOM_CONFIG_SCREEN_COMMAND: String = "/mahjongcraft_client room_config screen"
    }
}
