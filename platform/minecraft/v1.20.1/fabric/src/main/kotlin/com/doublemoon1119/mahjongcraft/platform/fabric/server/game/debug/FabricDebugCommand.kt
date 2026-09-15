package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTimerStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTimerUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WIN_AVAILABLE_ID
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiMatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation.FabricDebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.FabricDebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.FabricDebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text.FabricDebugTextCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * 註冊 development-only、op 限定的 `/mahjongcraft debug ...` 指令根節點與各功能測試子指令。
 *
 * 各子指令樹由專責的 family command 建立，本類別只把它們回傳的節點掛在既有位置，不認識那些 handler：
 *
 * - `scenario`：[FabricDebugScenarioCommand]
 * - `dice`、`deal`、`draw`、`discard`、`meld`：[FabricDebugAnimationCommand]
 * - `win`、`showcase`、三種 settlement、`continuing_win`、`win_showcase_override`：
 *   [FabricDebugPresentationCommand]
 * - `hovered_text`：[FabricDebugTextCommand]
 *
 * 尚未分家的 `decision_hud`、`preparation` 與 `match_progression` 仍由本類別直接建立。
 *
 * 各子指令都完全自成一體：在呼叫者面前臨時生成全新的預覽 entity（不呼叫 `assignToTable`、不掛在任何
 * 桌子／對局底下），不觸碰任何房間或對局 use case——呼叫者不需要站在任何桌子附近，也不需要加入房間
 * 或身處進行中的對局，站在隨便一個已載入的世界座標就能直接測試演出效果。臨時 entity 的到期清除見
 * [DebugPreviewEntityLifecycle]。
 *
 * 整組指令樹只在 [MinecraftEnvironment.isDevelopment] 為 `true` 時才註冊——正式打包發布的產物裡整棵
 * debug 指令樹根本沒被註冊過，不是「有指令但用權限藏起來」而已。額外再疊一層 op 權限
 * （`hasPermissionLevel(2)`）當作雙重保險，避免「不小心用非開發建置跑本地測試伺服器」時被一般玩家
 * 誤用。
 *
 * @property minecraftEnvironment 查詢目前是否為開發環境，決定整組指令樹要不要註冊。
 * @property animationCommand 建立自由 entity 動畫子指令樹。
 * @property presentationCommand 建立對局演出預覽子指令樹。
 * @property textCommand 建立訊息排版預覽子指令樹。
 * @property entityLifecycle 保管並驅動臨時 entity 的到期清除。
 */
@Single
class FabricDebugCommand(
    private val minecraftEnvironment: MinecraftEnvironment,
    private val membershipRepository: PlayerMembershipRepository,
    private val gameRepository: GameRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val decisionAvailabilityService: GameDecisionAvailabilityService,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val debugGameScenarioCommand: FabricDebugScenarioCommand,
    private val animationCommand: FabricDebugAnimationCommand,
    private val presentationCommand: FabricDebugPresentationCommand,
    private val textCommand: FabricDebugTextCommand,
    private val entityLifecycle: DebugPreviewEntityLifecycle,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 每位測試者最後一次 HUD 預覽的虛擬 game ID，供 clear 精確停止同一份 client state。 */
    private val debugDecisionGameIds = mutableMapOf<java.util.UUID, String>()

    /** 註冊整組 debug 指令樹；只有開發環境才真的呼叫 `dispatcher.register`，見類別 KDoc。 */
    fun register() {
        if (!minecraftEnvironment.isDevelopment) return
        entityLifecycle.registerTicking()
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(build())
        }
    }

    /** 建立保留既有 literal、權限與子指令順序的完整 debug 指令樹。 */
    internal fun build(): LiteralArgumentBuilder<ServerCommandSource> = literal(MinecraftModMetadata.MOD_ID).then(
        literal(DEBUG_SUBCOMMAND)
            .requires { it.hasPermissionLevel(OP_PERMISSION_LEVEL) }
            .then(debugGameScenarioCommand.build())
            .then(presentationCommand.buildWinCommand())
            .then(presentationCommand.buildShowcaseCommand())
            .then(animationCommand.buildDiceCommand())
            .then(animationCommand.buildDealCommand())
            .then(animationCommand.buildDrawCommand())
            .then(animationCommand.buildDiscardCommand())
            .then(presentationCommand.buildExhaustiveDrawSettlementCommand())
            .then(textCommand.buildHoveredTextCommand())
            .then(presentationCommand.buildWinSettlementCommand())
            .then(presentationCommand.buildMatchSettlementCommand())
            .then(matchProgressionCommand())
            .then(decisionHudCommand())
            .then(animationCommand.buildMeldCommand())
            .then(presentationCommand.buildContinuingWinCommand())
            .then(presentationCommand.buildWinShowcaseOverrideCommand())
            .then(preparationCommand()),
    )

    /** 建立涵蓋操作、立直、分析及 preparation 的 HUD 預覽指令；literal 節點同時提供完整 tab 補全。 */
    private fun decisionHudCommand(): LiteralArgumentBuilder<ServerCommandSource> = DecisionHudPreview.entries.fold(
        literal(DECISION_HUD_SUBCOMMAND).then(literal(CLEAR_ARGUMENT).executes { clearDecisionHud(it.source) }),
    ) { node, preview ->
        node.then(literal(preview.commandName).executes { context -> previewDecisionHud(context.source, preview) })
    }

    /** 直接傳送正式 S2C prompt DTO，讓 client 使用與真實對局相同的 HUD renderer。 */
    private fun previewDecisionHud(source: ServerCommandSource, preview: DecisionHudPreview): Int {
        val player = source.player ?: return 0
        val gameId = Uuid.random().toString()
        debugDecisionGameIds[player.uuid] = gameId
        val analysisTileId = if (preview.isDiscardAnalysis) {
            val direction = player.rotationVector
            MahjongTileEntity(world = source.world).also { tile ->
                tile.uuid = Uuid.random().toJavaUuid()
                tile.refreshPositionAndAngles(
                    player.x + direction.x * 2.0,
                    player.eyeY - 0.5,
                    player.z + direction.z * 2.0,
                    player.yaw,
                    0f,
                )
                tile.tilePose = MahjongTilePose.FACE_UP
                tile.tileAssetKey = "m9"
                source.world.spawnEntity(tile)
                entityLifecycle.schedule(source.world, source.world.time + DEBUG_HUD_DURATION_TICKS, listOf(tile))
            }.uuid.toString()
        } else {
            null
        }
        MahjongChannels.decisionTimerUpdate.sendTo(
            player,
            json,
            DecisionTimerUpdatePayloadDto(
                gameId,
                DecisionTimerStatusDto(
                    phase = preview.phase,
                    baseRemainingMillis = 8_000,
                    reserveRemainingMillis = 15_000,
                    prompt = preview.prompt("debug:$gameId", analysisTileId),
                ),
            ),
        )
        source.sendFeedback({ Text.literal("Decision HUD preview: ${preview.commandName}") }, false)
        return COMMAND_SUCCESS
    }

    /** 清除 HUD 預覽使用的 client timer/prompt。 */
    private fun clearDecisionHud(source: ServerCommandSource): Int {
        val player = source.player ?: return 0
        val gameId = debugDecisionGameIds.remove(player.uuid) ?: return COMMAND_SUCCESS
        MahjongChannels.decisionTimerUpdate.sendTo(
            player,
            json,
            DecisionTimerUpdatePayloadDto(gameId, null),
        )
        return COMMAND_SUCCESS
    }

    /** 建立 development-only round preparation 測試指令樹。 */
    private fun preparationCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(PREPARATION_SUBCOMMAND)
        .then(
            literal(START_ARGUMENT)
                .then(literal(CONFIRM_ARGUMENT).executes { startPreparation(it.source, PreparationPreview.CONFIRM) })
                .then(literal(CHOICE_ARGUMENT).executes { startPreparation(it.source, PreparationPreview.CHOICE) })
                .then(
                    literal(TILES_ARGUMENT).executes { startPreparation(it.source, PreparationPreview.TILES) }
                        .then(
                            argument(MIN_COUNT_ARGUMENT, IntegerArgumentType.integer(1)).then(
                                argument(MAX_COUNT_ARGUMENT, IntegerArgumentType.integer(1)).executes { context ->
                                    startPreparation(
                                        context.source,
                                        PreparationPreview.TILES,
                                        minCount = IntegerArgumentType.getInteger(context, MIN_COUNT_ARGUMENT),
                                        maxCount = IntegerArgumentType.getInteger(context, MAX_COUNT_ARGUMENT),
                                    )
                                },
                            ),
                        ),
                ),
        )
        .then(
            literal(SUBMIT_ARGUMENT)
                .then(literal(CONFIRM_ARGUMENT).executes { submitPreparation(it.source, RoundPreparationSubmission.Confirmed) })
                .then(
                    literal(CHOICE_ARGUMENT).then(
                        argument(OPTION_ID_ARGUMENT, IdentifierArgumentType.identifier())
                            .suggests(::suggestPreparationOptions)
                            .executes { context ->
                                submitPreparation(
                                    context.source,
                                    RoundPreparationSubmission.Choice(
                                        IdentifierArgumentType.getIdentifier(context, OPTION_ID_ARGUMENT).toString(),
                                    ),
                                )
                            },
                    ),
                )
                .then(
                    literal(TILES_ARGUMENT).then(
                        argument(TILE_IDS_ARGUMENT, StringArgumentType.greedyString())
                            .suggests(::suggestPreparationTileIds)
                            .executes { context ->
                                val tileIds = StringArgumentType.getString(context, TILE_IDS_ARGUMENT)
                                    .split(' ')
                                    .filter(String::isNotBlank)
                                    .map { Uuid.parse(it) }
                                    .toSet()
                                submitPreparation(context.source, RoundPreparationSubmission.Tiles(tileIds))
                            },
                    ),
                ),
        )
        .then(literal(TIMEOUT_ARGUMENT).executes(::timeoutPreparation))
        .then(literal(CANCEL_ARGUMENT).executes(::cancelPreparation))

    /** 建立不修改權威對局、只預覽日麻 progression 決策的測試指令樹。 */
    private fun matchProgressionCommand(): LiteralArgumentBuilder<ServerCommandSource> = MatchProgressionPreview.entries.fold(literal(MATCH_PROGRESSION_SUBCOMMAND)) { node, preview ->
        node.then(
            literal(preview.commandName).executes { context ->
                previewMatchProgression(context.source, preview)
            },
        )
    }

    /** 使用執行者目前牌桌的玩家身分建立唯讀情境，回報正式日麻 policy 的決策。 */
    private fun previewMatchProgression(source: ServerCommandSource, preview: MatchProgressionPreview): Int = withPlayerTableSuspend(source) { tableId, _ ->
        val current = gameRepository.getTableState(tableId)
            ?: return@withPlayerTableSuspend "Game not found"
        if (current.players.size != RIICHI_PLAYER_COUNT) {
            return@withPlayerTableSuspend "Match progression previews require four players"
        }
        val config = RiichiRuleConfig(gameLength = preview.gameLength)
        val scores = preview.scores(current.dealerIndex)
        val position = preview.position
        val state = current.copy(
            players = current.players.mapIndexed { index, player -> player.copy(score = scores[index]) },
            config = config,
            prevalentWind = position.prevalentWind,
            roundNumber = position.roundNumber,
            roundPosition = position,
        )
        val completion = RoundCompletionSummary(
            outcomeId = "${MinecraftModMetadata.MOD_ID}:debug_match_progression",
            classification = preview.classification,
            beneficiaryPlayerIds = if (preview.dealerIsBeneficiary) setOf(state.dealerPlayerId) else emptySet(),
            transitionDirective = preview.directive,
            settledScoresByPlayerId = state.players.associate { it.id to it.score },
        )
        val rankedPlayerIds = state.players
            .sortedWith(compareByDescending<MahjongPlayer> { it.score }.thenBy { it.initialSeatIndex })
            .map { it.id }
        val decision = RiichiMatchProgressionPolicy(config).decide(
            MatchProgressionContext(state, completion, rankedPlayerIds),
        )
        "${preview.commandName}: $decision"
    }

    /** 只補全執行者目前 preparation 步驟允許選擇的完整 option ID。 */
    private fun suggestPreparationOptions(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestFromPlayerPreparation(context.source, builder) { input, _, suggestions ->
        val options = (input as? RoundPreparationInputSpec.SingleChoice)?.optionIds.orEmpty()
        options
            .filter { it.startsWith(suggestions.remaining, ignoreCase = true) }
            .forEach(suggestions::suggest)
    }

    /**
     * 只補全執行者目前可選的手牌 UUID；已輸入的 UUID 不重複建議，達到最大張數後停止補全。
     */
    private fun suggestPreparationTileIds(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestFromPlayerPreparation(context.source, builder) { input, _, suggestions ->
        val tileSelection = input as? RoundPreparationInputSpec.TileSelection ?: return@suggestFromPlayerPreparation
        val rawInput = suggestions.remaining
        val endsWithSpace = rawInput.lastOrNull()?.isWhitespace() == true
        val tokens = rawInput.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val completedTokens = if (endsWithSpace) tokens else tokens.dropLast(1)
        if (completedTokens.size >= tileSelection.maxCount) return@suggestFromPlayerPreparation

        val currentToken = if (endsWithSpace) "" else tokens.lastOrNull().orEmpty()
        tileSelection.eligibleTileIds
            .asSequence()
            .map(Uuid::toString)
            .filterNot(completedTokens::contains)
            .filter { it.startsWith(currentToken, ignoreCase = true) }
            .sorted()
            .map { (completedTokens + it).joinToString(" ") }
            .forEach(suggestions::suggest)
    }

    /**
     * 非同步解析執行者所在牌桌的私有 preparation input，讓 Brigadier suggestions 不阻塞伺服器執行緒。
     */
    private fun suggestFromPlayerPreparation(
        source: ServerCommandSource,
        builder: SuggestionsBuilder,
        appendSuggestions: (
            input: RoundPreparationInputSpec,
            pending: PendingRoundPreparation,
            builder: SuggestionsBuilder,
        ) -> Unit,
    ): CompletableFuture<Suggestions> {
        val future = CompletableFuture<Suggestions>()
        val player = source.player
        if (player == null) {
            future.complete(builder.build())
            return future
        }
        scope.launch {
            val playerId = player.uuid.toKotlinUuid()
            val tableId = membershipRepository.getTableId(playerId)
            val pending = tableId?.let { gameRepository.getGame(it)?.pendingRoundPreparation }
            val input = pending?.inputSpecsByPlayerId?.get(playerId)
            if (input != null && playerId !in pending.submissionsByPlayerId) {
                appendSuggestions(input, pending, builder)
            }
            future.complete(builder.build())
        }
        return future
    }

    /**
     * 建立指定形狀的測試 preparation state；[minCount]／[maxCount] 只用在 [PreparationPreview.TILES]，
     * 預設維持既有的 3/3（省略引數時的既有行為不變），可調高 `maxCount` 端對端測試多選確認面板
     * （選中發光→確認面板→送出）走真正的 `SubmitRoundPreparation` 流程與呼叫者真實手牌，不需要真正的
     * 地區麻將規則就先湊出一個 `maxCount > 1` 的情境。
     *
     * 寫入狀態後額外呼叫 [GameDecisionAvailabilityService.reconcile] 並同步結果，比照
     * [GameFlowCoordinator] 內部 `dispatchAndReconcile` 的作法，讓這位玩家真的取得
     * [com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase.ROUND_PREPARATION]
     * 計時器——直接寫入 repository 不會經過 coordinator 的指令派送流程，計時器不會自動產生，逾時、
     * 強制 fallback 等行為在 debug 情境下就永遠測不到。
     */
    private fun startPreparation(source: ServerCommandSource, preview: PreparationPreview, minCount: Int = 3, maxCount: Int = 3): Int = withPlayerTableSuspend(source) { tableId, playerId ->
        val changed = gameRepository.updateGame(tableId) { game ->
            if (game == null) return@updateGame null to false
            val input = when (preview) {
                PreparationPreview.CONFIRM -> RoundPreparationInputSpec.Confirmation
                PreparationPreview.CHOICE -> RoundPreparationInputSpec.SingleChoice(DEBUG_OPTION_IDS)
                PreparationPreview.TILES -> RoundPreparationInputSpec.TileSelection(
                    eligibleTileIds = game.tableState.players.first { it.id == playerId }.hand.allTiles
                        .mapTo(linkedSetOf()) { it.id },
                    minCount = minCount,
                    maxCount = maxCount,
                )
            }
            game.copy(
                pendingRoundPreparation = PendingRoundPreparation(
                    stepId = "${MinecraftModMetadata.MOD_ID}:debug_${preview.name.lowercase()}",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(playerId to input),
                ),
            ) to true
        }
        if (changed) {
            snapshotSynchronizer.syncAll(tableId)
            decisionAvailabilityService.reconcile(tableId)
        }
        "Round preparation ${preview.name.lowercase()} preview ${if (changed) "started" else "failed"}"
    }

    /** 透過正式 coordinator 提交測試 preparation 選擇。 */
    private fun submitPreparation(source: ServerCommandSource, submission: RoundPreparationSubmission): Int = withPlayerTableSuspend(source) { tableId, playerId ->
        gameFlowCoordinator(tableId, playerId, GameCommand.SubmitRoundPreparation(submission))
        "Round preparation submission sent"
    }

    /** 將執行者標記為逾時，讓正式 fallback driver 在下一次推進時代為提交。 */
    private fun timeoutPreparation(context: CommandContext<ServerCommandSource>): Int = withPlayerTableSuspend(context.source) { tableId, playerId ->
        gameRepository.updateGame(tableId) { game ->
            game?.copy(forcedAutoPlayPlayerIds = game.forcedAutoPlayPlayerIds + playerId) to Unit
        }
        gameFlowCoordinator.driveAutomatedPlayers(tableId)
        "Round preparation timeout fallback requested"
    }

    /**
     * 清除 development-only 測試 preparation state。連同呼叫 [GameDecisionAvailabilityService.reconcile]
     * 立即結算 [startPreparation] 建立的計時器，理由同該函式 KDoc——不清掉的話，計時器要等到下一次
     * 剛好觸發 reconcile 的操作才會被結算掉。
     */
    private fun cancelPreparation(context: CommandContext<ServerCommandSource>): Int = withPlayerTableSuspend(context.source) { tableId, _ ->
        gameRepository.updateGame(tableId) { game ->
            game?.copy(pendingRoundPreparation = null) to Unit
        }
        snapshotSynchronizer.syncAll(tableId)
        decisionAvailabilityService.reconcile(tableId)
        "Round preparation preview cancelled"
    }

    /** 在協程中解析玩家與桌子，供需要呼叫 suspend flow service 的 debug 指令使用。 */
    private fun withPlayerTableSuspend(
        source: ServerCommandSource,
        action: suspend (tableId: Uuid, playerId: Uuid) -> String,
    ): Int {
        val player = source.player ?: run {
            source.sendError(Text.literal("This debug subcommand must be run by a player seated at a table"))
            return 0
        }
        scope.launch {
            val playerId = player.uuid.toKotlinUuid()
            val tableId = membershipRepository.getTableId(playerId)
            val message = tableId?.let { action(it, playerId) }
            withContext(dispatchers.main) {
                if (message == null) {
                    source.sendError(Text.literal("You are not seated at any mahjong table"))
                } else {
                    source.sendFeedback({ Text.literal(message) }, true)
                }
            }
        }
        return 1
    }

    /** `/debug decision_hud` 的固定、可補全測試情境。 */
    private enum class DecisionHudPreview(
        val commandName: String,
        val phase: PlayerDecisionPhaseDto = PlayerDecisionPhaseDto.OWN_TURN,
        val isDiscardAnalysis: Boolean = false,
        /** 自己回合摸牌的觸發牌 asset key；只有真的代表「摸到這張牌」的情境才給值，例如 [TIMER] 不給。 */
        val selfDrawTileAssetKey: String? = null,
    ) {
        TIMER("timer"),
        CHI("chi", PlayerDecisionPhaseDto.DISCARD_REACTION),
        PON("pon", PlayerDecisionPhaseDto.DISCARD_REACTION),
        KAN("kan", PlayerDecisionPhaseDto.DISCARD_REACTION),
        RON("ron", PlayerDecisionPhaseDto.DISCARD_REACTION),
        TSUMO("tsumo", selfDrawTileAssetKey = "red_dragon"),
        RIICHI("riichi", selfDrawTileAssetKey = "m1"),
        ANKAN("ankan", selfDrawTileAssetKey = "m9"),
        KYUUSHU("kyuushu", selfDrawTileAssetKey = "east"),
        MIXED("mixed", PlayerDecisionPhaseDto.DISCARD_REACTION),
        DISCARD_ANALYSIS("discard_analysis", isDiscardAnalysis = true),
        DISCARD_FURITEN("discard_furiten", isDiscardAnalysis = true),
        DISCARD_MANY_WAITS("discard_many_waits", isDiscardAnalysis = true),
        DISCARD_NO_YAKU("discard_no_yaku", isDiscardAnalysis = true),
        DISCARD_BELOW_MINIMUM("discard_below_minimum", isDiscardAnalysis = true),
        DISCARD_TSUMO_ONLY("discard_tsumo_only", isDiscardAnalysis = true),
        DISCARD_MIXED_AVAILABILITY("discard_mixed_availability", isDiscardAnalysis = true),
        DISCARD_FURITEN_UNAVAILABLE("discard_furiten_unavailable", isDiscardAnalysis = true),
        PREPARATION_CONFIRM("preparation_confirm", PlayerDecisionPhaseDto.ROUND_PREPARATION),
        PREPARATION_CHOICE("preparation_choice", PlayerDecisionPhaseDto.ROUND_PREPARATION),
        PREPARATION_TILES("preparation_tiles", PlayerDecisionPhaseDto.ROUND_PREPARATION),
        ;

        fun prompt(decisionKey: String, analysisTileId: String?): PlayerDecisionPromptDto = PlayerDecisionPromptDto(
            decisionKey = decisionKey,
            actions = actions(),
            triggerTileAssetKey = if (phase == PlayerDecisionPhaseDto.DISCARD_REACTION) "s5" else selfDrawTileAssetKey,
            triggerPlayerId = if (phase == PlayerDecisionPhaseDto.DISCARD_REACTION) Uuid.random().toString() else null,
            triggerPlayerName = if (phase == PlayerDecisionPhaseDto.DISCARD_REACTION) "AI 1" else null,
            triggerPlayerRelation = if (phase == PlayerDecisionPhaseDto.DISCARD_REACTION) DecisionPlayerRelationDto.LEFT else null,
            triggerActionId = if (phase == PlayerDecisionPhaseDto.DISCARD_REACTION) "mahjongcraft:discard" else null,
            preparation = preparation(),
            discardAnalyses = analysisTileId?.let { listOf(analysis(it)) }.orEmpty(),
        )

        private fun actions(): List<PlayerDecisionActionDto> {
            fun action(id: String, tiles: List<String>, claimedIndex: Int? = null) = PlayerDecisionActionDto(
                token = "$commandName:$id",
                actionId = "mahjongcraft:$id",
                previewTileAssetKeys = tiles,
                claimedTileIndex = claimedIndex,
            )
            fun riichiAction() = PlayerDecisionActionDto(
                token = "mahjongcraft:riichi",
                actionId = "mahjongcraft:riichi",
                previewTileAssetKeys = listOf("m1", "m4", "m7", "p2", "p5", "p8", "s3", "s6", "s9"),
                tileSelection = PlayerDecisionActionTileSelectionDto(
                    eligibleTileIds = List(9) { Uuid.random().toString() },
                    minCount = 1,
                    maxCount = 1,
                ),
            )
            // 只有吃會標出鳴來的那張牌（三張牌花色/數值不同才有辨識意義）；碰／槓牌面彼此完全相同，不標記。
            return when (this) {
                CHI -> listOf(action("chi", listOf("s4", "s5", "s6"), claimedIndex = 1))
                PON -> listOf(action("pon", listOf("p5", "p5", "p5")))
                KAN -> listOf(action("kan_open", listOf("m9", "m9", "m9", "m9")))
                ANKAN -> listOf(action("kan_closed", listOf("m9", "m9", "m9", "m9")))
                RON -> listOf(action("ron", listOf("s5")))
                TSUMO -> listOf(action("tsumo", listOf("red_dragon")))
                RIICHI -> listOf(riichiAction())
                KYUUSHU -> listOf(
                    action(
                        "kyuushu_kyuuhai",
                        listOf("m1", "m9", "p1", "p9", "s1", "s9", "east", "south", "west", "north", "white_dragon", "green_dragon"),
                    ),
                )
                MIXED -> listOf(
                    action("chi", listOf("s4", "s5", "s6"), claimedIndex = 1),
                    action("pon", listOf("s5", "s5", "s5")),
                    action("kan_open", listOf("s5", "s5", "s5", "s5")),
                    action("ron", listOf("s5")),
                    riichiAction(),
                    action("pass", emptyList()),
                )
                else -> emptyList()
            }
        }

        private fun preparation(): RoundPreparationPromptDto? = when (this) {
            PREPARATION_CONFIRM -> RoundPreparationPromptDto.Confirmation
            PREPARATION_CHOICE -> RoundPreparationPromptDto.SingleChoice(listOf("mahjongcraft:alpha", "mahjongcraft:beta", "mahjongcraft:gamma"))
            PREPARATION_TILES -> RoundPreparationPromptDto.TileSelection(
                eligibleTileIds = List(14) { Uuid.random().toString() },
                eligibleTileAssetKeys = listOf(
                    "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "p1", "p2", "p3", "p4", "p5",
                ),
                minCount = 3,
                maxCount = 3,
            )
            else -> null
        }

        private fun analysis(discardTileId: String): DiscardReadinessAnalysisDto {
            val assets = if (this == DISCARD_MANY_WAITS) {
                listOf("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "p1", "p9", "s1", "s9")
            } else {
                listOf("m2", "m5", "m8")
            }
            val availability = when (this) {
                DISCARD_NO_YAKU, DISCARD_FURITEN_UNAVAILABLE -> "mahjongcraft:win_no_yaku"
                DISCARD_BELOW_MINIMUM -> "mahjongcraft:win_below_minimum"
                DISCARD_TSUMO_ONLY -> "mahjongcraft:win_tsumo_only"
                else -> WIN_AVAILABLE_ID
            }
            return DiscardReadinessAnalysisDto(
                discardTileId,
                assets.mapIndexed { index, asset ->
                    WaitingTileAvailabilityDto(
                        asset,
                        (3 - index).coerceAtLeast(0),
                        if (this == DISCARD_MIXED_AVAILABILITY) {
                            MIXED_AVAILABILITY_CYCLE[index % MIXED_AVAILABILITY_CYCLE.size]
                        } else {
                            availability
                        },
                    )
                },
                if (this == DISCARD_FURITEN || this == DISCARD_FURITEN_UNAVAILABLE) "mahjongcraft:discard_furiten" else null,
            )
        }

        private companion object {
            /** [DISCARD_MIXED_AVAILABILITY] 逐張輪流展示的和牌可用性命名字串。 */
            val MIXED_AVAILABILITY_CYCLE = listOf(
                WIN_AVAILABLE_ID,
                "mahjongcraft:win_tsumo_only",
                "mahjongcraft:win_no_yaku",
                "mahjongcraft:win_below_minimum",
            )
        }
    }

    private companion object {
        const val OP_PERMISSION_LEVEL: Int = 2
        const val DEBUG_SUBCOMMAND: String = "debug"
        const val PREPARATION_SUBCOMMAND: String = "preparation"
        const val START_ARGUMENT: String = "start"
        const val SUBMIT_ARGUMENT: String = "submit"
        const val CONFIRM_ARGUMENT: String = "confirm"
        const val CHOICE_ARGUMENT: String = "choice"
        const val TILES_ARGUMENT: String = "tiles"
        const val TIMEOUT_ARGUMENT: String = "timeout"
        const val CANCEL_ARGUMENT: String = "cancel"
        const val OPTION_ID_ARGUMENT: String = "option_id"
        const val TILE_IDS_ARGUMENT: String = "tile_ids"
        const val CLEAR_ARGUMENT: String = "clear"
        const val MATCH_PROGRESSION_SUBCOMMAND: String = "match_progression"
        const val DECISION_HUD_SUBCOMMAND: String = "decision_hud"
        const val DEBUG_HUD_DURATION_TICKS: Long = 20L * 30L
        const val MIN_COUNT_ARGUMENT: String = "min_count"
        const val MAX_COUNT_ARGUMENT: String = "max_count"
        val DEBUG_OPTION_IDS: List<String> = listOf("mahjongcraft:alpha", "mahjongcraft:beta", "mahjongcraft:gamma")

        /** 內建日麻 progression debug 情境固定使用的玩家數。 */
        const val RIICHI_PLAYER_COUNT: Int = 4

        /** Development preparation 測試步驟種類。 */
        enum class PreparationPreview {
            CONFIRM,
            CHOICE,
            TILES,
        }

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1
    }

    /** Development-only 日麻終局 progression 預覽情境。 */
    private enum class MatchProgressionPreview(
        /** 指令 literal 名稱。 */
        val commandName: String,
        /** 此情境使用的對局長度。 */
        val gameLength: RiichiGameLength,
        /** 此情境的權威局位。 */
        val position: MatchRoundPosition,
        /** 本局莊家推進決策。 */
        val directive: RoundTransitionDirective,
        /** 本局結果分類。 */
        val classification: RoundCompletionClassification = RoundCompletionClassification.WIN,
        /** 此情境是否把莊家列為本局權威得利者。 */
        val dealerIsBeneficiary: Boolean = false,
    ) {
        /** 東四未達返點，預期南入。 */
        EAST_OVERTIME(
            "east_overtime",
            RiichiGameLength.East,
            MatchRoundPosition(3, Wind.EAST, 4),
            RoundTransitionDirective.ADVANCE_DEALER,
        ),

        /** 東四已達返點，預期終局。 */
        EAST_END(
            "east_end",
            RiichiGameLength.East,
            MatchRoundPosition(3, Wind.EAST, 4),
            RoundTransitionDirective.ADVANCE_DEALER,
        ),

        /** 南四未達返點，預期西入。 */
        WEST_OVERTIME(
            "west_overtime",
            RiichiGameLength.TwoWinds,
            MatchRoundPosition(7, Wind.SOUTH, 4),
            RoundTransitionDirective.ADVANCE_DEALER,
        ),

        /** 延長局達返點，預期驟死終局。 */
        EXTRA_SUDDEN_DEATH(
            "extra_sudden_death",
            RiichiGameLength.East,
            MatchRoundPosition(4, Wind.SOUTH, 1, MatchRoundPhase.EXTRA),
            RoundTransitionDirective.ADVANCE_DEALER,
        ),

        /** 延長最後局未達返點，預期強制終局。 */
        EXTRA_LIMIT(
            "extra_limit",
            RiichiGameLength.East,
            MatchRoundPosition(7, Wind.SOUTH, 4, MatchRoundPhase.EXTRA),
            RoundTransitionDirective.ADVANCE_DEALER,
        ),

        /** 最後莊家胡牌且達成第一名返點，預期和了止め。 */
        DEALER_AGARI_YAME(
            "dealer_agari_yame",
            RiichiGameLength.East,
            MatchRoundPosition(3, Wind.EAST, 4),
            RoundTransitionDirective.REPEAT_DEALER,
            dealerIsBeneficiary = true,
        ),

        /** 最後莊家流局聽牌且達成第一名返點，預期聽牌止め。 */
        DEALER_TENPAI_YAME(
            "dealer_tenpai_yame",
            RiichiGameLength.East,
            MatchRoundPosition(3, Wind.EAST, 4),
            RoundTransitionDirective.REPEAT_DEALER,
            RoundCompletionClassification.EXHAUSTIVE_DRAW,
            dealerIsBeneficiary = true,
        ),

        /** 任一玩家低於擊飛門檻，預期立即終局。 */
        BUST(
            "bust",
            RiichiGameLength.East,
            MatchRoundPosition(1, Wind.EAST, 2),
            RoundTransitionDirective.ADVANCE_DEALER,
        ),
        ;

        /** 依情境與目前莊家索引建立四位玩家的測試分數。 */
        fun scores(dealerIndex: Int): List<Int> = when (this) {
            EAST_OVERTIME, WEST_OVERTIME, EXTRA_LIMIT -> listOf(29_900, 25_100, 25_000, 20_000)
            EAST_END, EXTRA_SUDDEN_DEATH -> listOf(30_000, 25_000, 25_000, 20_000)
            DEALER_AGARI_YAME, DEALER_TENPAI_YAME -> List(RIICHI_PLAYER_COUNT) { index ->
                if (index == dealerIndex) 30_000 else 23_333
            }
            BUST -> listOf(50_000, 30_100, 20_000, -100)
        }
    }
}
