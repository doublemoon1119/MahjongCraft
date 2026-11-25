package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.LocatedMahjongTable
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.CompletableFuture
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * `/mahjongcraft room` 底下的房間與對局階段玩家指令：`join`、`leave`、`ready`、`start`、
 * `ai add`、`ai strategy`、`kick`、`config`。
 *
 * 用途：
 *
 * - 右鍵、蹲下右鍵桌子是既有的建房、加入、離開方式，這裡提供功能相同的指令版本。
 * - 它是與手勢並存的正式操作方式，不是暫時的除錯工具。
 * - 集中在 `room` 底下，是因為對局階段（出牌、吃碰槓胡）之後也會有自己的指令，避免全部擠在 `/mahjongcraft` 根節點。
 *
 * 目標桌子：
 *
 * - [join]、[leave] 需要玩家明確指定目標桌子（[TableCoordinateArgument]），不會自動選最近的一張。
 * - Tab 補全只列出玩家目前可互動範圍內的桌子（[ReachableMahjongTableResolver]），與右鍵桌子能生效的範圍一致。
 * - [ready]、[start]、[addAi]、[changeAiStrategy]、[showConfig] 改用玩家目前的房間歸屬解析目標房間，不需要指定桌子。
 * - [ready]、[start] 仍要求玩家實際在桌子的可互動範圍內，[start] 還會檢查所有房間成員，見
 *   `MahjongTableRoomService.isPlayerReachable`。
 *
 * 設定：
 *
 * - [showConfig] 不帶參數，只印出目前設定。
 * - 訊息裡的可互動文字點擊後，觸發用戶端指令 `/mahjongcraft_client room_config screen`（[RoomConfigScreenCommand]），開啟設定
 *   編輯畫面。
 * - 實際送出新設定走獨立的網路頻道，不經過這個伺服器指令。
 *
 * 引數：
 *
 * - [kick]、[changeAiStrategy] 的目標引數，以及 [addAi]、[changeAiStrategy] 的策略引數，都用 [StringArgumentType.string]，
 *   不用 [StringArgumentType.word]。策略 key 慣用的命名空間冒號，在未加引號的引數中不合法。
 * - Tab 補全時以 [StringArgumentType.escapeIfRequired] 視需要自動加上引號。
 * - 補全 AI 候選時，打進聊天框的文字固定是與語言無關的代號（[RoomMemberCandidate.token]），同一個指令不因輸入者的語言而不同。
 * - 依語言顯示的「AI 玩家 N」放在 [com.mojang.brigadier.suggestion.SuggestionsBuilder.suggest] 的 tooltip
 *   （[Component.translatable]），由客戶端依玩家的語言解析。
 *
 * @property tableResolver 依玩家可互動範圍找出候選麻將桌。
 * @property memberCandidateResolver 依玩家目前房間列出可踢除或可更換策略的候選成員。
 * @property aiStrategyRegistry 目前已註冊的 AI 策略 key，供 `ai add`／`ai strategy` 的策略引數 Tab
 *   補全。
 * @property aiStrategyDisplayNames 將 AI 策略 key 解析成可翻譯的顯示名稱，供 AI 候選項目與策略候選
 *   項目的 tooltip 使用。
 * @property roomService 實際執行房間／對局動作的既有服務。
 * @property feedbackPublisher 找不到指定桌子或候選目標時的回饋。
 * @property scope 橋接 suggestion provider 內部 suspend 查詢與 Brigadier 同步 API 的協程 scope。
 */
class RoomCommand(
    private val tableResolver: ReachableMahjongTableResolver,
    private val memberCandidateResolver: RoomMemberCandidateResolver,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
    private val aiStrategyDisplayNames: AiStrategyDisplayNameRegistry,
    private val roomService: MahjongTableRoomService,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val scope: AppCoroutineScope,
) {
    /**
     * 將 `/mahjongcraft room join|leave|ready|start|ai|kick` 加入指令分派器。
     *
     * @param events 提供指令登記時機的伺服器事件來源。
     */
    fun register(events: ServerEvents) {
        events.onRegisterCommands { dispatcher ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal("room")
                            .then(
                                literal("join")
                                    .then(
                                        argument(TABLE_ARGUMENT, StringArgumentType.word())
                                            .suggests(::suggestReachableTables)
                                            .executes { context -> join(context) },
                                    ),
                            )
                            .then(
                                literal("leave")
                                    .then(
                                        argument(TABLE_ARGUMENT, StringArgumentType.word())
                                            .suggests(::suggestReachableTables)
                                            .executes { context -> leave(context) },
                                    ),
                            )
                            .then(literal("ready").executes { context -> ready(context.source) })
                            .then(literal("start").executes { context -> start(context.source) })
                            .then(
                                literal("ai")
                                    .then(
                                        literal("add")
                                            .executes { context -> addAi(context.source, strategyKey = null) }
                                            .then(
                                                argument(STRATEGY_ARGUMENT, StringArgumentType.string())
                                                    .suggests(::suggestAiStrategies)
                                                    .executes { context ->
                                                        addAi(
                                                            source = context.source,
                                                            strategyKey = StringArgumentType.getString(context, STRATEGY_ARGUMENT),
                                                        )
                                                    },
                                            ),
                                    )
                                    .then(
                                        literal("strategy")
                                            .then(
                                                argument(TARGET_ARGUMENT, StringArgumentType.string())
                                                    .suggests(::suggestAiTargets)
                                                    .then(
                                                        argument(STRATEGY_ARGUMENT, StringArgumentType.string())
                                                            .suggests(::suggestAiStrategies)
                                                            .executes { context -> changeAiStrategy(context) },
                                                    ),
                                            ),
                                    ),
                            )
                            .then(
                                literal("kick")
                                    .then(
                                        argument(TARGET_ARGUMENT, StringArgumentType.string())
                                            .suggests(::suggestAllTargets)
                                            .executes { context -> kick(context) },
                                    ),
                            )
                            .then(literal("config").executes { context -> showConfig(context.source) }),
                    ),
            )
        }
    }

    /**
     * 建立或加入指定座標的麻將桌房間，等同右鍵桌子。
     *
     * @param context Brigadier 的指令來源與引數。
     */
    private fun join(context: CommandContext<CommandSourceStack>): Int = withResolvedTable(context) { player, table ->
        roomService.interact(table, player)
    }

    /**
     * 離開指定座標麻將桌的等待階段，等同蹲下右鍵桌子。
     *
     * @param context Brigadier 的指令來源與引數。
     */
    private fun leave(context: CommandContext<CommandSourceStack>): Int = withResolvedTable(context) { player, table ->
        roomService.leave(table, player)
    }

    /**
     * 切換玩家目前所在房間的準備狀態。
     *
     * @param source Brigadier 指令來源。
     */
    private fun ready(source: CommandSourceStack): Int = withPlayer(source) { player ->
        roomService.ready(player)
    }

    /**
     * 房主在所有人皆已準備完成時開始遊戲。
     *
     * @param source Brigadier 指令來源。
     */
    private fun start(source: CommandSourceStack): Int = withPlayer(source) { player ->
        roomService.start(player)
    }

    /**
     * 房主替目前所在房間新增一名 AI 玩家，[strategyKey] 省略時使用預設策略。
     *
     * @param source Brigadier 指令來源。
     * @param strategyKey 可選的 AI 策略註冊 key。
     */
    private fun addAi(source: CommandSourceStack, strategyKey: String?): Int = withPlayer(source) { player ->
        roomService.addAi(player, strategyKey)
    }

    /**
     * 房主將指定的候選成員（真人或 AI）移出目前所在房間。
     *
     * @param context Brigadier 的指令來源與候選標籤。
     */
    private fun kick(context: CommandContext<CommandSourceStack>): Int = withPlayer(context.source) { player ->
        val label = StringArgumentType.getString(context, TARGET_ARGUMENT)
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val targetId = memberCandidateResolver.resolve(playerId, label)
            if (targetId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.KickFailed)
                return@launch
            }
            roomService.kick(player, targetId)
        }
    }

    /**
     * 房主替目前所在房間內的某個 AI 更換策略。
     *
     * @param context Brigadier 的指令來源、目標標籤與策略 key。
     */
    private fun changeAiStrategy(context: CommandContext<CommandSourceStack>): Int = withPlayer(context.source) { player ->
        val label = StringArgumentType.getString(context, TARGET_ARGUMENT)
        val strategyKey = StringArgumentType.getString(context, STRATEGY_ARGUMENT)
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val targetId = memberCandidateResolver.resolve(playerId, label)
            if (targetId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.ChangeAiStrategyFailed)
                return@launch
            }
            roomService.changeAiStrategy(player, targetId, strategyKey)
        }
    }

    /**
     * 顯示玩家目前所在房間的完整遊戲設定，訊息本身可點擊開啟編輯畫面。
     *
     * @param source Brigadier 指令來源。
     */
    private fun showConfig(source: CommandSourceStack): Int = withPlayer(source) { player ->
        roomService.showConfig(player)
    }

    /**
     * 解析指令帶入的座標引數，找不到對應且可互動的桌子時回報原因並中止。
     *
     * @param context Brigadier 指令來源與桌子座標引數。
     * @param action 桌子解析成功後要執行的操作。
     */
    private fun withResolvedTable(
        context: CommandContext<CommandSourceStack>,
        action: (ServerPlayer, LocatedMahjongTable) -> Unit,
    ): Int = withPlayer(context.source) { player ->
        val pos = TableCoordinateArgument.parse(StringArgumentType.getString(context, TABLE_ARGUMENT))
        val table = pos?.let { tableResolver.resolve(player, it) }
        if (table == null) {
            feedbackPublisher.publish(player.uuid.toKotlinUuid(), MinecraftPlayerFeedback.TableNotReachable)
            return@withPlayer
        }
        action(player, table)
    }

    /**
     * 解析執行指令的玩家，非玩家（例如主控台）執行時直接以指令失敗回應。
     *
     * @param source Brigadier 指令來源。
     * @param action 解析到玩家時要執行的操作。
     */
    private fun withPlayer(source: CommandSourceStack, action: (ServerPlayer) -> Unit): Int {
        val player = source.player ?: return COMMAND_FAILURE
        action(player)
        return COMMAND_SUCCESS
    }

    /**
     * 列出執行指令玩家目前可互動範圍內所有桌子的座標，作為 `<table>` 引數的 Tab 補全建議。
     *
     * @param context Brigadier 指令來源。
     * @param builder 收集補全候選的 builder。
     * @return 完成桌子座標候選的 future。
     */
    private fun suggestReachableTables(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val player = context.source.player
        if (player != null) {
            tableResolver.findReachable(player).forEach { table ->
                builder.suggest(TableCoordinateArgument.format(table.pos))
            }
        }
        return builder.buildFuture()
    }

    /**
     * 列出目前已註冊的 AI 策略 key，作為 `ai add`／`ai strategy` 的策略引數 Tab 補全建議。tooltip
     * 顯示該策略翻譯後的顯示名稱，與 AI 候選項目的呈現方式一致。
     *
     * @param context Brigadier 指令來源。
     * @param builder 收集補全候選的 builder。
     * @return 完成策略 key 候選的 future。
     */
    private fun suggestAiStrategies(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        aiStrategyRegistry.getAllStrategyKeys().forEach { key ->
            builder.suggest(StringArgumentType.escapeIfRequired(key), aiStrategyDisplayNames.resolveDisplayText(key))
        }
        return builder.buildFuture()
    }

    /**
     * 列出執行指令玩家目前所在房間內所有候選成員（含真人與 AI），作為 `kick <target>` 引數的建議。
     *
     * @param context Brigadier 指令來源。
     * @param builder 收集補全候選的 builder。
     * @return 完成成員候選的 future。
     */
    private fun suggestAllTargets(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestTargets(context, builder) { playerId ->
        memberCandidateResolver.listCandidates(playerId)
    }

    /**
     * 列出執行指令玩家目前所在房間內的 AI 候選成員，作為 `ai strategy <target>` 引數的建議。
     *
     * @param context Brigadier 指令來源。
     * @param builder 收集補全候選的 builder。
     * @return 完成 AI 成員候選的 future。
     */
    private fun suggestAiTargets(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestTargets(context, builder) { playerId ->
        memberCandidateResolver.listAiCandidates(playerId)
    }

    /**
     * 依 [listCandidates] 列出候選成員 tooltip 補全建議的共用邏輯。真人只顯示使用者名稱，AI 額外帶上
     * 序號與策略顯示名稱的 tooltip。
     *
     * 候選清單是一次 suspend 查詢，這裡用 [CompletableFuture] 手動橋接：[SuggestionsBuilder.build]
     * 本身是同步、立即可用的操作，協程完成查詢後直接呼叫它並完成回傳的 future，不需要額外的執行緒
     * 切換或阻塞等待。
     *
     * @param context Brigadier 指令來源。
     * @param builder 收集補全候選的 builder。
     * @param listCandidates 依執行者 UUID 取得可建議成員的非同步查詢。
     * @return 完成候選成員與 tooltip 的 future。
     */
    private fun suggestTargets(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder,
        listCandidates: suspend (Uuid) -> List<RoomMemberCandidate>,
    ): CompletableFuture<Suggestions> {
        val future = CompletableFuture<Suggestions>()
        val player = context.source.player
        if (player == null) {
            future.complete(builder.build())
            return future
        }
        scope.launch {
            listCandidates(player.uuid.toKotlinUuid()).forEach { candidate ->
                val token = StringArgumentType.escapeIfRequired(candidate.token)
                if (candidate.aiSequence != null) {
                    val strategyName = candidate.strategyKey
                        ?.let(aiStrategyDisplayNames::resolveDisplayText)
                        ?: Component.literal("?")
                    val tooltip = Component.translatable(
                        MinecraftMessageKeys.KICK_CANDIDATE_AI_LABEL,
                        candidate.aiSequence,
                        strategyName,
                    )
                    builder.suggest(token, tooltip)
                } else {
                    builder.suggest(token)
                }
            }
            future.complete(builder.build())
        }
        return future
    }

    private companion object {
        /** 桌子座標引數名稱。 */
        const val TABLE_ARGUMENT: String = "table"

        /** AI 策略引數名稱。 */
        const val STRATEGY_ARGUMENT: String = "strategy"

        /** 踢除或更換策略目標引數名稱。 */
        const val TARGET_ARGUMENT: String = "target"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
