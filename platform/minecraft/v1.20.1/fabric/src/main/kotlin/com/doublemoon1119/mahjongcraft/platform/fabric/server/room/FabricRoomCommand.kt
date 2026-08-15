package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * `/mahjongcraft room` 底下的房間與對局階段玩家指令：`join`、`leave`、`ready`、`start`、
 * `ai add`、`ai strategy`、`kick`。
 *
 * 右鍵／蹲下右鍵桌子已經是既有的建房／加入／離開互動方式，這裡提供功能相同的指令版本，作為與手勢
 * 並存的另一種操作方式，不是暫時的除錯工具，日後也不會被移除。集中在 `room` 子分類下，是因為對局
 * 階段（出牌、吃碰槓胡）之後也會有自己的一批指令，避免全部擠在 `/mahjongcraft` 根節點下。
 *
 * [join]／[leave] 需要玩家明確指定目標桌子（[TableCoordinateArgument]），不會自動選最近的一張；
 * Tab 補全只會列出玩家目前實際可互動範圍內的桌子（[ReachableMahjongTableResolver]），與右鍵桌子
 * 能生效的範圍一致。[ready]／[start]／[addAi]／[changeAiStrategy] 改用玩家目前的房間歸屬解析目標
 * 房間，不需要玩家人在桌子附近——開局本身就會把玩家傳送到座位，先天不需要距離限制。
 *
 * [kick]、[changeAiStrategy] 的目標引數與 [addAi]／[changeAiStrategy] 的策略引數都用
 * [StringArgumentType.string]（而非 [StringArgumentType.word]），因為策略 key 慣用的命名空間冒號
 * 在未加引號的字串引數中不合法，Tab 補全時改用 [StringArgumentType.escapeIfRequired] 視需要自動加上
 * 引號。補全 AI 候選項目時，打進聊天框的實際文字固定是語言無關的技術代號（見
 * [RoomMemberCandidate.token]），因為伺服器端在 1.20.1 無法得知玩家客戶端語言；真正依語言顯示的
 * 「AI 玩家 N」文字是透過 [com.mojang.brigadier.suggestion.SuggestionsBuilder.suggest] 的 tooltip
 * 參數（[Text.translatable]）呈現，由客戶端依玩家自己的語言設定解析。
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
@Single
class FabricRoomCommand(
    private val tableResolver: ReachableMahjongTableResolver,
    private val memberCandidateResolver: RoomMemberCandidateResolver,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
    private val aiStrategyDisplayNames: AiStrategyDisplayNameRegistry,
    private val roomService: MahjongTableRoomService,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val scope: AppCoroutineScope,
) {
    /** 將 `/mahjongcraft room join|leave|ready|start|ai|kick` 加入 Fabric command dispatcher。 */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
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
                                                            context.source,
                                                            StringArgumentType.getString(context, STRATEGY_ARGUMENT),
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
                            ),
                    ),
            )
        }
    }

    /** 建立或加入指定座標的麻將桌房間，等同右鍵桌子。 */
    private fun join(context: CommandContext<ServerCommandSource>): Int = withResolvedTable(context) { player, table ->
        roomService.interact(table, player)
    }

    /** 離開指定座標麻將桌的等待階段，等同蹲下右鍵桌子。 */
    private fun leave(context: CommandContext<ServerCommandSource>): Int = withResolvedTable(context) { player, table ->
        roomService.leave(table, player)
    }

    /** 切換玩家目前所在房間的準備狀態。 */
    private fun ready(source: ServerCommandSource): Int = withPlayer(source) { player ->
        roomService.ready(player)
    }

    /** 房主在所有人皆已準備完成時開始遊戲。 */
    private fun start(source: ServerCommandSource): Int = withPlayer(source) { player ->
        roomService.start(player)
    }

    /** 房主替目前所在房間新增一名 AI 玩家，[strategyKey] 省略時使用預設策略。 */
    private fun addAi(source: ServerCommandSource, strategyKey: String?): Int = withPlayer(source) { player ->
        roomService.addAi(player, strategyKey)
    }

    /** 房主將指定的候選成員（真人或 AI）移出目前所在房間。 */
    private fun kick(context: CommandContext<ServerCommandSource>): Int = withPlayer(context.source) { player ->
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

    /** 房主替目前所在房間內的某個 AI 更換策略。 */
    private fun changeAiStrategy(context: CommandContext<ServerCommandSource>): Int = withPlayer(context.source) { player ->
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

    /** 解析指令帶入的座標引數，找不到對應且可互動的桌子時回報原因並中止。 */
    private fun withResolvedTable(
        context: CommandContext<ServerCommandSource>,
        action: (ServerPlayerEntity, MahjongTableBlockEntity) -> Unit,
    ): Int = withPlayer(context.source) { player ->
        val pos = TableCoordinateArgument.parse(StringArgumentType.getString(context, TABLE_ARGUMENT))
        val table = pos?.let { tableResolver.resolve(player, it) }
        if (table == null) {
            feedbackPublisher.publish(player.uuid.toKotlinUuid(), MinecraftPlayerFeedback.TableNotReachable)
            return@withPlayer
        }
        action(player, table)
    }

    /** 解析執行指令的玩家，非玩家（例如主控台）執行時直接以指令失敗回應。 */
    private fun withPlayer(source: ServerCommandSource, action: (ServerPlayerEntity) -> Unit): Int {
        val player = source.player ?: return COMMAND_FAILURE
        action(player)
        return COMMAND_SUCCESS
    }

    /** 列出執行指令玩家目前可互動範圍內所有桌子的座標，作為 `<table>` 引數的 Tab 補全建議。 */
    private fun suggestReachableTables(
        context: CommandContext<ServerCommandSource>,
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
     * 列出目前已註冊的 AI 策略 key，作為 `ai add`／`ai strategy` 的策略引數 Tab 補全建議；tooltip
     * 顯示該策略翻譯後的顯示名稱，與 AI 候選項目的呈現方式一致。
     */
    private fun suggestAiStrategies(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        aiStrategyRegistry.getAllStrategyKeys().forEach { key ->
            builder.suggest(StringArgumentType.escapeIfRequired(key), aiStrategyDisplayNames.resolveDisplayText(key))
        }
        return builder.buildFuture()
    }

    /** 列出執行指令玩家目前所在房間內所有候選成員（含真人與 AI），作為 `kick <target>` 引數的建議。 */
    private fun suggestAllTargets(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestTargets(context, builder) { playerId ->
        memberCandidateResolver.listCandidates(playerId)
    }

    /** 列出執行指令玩家目前所在房間內的 AI 候選成員，作為 `ai strategy <target>` 引數的建議。 */
    private fun suggestAiTargets(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestTargets(context, builder) { playerId ->
        memberCandidateResolver.listAiCandidates(playerId)
    }

    /**
     * 依 [listCandidates] 列出候選成員 tooltip 補全建議的共用邏輯；真人只顯示使用者名稱，AI 額外帶上
     * 序號與策略顯示名稱的 tooltip。
     *
     * 候選清單是一次 suspend 查詢，這裡用 [CompletableFuture] 手動橋接：[SuggestionsBuilder.build]
     * 本身是同步、立即可用的操作，協程完成查詢後直接呼叫它並完成回傳的 future，不需要額外的執行緒
     * 切換或阻塞等待。
     */
    private fun suggestTargets(
        context: CommandContext<ServerCommandSource>,
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
                        ?: Text.literal("?")
                    val tooltip = Text.translatable(
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
