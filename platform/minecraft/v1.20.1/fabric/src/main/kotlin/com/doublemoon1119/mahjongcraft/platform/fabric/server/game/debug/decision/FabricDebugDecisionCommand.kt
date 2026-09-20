package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
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
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * 建立 `/mahjongcraft debug` 底下的玩家決策互動預覽子指令：`decision_hud` 與 `preparation`。
 *
 * `decision_hud` 只把固定的 prompt DTO 與計時器狀態直接送給呼叫者的 client，不建立任何對局、也不經過
 * coordinator；需要分析面板的情境會在玩家眼前生成一張臨時牌當作分析目標，由
 * [DebugPreviewEntityLifecycle] 在預覽時間結束後清除。每位測試者最後一次預覽使用的虛擬 game ID 保存在
 * [debugDecisionGameIds]，讓 `clear` 只停止同一位測試者自己那份 client state。
 *
 * `preparation` 相反：它直接寫入真實牌桌的 pending preparation 狀態，再呼叫
 * [GameDecisionAvailabilityService.reconcile] 並同步結果，讓計時器、逾時與強制 fallback 都走正式路徑，
 * 因此要求呼叫者已入座。這是開發專用的權威狀態注入，只在 development build 註冊。
 *
 * @property gameRepository 讀寫 preparation 測試情境所需的牌桌狀態。
 * @property gameFlowCoordinator 送出 preparation 的正式指令。
 * @property decisionAvailabilityService 重新計算 preparation 的決策可用性與計時器。
 * @property snapshotSynchronizer 同步 reconcile 之後的快照。
 * @property membershipRepository 供 tab 補全解析呼叫者目前入座的桌子。
 * @property scope 執行 tab 補全所需的非同步查詢。
 * @property playerTableScope 解析呼叫者目前入座的桌子。
 * @property entityLifecycle 排定分析預覽牌的到期清除。
 * @property json 序列化送往 client 的 HUD payload。
 */
@Single
class FabricDebugDecisionCommand(
    private val gameRepository: GameRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val decisionAvailabilityService: GameDecisionAvailabilityService,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val membershipRepository: PlayerMembershipRepository,
    private val scope: AppCoroutineScope,
    private val playerTableScope: DebugPlayerTableScope,
    private val entityLifecycle: DebugPreviewEntityLifecycle,
    @Provided private val json: Json,
) {
    /** 每位測試者最後一次 HUD 預覽的虛擬 game ID，供 clear 精確停止同一份 client state。 */
    private val debugDecisionGameIds = mutableMapOf<UUID, String>()

    /** 建立涵蓋操作、立直、分析及 preparation 的 HUD 預覽指令；literal 節點同時提供完整 tab 補全。 */
    fun buildDecisionHudCommand(): LiteralArgumentBuilder<ServerCommandSource> = DecisionHudPreview.entries.fold(
        literal(DECISION_HUD_SUBCOMMAND).then(literal(CLEAR_ARGUMENT).executes { clearDecisionHud(it.source) }),
    ) { node, preview ->
        node.then(literal(preview.commandName).executes { context -> previewDecisionHud(context.source, preview) })
    }

    /** 直接傳送正式 S2C prompt DTO，讓 client 使用與真實對局相同的 HUD renderer。 */
    private fun previewDecisionHud(source: ServerCommandSource, preview: DecisionHudPreview): Int {
        val player = source.player ?: return COMMAND_FAILURE
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
        val player = source.player ?: return COMMAND_FAILURE
        val gameId = debugDecisionGameIds.remove(player.uuid) ?: return COMMAND_SUCCESS
        MahjongChannels.decisionTimerUpdate.sendTo(
            player,
            json,
            DecisionTimerUpdatePayloadDto(gameId, null),
        )
        return COMMAND_SUCCESS
    }

    /** 建立 development-only round preparation 測試指令樹。 */
    fun buildPreparationCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(PREPARATION_SUBCOMMAND)
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
     * [PlayerDecisionPhase.ROUND_PREPARATION]
     * 計時器——直接寫入 repository 不會經過 coordinator 的指令派送流程，計時器不會自動產生，逾時、
     * 強制 fallback 等行為在 debug 情境下就永遠測不到。
     */
    private fun startPreparation(source: ServerCommandSource, preview: PreparationPreview, minCount: Int = 3, maxCount: Int = 3): Int = playerTableScope.runSuspending(source) { tableId, playerId ->
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
    private fun submitPreparation(source: ServerCommandSource, submission: RoundPreparationSubmission): Int = playerTableScope.runSuspending(source) { tableId, playerId ->
        gameFlowCoordinator(tableId, playerId, GameCommand.SubmitRoundPreparation(submission))
        "Round preparation submission sent"
    }

    /** 將執行者標記為逾時，讓正式 fallback driver 在下一次推進時代為提交。 */
    private fun timeoutPreparation(context: CommandContext<ServerCommandSource>): Int = playerTableScope.runSuspending(context.source) { tableId, playerId ->
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
    private fun cancelPreparation(context: CommandContext<ServerCommandSource>): Int = playerTableScope.runSuspending(context.source) { tableId, _ ->
        gameRepository.updateGame(tableId) { game ->
            game?.copy(pendingRoundPreparation = null) to Unit
        }
        snapshotSynchronizer.syncAll(tableId)
        decisionAvailabilityService.reconcile(tableId)
        "Round preparation preview cancelled"
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
            // 預覽情境使用日麻的動作與振聽用語。
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
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
        /** `preparation` 子指令 literal。 */
        const val PREPARATION_SUBCOMMAND: String = "preparation"

        /** 建立測試 preparation 的 literal。 */
        const val START_ARGUMENT: String = "start"

        /** 送出 preparation 的 literal。 */
        const val SUBMIT_ARGUMENT: String = "submit"

        /** 單純確認步驟的 literal。 */
        const val CONFIRM_ARGUMENT: String = "confirm"

        /** 單選步驟的 literal。 */
        const val CHOICE_ARGUMENT: String = "choice"

        /** 選牌步驟的 literal。 */
        const val TILES_ARGUMENT: String = "tiles"

        /** 逾時 fallback 的 literal。 */
        const val TIMEOUT_ARGUMENT: String = "timeout"

        /** 取消 preparation 的 literal。 */
        const val CANCEL_ARGUMENT: String = "cancel"

        /** 單選 option ID 引數名稱。 */
        const val OPTION_ID_ARGUMENT: String = "option_id"

        /** 選牌 UUID 清單引數名稱。 */
        const val TILE_IDS_ARGUMENT: String = "tile_ids"

        /** 清除 HUD 預覽的 literal。 */
        const val CLEAR_ARGUMENT: String = "clear"

        /** `decision_hud` 子指令 literal。 */
        const val DECISION_HUD_SUBCOMMAND: String = "decision_hud"

        /** HUD 預覽臨時牌的存活時間。 */
        const val DEBUG_HUD_DURATION_TICKS: Long = 20L * 30L

        /** 選牌步驟最少張數引數名稱。 */
        const val MIN_COUNT_ARGUMENT: String = "min_count"

        /** 選牌步驟最多張數引數名稱。 */
        const val MAX_COUNT_ARGUMENT: String = "max_count"

        /** 單選步驟使用的固定 option ID。 */
        val DEBUG_OPTION_IDS: List<String> = listOf("mahjongcraft:alpha", "mahjongcraft:beta", "mahjongcraft:gamma")

        /** Development preparation 測試步驟種類。 */
        enum class PreparationPreview {
            CONFIRM,
            CHOICE,
            TILES,
        }

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
