package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementPresentationRequestFactory
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
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
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationCinematicTimeline
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildMatchResultChatText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildRoundResultChatText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configShowMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.normalizedTileAssetKey
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.entity.Entity
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * `/mahjongcraft debug ...`：常駐、op 限定的動畫測試指令群組，涵蓋所有既有呈現層動畫與胡牌慶祝演出的
 * 預覽版本，讓這些動畫不必真的湊到對應遊戲情境（真的自摸、真的湊到四家開局等）才能觸發，也不需要
 * 每次臨時寫一個測試指令、commit 前再刪掉。
 *
 * 每個子指令都完全自成一體：在呼叫者面前臨時生成幾個全新的 [MahjongTileEntity]／[MahjongDiceEntity]
 * （不呼叫 `assignToTable`、不掛在任何桌子／對局底下），直接對這些臨時 entity 重播對應的動畫排程邏輯
 * （[TileAnimationSteps]，跟正式桌子綁定的 presenter 共用同一份），動畫播完後自行清除這些臨時 entity。
 * 完全不觸碰 `GameRepository`／`PlayerMembershipRepository`／任何房間或對局 use case——呼叫者不需要
 * 站在任何桌子附近，也不需要加入房間或身處進行中的對局，站在隨便一個已載入的世界座標就能直接測試
 * 演出效果，且每次呼叫都是全新一批 entity，可以無限重複呼叫。
 *
 * 牌面（[MahjongTileEntity.tileAssetKey]）可由呼叫者透過每個子指令下方可選的 `tile` 引數指定——這些
 * 臨時 entity 刻意不呼叫 `assignToTable`（[MahjongTileEntity.managedByGame] 維持 `false`），client 端
 * `MahjongTileEntityRenderer` 對「自由放置」的牌一律直接讀 entity 自身的 `tileAssetKey`，不像牌局
 * 管理中的牌那樣需要透過 `TableStateSnapshot` 可見性快照才能解析牌面——這正是既有「自由放置裝飾牌」
 * 使用的同一條路徑，因此這裡不需要真正的對局資料也能正確顯示指定的牌面。省略 `tile` 引數時使用隨機
 * 抽出的內建牌面。
 *
 * 整組指令樹只在 [MinecraftEnvironment.isDevelopment] 為 `true` 時才註冊——正式打包發布的產物裡整棵
 * debug 指令樹根本沒被註冊過，不是「有指令但用權限藏起來」而已。額外再疊一層 op 權限
 * （`hasPermissionLevel(2)`）當作雙重保險，避免「不小心用非開發建置跑本地測試伺服器」時被一般玩家
 * 誤用。
 *
 * @property minecraftEnvironment 查詢目前是否為開發環境，決定整組指令樹要不要註冊。
 * @property effectScheduler 排定 `win` 胡牌慶祝演出的降臨特效。
 */
@Single
class FabricDebugAnimationCommand(
    private val minecraftEnvironment: MinecraftEnvironment,
    private val debugWinRoundContinuationState: DebugWinRoundContinuationState,
    private val debugWinShowcaseOverride: DebugWinShowcaseOverride,
    private val membershipRepository: PlayerMembershipRepository,
    private val gameRepository: GameRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val effectScheduler: FabricWinCelebrationEffectScheduler,
    private val showcaseScheduler: FabricWinCelebrationShowcaseScheduler,
    private val showcaseRegistry: WinCelebrationShowcaseRegistry,
    private val exhaustiveDrawSettlementScheduler: FabricExhaustiveDrawSettlementPresentationScheduler,
    private val winSettlementScheduler: FabricWinSettlementPresentationScheduler,
    private val matchSettlementScheduler: FabricMatchSettlementPresentationScheduler,
    private val exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val serverConfigManager: FabricServerConfigManager,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 每個子指令自行排定的清除任務，見 [scheduleCleanup] KDoc。 */
    private val cleanupTasks = ConcurrentLinkedQueue<CleanupTask>()

    /** 註冊整組 debug 指令樹；只有開發環境才真的呼叫 `dispatcher.register`，見類別 KDoc。 */
    fun register() {
        if (!minecraftEnvironment.isDevelopment) return
        registerCleanupTicking()
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID).then(
                    literal(DEBUG_SUBCOMMAND)
                        .requires { it.hasPermissionLevel(OP_PERMISSION_LEVEL) }
                        .then(
                            literal(WIN_SUBCOMMAND)
                                .then(withOptionalTileArgument(literal(TSUMO_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = true, tileArg) })
                                .then(withOptionalTileArgument(literal(RON_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = false, tileArg) }),
                        )
                        .then(
                            literal(SHOWCASE_SUBCOMMAND)
                                .then(
                                    withOptionalCueArgument(literal(TSUMO_ARGUMENT), allowMultiple = false) { source, cue ->
                                        previewShowcase(source, isTsumo = true, listOf(cue ?: DEFAULT_SHOWCASE_CUE))
                                    },
                                )
                                .then(
                                    withOptionalCueArgument(literal(RON_ARGUMENT), allowMultiple = true) { source, cues ->
                                        previewShowcase(source, isTsumo = false, (cues ?: DEFAULT_SHOWCASE_CUE).split(",").take(3))
                                    },
                                )
                                .then(
                                    literal(MULTI_RON_ARGUMENT).then(
                                        argument(WINNER_COUNT_ARGUMENT, IntegerArgumentType.integer(2, 3))
                                            .executes { context ->
                                                val count = IntegerArgumentType.getInteger(context, WINNER_COUNT_ARGUMENT)
                                                previewShowcase(context.source, isTsumo = false, List(count) { DEFAULT_SHOWCASE_CUE })
                                            }
                                            .then(
                                                argument(CUE_ARGUMENT, StringArgumentType.greedyString())
                                                    .suggests(::suggestShowcaseCueList)
                                                    .executes { context ->
                                                        val count = IntegerArgumentType.getInteger(context, WINNER_COUNT_ARGUMENT)
                                                        val supplied = StringArgumentType.getString(context, CUE_ARGUMENT).split(",").filter(String::isNotBlank)
                                                        val cues = expandShowcaseCues(supplied, count, DEFAULT_SHOWCASE_CUE)
                                                        previewShowcase(context.source, isTsumo = false, cues)
                                                    },
                                            ),
                                    ),
                                )
                                .then(
                                    literal(PHASE_ARGUMENT).then(
                                        argument(PHASE_NAME_ARGUMENT, StringArgumentType.word())
                                            .suggests(::suggestShowcasePhases)
                                            .executes { context -> previewShowcasePhase(context.source, StringArgumentType.getString(context, PHASE_NAME_ARGUMENT), DEFAULT_SHOWCASE_CUE) }
                                            .then(
                                                argument(CUE_ARGUMENT, IdentifierArgumentType.identifier())
                                                    .suggests(::suggestSingleShowcaseCue)
                                                    .executes { context ->
                                                        previewShowcasePhase(
                                                            context.source,
                                                            StringArgumentType.getString(context, PHASE_NAME_ARGUMENT),
                                                            IdentifierArgumentType.getIdentifier(context, CUE_ARGUMENT).toString(),
                                                        )
                                                    },
                                            ),
                                    ),
                                ),
                        )
                        .then(literal(DICE_SUBCOMMAND).executes { ctx -> previewDice(ctx.source) })
                        .then(withOptionalTileArgument(literal(DEAL_SUBCOMMAND), ::previewDeal))
                        .then(withOptionalTileArgument(literal(DRAW_SUBCOMMAND), ::previewDraw))
                        .then(withOptionalTileArgument(literal(DISCARD_SUBCOMMAND), ::previewDiscard))
                        .then(
                            literal(EXHAUSTIVE_DRAW_SETTLEMENT_SUBCOMMAND)
                                .then(
                                    literal(NORMAL_ARGUMENT).then(
                                        argument(TENPAI_COUNT_ARGUMENT, IntegerArgumentType.integer(0, 4)).executes { context ->
                                            previewSettlement(
                                                context.source,
                                                RiichiExhaustiveDrawReason.Normal.id,
                                                IntegerArgumentType.getInteger(context, TENPAI_COUNT_ARGUMENT),
                                            )
                                        }.then(
                                            argument(PLAYER_COUNT_ARGUMENT, IntegerArgumentType.integer(2, 4)).executes { context ->
                                                previewSettlement(
                                                    context.source,
                                                    RiichiExhaustiveDrawReason.Normal.id,
                                                    IntegerArgumentType.getInteger(context, TENPAI_COUNT_ARGUMENT),
                                                    playerCount = IntegerArgumentType.getInteger(context, PLAYER_COUNT_ARGUMENT),
                                                )
                                            },
                                        ),
                                    ),
                                )
                                .then(literal(KYUUSHU_ARGUMENT).executes { context -> previewSettlement(context.source, RiichiExhaustiveDrawReason.KyuushuKyuuhai.id, 1, proof = true) })
                                .then(
                                    literal(SCORE_ARGUMENT)
                                        .executes { context -> previewSettlement(context.source, RiichiExhaustiveDrawReason.Normal.id, 0, scoreDelta = DEFAULT_SCORE_DELTA) }
                                        .then(
                                            argument(SCORE_DELTA_ARGUMENT, IntegerArgumentType.integer(1)).executes { context ->
                                                previewSettlement(
                                                    context.source,
                                                    RiichiExhaustiveDrawReason.Normal.id,
                                                    0,
                                                    scoreDelta = IntegerArgumentType.getInteger(context, SCORE_DELTA_ARGUMENT),
                                                )
                                            },
                                        ),
                                )
                                .then(
                                    literal(ABORTIVE_ARGUMENT).then(
                                        argument(REASON_ARGUMENT, IdentifierArgumentType.identifier())
                                            .suggests(::suggestAbortiveDrawReasons)
                                            .executes { context ->
                                                previewSettlement(context.source, IdentifierArgumentType.getIdentifier(context, REASON_ARGUMENT).toString(), 0)
                                            },
                                    ),
                                ),
                        )
                        .then(
                            literal(HOVERED_TEXT_SUBCOMMAND)
                                .then(
                                    literal(EXHAUSTIVE_DRAW_SETTLEMENT_ARGUMENT)
                                        .executes { context -> previewExhaustiveDrawSettlementHoveredText(context.source) },
                                )
                                .then(
                                    literal(WIN_SETTLEMENT_SUBCOMMAND)
                                        .executes { context -> previewWinSettlementHoveredText(context.source) },
                                )
                                .then(
                                    literal(MATCH_SETTLEMENT_SUBCOMMAND)
                                        .executes { context -> previewMatchSettlementHoveredText(context.source) },
                                )
                                .then(literal(GAME_CREATED_LOCATION_ARGUMENT).executes { context -> previewGameCreatedLocationHoveredText(context.source) })
                                .then(
                                    literal(GAME_CONFIG_ARGUMENT)
                                        .then(literal(SHOW_ARGUMENT).executes { context -> previewGameConfigHoveredText(context.source, SHOW_ARGUMENT) })
                                        .then(literal(CHANGED_ARGUMENT).executes { context -> previewGameConfigHoveredText(context.source, CHANGED_ARGUMENT) })
                                        .then(literal(UNCHANGED_ARGUMENT).executes { context -> previewGameConfigHoveredText(context.source, UNCHANGED_ARGUMENT) }),
                                )
                                .then(literal(SERVER_CONFIG_ARGUMENT).executes { context -> previewServerConfigHoveredText(context.source) }),
                        )
                        .then(
                            literal(WIN_SETTLEMENT_SUBCOMMAND)
                                .then(literal(TSUMO_ARGUMENT).executes { context -> previewWinSettlement(context.source, WinSettlementPreview.TSUMO, 1) })
                                .then(
                                    literal(RON_ARGUMENT)
                                        .executes { context -> previewWinSettlement(context.source, WinSettlementPreview.RON, 1) }
                                        .then(
                                            argument(WINNER_COUNT_ARGUMENT, IntegerArgumentType.integer(1, 3)).executes { context ->
                                                previewWinSettlement(
                                                    context.source,
                                                    WinSettlementPreview.RON,
                                                    IntegerArgumentType.getInteger(context, WINNER_COUNT_ARGUMENT),
                                                )
                                            },
                                        ),
                                )
                                .then(literal(YAKUMAN_ARGUMENT).executes { context -> previewWinSettlement(context.source, WinSettlementPreview.YAKUMAN, 1) })
                                .then(literal(NAGASHI_ARGUMENT).executes { context -> previewWinSettlement(context.source, WinSettlementPreview.NAGASHI, 1) }),
                        )
                        .then(
                            literal(MATCH_SETTLEMENT_SUBCOMMAND)
                                .executes { context -> previewMatchSettlement(context.source, 4) }
                                .then(
                                    argument(PLAYER_COUNT_ARGUMENT, IntegerArgumentType.integer(2, 4))
                                        .executes { context ->
                                            previewMatchSettlement(
                                                context.source,
                                                IntegerArgumentType.getInteger(context, PLAYER_COUNT_ARGUMENT),
                                            )
                                        },
                                ),
                        )
                        .then(matchProgressionCommand())
                        .then(
                            literal(MELD_SUBCOMMAND)
                                .then(withOptionalTileArgument(literal(CHI_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.CHI, tileArg) })
                                .then(withOptionalTileArgument(literal(PON_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.PON, tileArg) })
                                .then(withOptionalTileArgument(literal(KAN_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.OPEN_KAN, tileArg) }),
                        )
                        .then(
                            DebugWinRoundContinuationMode.entries.fold(
                                literal(CONTINUING_WIN_SUBCOMMAND)
                                    .executes { context -> reportContinuingWinMode(context.source) },
                            ) { node, mode ->
                                node.then(
                                    literal(mode.name.lowercase()).executes { context ->
                                        setContinuingWinMode(context.source, mode)
                                    },
                                )
                            },
                        )
                        .then(
                            withOptionalCueArgument(
                                literal(WIN_SHOWCASE_OVERRIDE_SUBCOMMAND).then(
                                    literal(CLEAR_ARGUMENT).executes { context -> clearWinShowcaseOverride(context.source) },
                                ),
                                allowMultiple = false,
                            ) { source, cue -> armWinShowcaseOverride(source, cue) },
                        )
                        .then(preparationCommand()),
                ),
            )
        }
    }

    /** 建立 development-only round preparation 測試指令樹。 */
    private fun preparationCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(PREPARATION_SUBCOMMAND)
        .then(
            literal(START_ARGUMENT)
                .then(literal(CONFIRM_ARGUMENT).executes { startPreparation(it.source, PreparationPreview.CONFIRM) })
                .then(literal(CHOICE_ARGUMENT).executes { startPreparation(it.source, PreparationPreview.CHOICE) })
                .then(literal(TILES_ARGUMENT).executes { startPreparation(it.source, PreparationPreview.TILES) }),
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

    /** 建立指定形狀的測試 preparation state。 */
    private fun startPreparation(source: ServerCommandSource, preview: PreparationPreview): Int = withPlayerTableSuspend(source) { tableId, playerId ->
        val changed = gameRepository.updateGame(tableId) { game ->
            if (game == null) return@updateGame null to false
            val input = when (preview) {
                PreparationPreview.CONFIRM -> RoundPreparationInputSpec.Confirmation
                PreparationPreview.CHOICE -> RoundPreparationInputSpec.SingleChoice(DEBUG_OPTION_IDS)
                PreparationPreview.TILES -> RoundPreparationInputSpec.TileSelection(
                    eligibleTileIds = game.tableState.players.first { it.id == playerId }.hand.allTiles
                        .mapTo(linkedSetOf()) { it.id },
                    minCount = 3,
                    maxCount = 3,
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
        if (changed) snapshotSynchronizer.syncAll(tableId)
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

    /** 清除 development-only 測試 preparation state。 */
    private fun cancelPreparation(context: CommandContext<ServerCommandSource>): Int = withPlayerTableSuspend(context.source) { tableId, _ ->
        gameRepository.updateGame(tableId) { game ->
            game?.copy(pendingRoundPreparation = null) to Unit
        }
        snapshotSynchronizer.syncAll(tableId)
        "Round preparation preview cancelled"
    }

    /**
     * 切換**執行者目前所在那一桌**的中途胡牌（胡牌後本局繼續）開發用模式，見
     * [DebugWinRoundContinuationResolver]。
     *
     * 跟這個類別的其他子指令不同，這一個**不**臨時生成任何 entity、也不預覽任何動畫——它只翻一個開關，
     * 之後在那一桌正常胡牌就會走中途胡牌流程。中途胡牌牽涉的是整條遊戲流程（已完成玩家被跳過、演出走
     * 獨立時間軸不擋其他玩家、換局延後），本來就不可能靠臨時 entity 預覽出來。
     *
     * 刻意以桌為範圍而非全伺服器：同一個開發伺服器上可能同時有多桌，全域開關會讓其他桌莫名其妙進入
     * 中途胡牌流程，而且開啟後會一直有效到有人記得手動關掉。因此執行者必須已經坐在某一桌上。
     */
    private fun setContinuingWinMode(source: ServerCommandSource, mode: DebugWinRoundContinuationMode): Int = withPlayerTable(source) { tableId ->
        debugWinRoundContinuationState.setMode(tableId, mode)
        "Continuing-win debug mode for table $tableId set to ${mode.name}"
    }

    /** 回報執行者目前所在那一桌的中途胡牌模式，並一併列出全伺服器其他仍有設定的桌子。 */
    private fun reportContinuingWinMode(source: ServerCommandSource): Int = withPlayerTable(source) { tableId ->
        buildString {
            append("Continuing-win debug mode for table $tableId is ${debugWinRoundContinuationState.modeFor(tableId).name}")
            val others = debugWinRoundContinuationState.activeTableIds() - tableId
            if (others.isNotEmpty()) append("; also active on: ${others.joinToString()}")
            val armed = debugWinShowcaseOverride.armedTableIds()
            if (armed.isNotEmpty()) append("; showcase override armed on: ${armed.joinToString()}")
        }
    }

    /**
     * 替執行者目前所在那一桌武裝一次性的役滿 showcase 覆寫，見 [DebugWinShowcaseOverride]——只覆寫
     * 呈現用的 cue，動不到權威役種、番數、分數或結算結果。
     */
    private fun armWinShowcaseOverride(source: ServerCommandSource, cueKey: String?): Int = withPlayerTable(source) { tableId ->
        val resolvedCue = cueKey ?: DEFAULT_SHOWCASE_CUE
        if (debugWinShowcaseOverride.arm(tableId, resolvedCue)) {
            "Next win on table $tableId will play showcase cue $resolvedCue (presentation only, one-shot)"
        } else {
            "Showcase override is available in development environments only"
        }
    }

    /** 解除執行者目前所在那一桌尚未用掉的 showcase 覆寫武裝。 */
    private fun clearWinShowcaseOverride(source: ServerCommandSource): Int = withPlayerTable(source) { tableId ->
        debugWinShowcaseOverride.clear(tableId)
        "Showcase override for table $tableId cleared"
    }

    /**
     * 解析執行者目前占用的麻將桌後在伺服器主執行緒上執行 [action]，並把它回傳的訊息回饋給執行者。
     *
     * 桌歸屬查詢是 suspend 的，而 Brigadier 的執行是同步的，因此結果透過協程非同步回饋——比照
     * `FabricGameCommand` 橋接 suspend 查詢與 Brigadier 同步 API 的既有做法。
     */
    private fun withPlayerTable(source: ServerCommandSource, action: (Uuid) -> String): Int {
        val player = source.player ?: run {
            source.sendError(Text.literal("This debug subcommand must be run by a player seated at a table"))
            return 0
        }
        scope.launch {
            val tableId = membershipRepository.getTableId(player.uuid.toKotlinUuid())
            withContext(dispatchers.main) {
                if (tableId == null) {
                    source.sendError(Text.literal("You are not seated at any mahjong table"))
                } else {
                    source.sendFeedback({ Text.literal(action(tableId)) }, true)
                }
            }
        }
        return 1
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

    /**
     * 幫一個子指令節點同時掛上「不帶 `tile` 引數」與「帶 `tile` 引數」兩種執行路徑——省略引數時
     * [onExecute] 收到的 `tileArg` 為 `null`，由呼叫端自行決定預設牌面，見 [resolveAssetKey]。
     */
    private fun withOptionalTileArgument(
        node: LiteralArgumentBuilder<ServerCommandSource>,
        onExecute: (ServerCommandSource, String?) -> Int,
    ): LiteralArgumentBuilder<ServerCommandSource> = node
        .executes { ctx -> onExecute(ctx.source, null) }
        .then(
            argument(TILE_ARGUMENT, StringArgumentType.word())
                .executes { ctx -> onExecute(ctx.source, StringArgumentType.getString(ctx, TILE_ARGUMENT)) },
        )

    /** 幫 showcase 節點掛上可選的 cue 或逗號分隔 cue 清單。 */
    private fun withOptionalCueArgument(
        node: LiteralArgumentBuilder<ServerCommandSource>,
        allowMultiple: Boolean,
        onExecute: (ServerCommandSource, String?) -> Int,
    ): LiteralArgumentBuilder<ServerCommandSource> {
        node.executes { ctx -> onExecute(ctx.source, null) }
        return if (allowMultiple) {
            node.then(
                argument(CUE_ARGUMENT, StringArgumentType.greedyString())
                    .suggests(::suggestShowcaseCueList)
                    .executes { ctx -> onExecute(ctx.source, StringArgumentType.getString(ctx, CUE_ARGUMENT)) },
            )
        } else {
            node.then(
                argument(CUE_ARGUMENT, IdentifierArgumentType.identifier())
                    .suggests(::suggestSingleShowcaseCue)
                    .executes { ctx -> onExecute(ctx.source, IdentifierArgumentType.getIdentifier(ctx, CUE_ARGUMENT).toString()) },
            )
        }
    }

    /** 列出單一 showcase cue 的 Tab 補全候選。 */
    private fun suggestSingleShowcaseCue(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestShowcaseCues(context, builder, allowMultiple = false)

    /** 列出逗號分隔 showcase cue 清單目前最後一段的 Tab 補全候選。 */
    private fun suggestShowcaseCueList(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestShowcaseCues(context, builder, allowMultiple = true)

    /** 從正式 registry 建立 showcase cue 補全，不另外維護 cue 字串清單。 */
    private fun suggestShowcaseCues(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
        allowMultiple: Boolean,
    ): CompletableFuture<Suggestions> {
        buildShowcaseCueSuggestions(builder.remaining, showcaseRegistry.cueKeys, allowMultiple).forEach(builder::suggest)
        return builder.buildFuture()
    }

    /** 補全核心 TNT 時間線可直接跳轉的固定 phase。 */
    private fun suggestShowcasePhases(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        SHOWCASE_PHASES.filter { it.startsWith(builder.remaining, ignoreCase = true) }.forEach(builder::suggest)
        return builder.buildFuture()
    }

    /** 補全 registry 中適合由 `abortive` 入口預覽的完整途中流局原因 ID。 */
    private fun suggestAbortiveDrawReasons(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        exhaustiveDrawReasonDisplayNameRegistry.reasonIds
            .asSequence()
            .filterNot { it == RiichiExhaustiveDrawReason.Normal.id || it == RiichiExhaustiveDrawReason.KyuushuKyuuhai.id }
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .sorted()
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    /** `showcase <tsumo|ron>`：在玩家面前生成一至三翼完整鞘翅煙火 showcase。 */
    private fun previewShowcase(source: ServerCommandSource, isTsumo: Boolean, cues: List<String>, initialElapsedTicks: Int = 0): Int {
        if (cues.any { showcaseRegistry.find(it) == null }) return COMMAND_FAILURE
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val controllerPos = BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ)
        val tableId = Uuid.random()
        val wingTiles = cues.mapIndexed { seat, cue ->
            val tiles = List(DEFAULT_RULE_CONFIG.initialHandSize) { index ->
                val asset = ALL_TILE_ASSET_KEYS.dropLast(1)[(index + seat * 5) % (ALL_TILE_ASSET_KEYS.size - 1)]
                val tile = spawnFreeTile(world, layout.handPlacement(seat, DEFAULT_RULE_CONFIG.initialHandSize, index), MahjongTilePose.STANDING, asset)
                tile to asset
            }
            Triple(seat, cue, tiles)
        }
        val winningAsset = ALL_TILE_ASSET_KEYS.first()
        val winningPlacement = if (isTsumo) layout.drawnTilePlacement(DEFAULT_RULE_CONFIG.initialHandSize) else layout.discardPlacement(0)
        val winningTile = spawnFreeTile(world, winningPlacement, MahjongTilePose.FACE_UP, winningAsset)
        showcaseScheduler.schedule(
            world = world,
            tableId = tableId,
            controllerPos = controllerPos,
            stagePlacement = layout.showcaseStagePlacement(),
            startGameTime = world.time - initialElapsedTicks,
            winningTileId = winningTile.uuid.toKotlinUuid(),
            winningTileAssetKey = winningAsset,
            wings = wingTiles.map { (seat, cue, tiles) ->
                FabricWinCelebrationShowcaseScheduler.Wing(seat, cue, tiles.map { it.first.uuid.toKotlinUuid() to it.second })
            },
        ) ?: return COMMAND_FAILURE
        // Stage 已同步保存所有牌面與起始位置；debug 臨時牌不必繼續 tick 到演出結束。
        (wingTiles.flatMap { it.third.map(Pair<MahjongTileEntity, String>::first) } + winningTile).forEach(Entity::discard)
        return COMMAND_SUCCESS
    }

    /** `showcase phase <launch|orbit|place|ignite|explode|reveal>`：直接從指定 phase 開始。 */
    private fun previewShowcasePhase(source: ServerCommandSource, phase: String, cue: String): Int {
        val initialElapsedTicks = when (phase.lowercase()) {
            "launch" -> WinCelebrationCinematicTimeline.LAUNCH_START
            "orbit" -> WinCelebrationCinematicTimeline.ORBIT_BUILDUP_START
            "place" -> WinCelebrationCinematicTimeline.TNT_PLACEMENT_START
            "ignite" -> WinCelebrationCinematicTimeline.IGNITION_START
            "explode" -> WinCelebrationCinematicTimeline.EXPLOSION_START
            "reveal" -> WinCelebrationCinematicTimeline.TITLE_REVEAL_START
            else -> return COMMAND_FAILURE
        }
        return previewShowcase(source, isTsumo = true, cues = listOf(cue), initialElapsedTicks = initialElapsedTicks.toInt())
    }

    /** `exhaustive_draw_settlement`：以正式持久化 stage 預覽統一流局排行榜。 */
    private fun previewSettlement(
        source: ServerCommandSource,
        reasonId: String,
        tenpaiCount: Int,
        proof: Boolean = false,
        scoreDelta: Int = 0,
        playerCount: Int = 4,
    ): Int {
        if (':' !in reasonId) return COMMAND_FAILURE
        if (playerCount !in 2..4 || tenpaiCount !in 0..playerCount) return COMMAND_FAILURE
        val player = source.player ?: return COMMAND_FAILURE
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val previousScores = if (scoreDelta == 0) List(playerCount) { 25_000 } else List(playerCount) { index -> 28_000 - index * 2_000 }
        val currentScores = previousScores.toMutableList()
        if (scoreDelta != 0) {
            val lastPlaceSeat = previousScores.lastIndex
            currentScores[lastPlaceSeat] += scoreDelta
            val payingSeats = previousScores.indices.filterNot { it == lastPlaceSeat }
            val basePayment = scoreDelta / payingSeats.size
            val remainder = scoreDelta % payingSeats.size
            payingSeats.forEachIndexed { index, seat -> currentScores[seat] -= basePayment + if (index < remainder) 1 else 0 }
        }
        val currentRanks = currentScores.indices
            .sortedWith(compareByDescending<Int> { currentScores[it] }.thenBy { it })
            .withIndex()
            .associate { (rank, seat) -> seat to rank + 1 }
        val playerIds = List(playerCount) { seat ->
            if (seat == 0) player.uuid.toKotlinUuid() else Uuid.random()
        }
        val previewTilesBySeat = List(playerCount) { seat ->
            val assets = if (proof && seat == 0) {
                KYUUSHU_PREVIEW_ASSETS
            } else {
                List(DEFAULT_RULE_CONFIG.initialHandSize) { index ->
                    ALL_TILE_ASSET_KEYS[(seat * 7 + index) % (ALL_TILE_ASSET_KEYS.size - 1)]
                }
            }
            assets.mapIndexed { index, asset ->
                spawnFreeTile(
                    player.serverWorld,
                    layout.handPlacement(seat, assets.size, index),
                    MahjongTilePose.STANDING,
                    asset,
                ) to asset
            }
        }
        val revealedAssetsById = mutableMapOf<Uuid, String>()
        val players = List(playerCount) { seat ->
            val handPresentation = when {
                proof && seat == 0 -> ExhaustiveDrawSettlementHandPresentation.REVEAL_PROOF
                seat < tenpaiCount -> ExhaustiveDrawSettlementHandPresentation.REVEAL_TENPAI
                else -> ExhaustiveDrawSettlementHandPresentation.CONCEAL
            }
            val handTiles = previewTilesBySeat[seat]
            if (handPresentation != ExhaustiveDrawSettlementHandPresentation.CONCEAL) {
                handTiles.forEach { (tile, asset) -> revealedAssetsById[tile.uuid.toKotlinUuid()] = asset }
            }
            ExhaustiveDrawSettlementPlayerPresentation(
                ranking = ScoreRankingPlayer(
                    playerId = playerIds[seat],
                    seatIndex = seat,
                    isAi = seat != 0,
                    previousScore = previousScores[seat],
                    currentScore = currentScores[seat],
                    previousRank = seat + 1,
                    currentRank = currentRanks.getValue(seat),
                ),
                seatWind = Wind.entries[seat],
                handTileIds = handTiles.map { it.first.uuid.toKotlinUuid() },
                handPresentation = handPresentation,
                revealedHandTileIds = if (handPresentation == ExhaustiveDrawSettlementHandPresentation.CONCEAL) emptyList() else handTiles.map { it.first.uuid.toKotlinUuid() },
                waitingTiles = emptyList(),
                statusId = when {
                    proof && seat == 0 -> BuiltInExhaustiveDrawSettlementStatusIds.DRAW_DECLARATION
                    seat < tenpaiCount -> BuiltInExhaustiveDrawSettlementStatusIds.TENPAI
                    reasonId.endsWith(":normal") -> BuiltInExhaustiveDrawSettlementStatusIds.NOTEN
                    else -> null
                },
            )
        }
        val end = exhaustiveDrawSettlementScheduler.schedule(
            world = player.serverWorld,
            tableId = Uuid.random(),
            controllerPos = BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ),
            placement = layout.showcaseStagePlacement(),
            request = ExhaustiveDrawSettlementPresentationRequest(reasonId, players),
            waitingTileAssetsBySeat = players.filter { it.handPresentation == ExhaustiveDrawSettlementHandPresentation.REVEAL_TENPAI }
                .associate { it.ranking.seatIndex to DEFAULT_WAITING_TILE_ASSETS },
            revealedTileAssetsById = revealedAssetsById,
        ) ?: return COMMAND_FAILURE
        scheduleCleanup(player.serverWorld, end, previewTilesBySeat.flatten().map(Pair<MahjongTileEntity, String>::first))
        source.sendFeedback({ net.minecraft.text.Text.literal("Exhaustive draw settlement preview active until game time $end") }, false)
        return COMMAND_SUCCESS
    }

    /** `hovered_text exhaustive_draw_settlement`：以正式 builder 發送一筆可懸停檢查的 round-result 訊息。 */
    private fun previewExhaustiveDrawSettlementHoveredText(source: ServerCommandSource): Int {
        val details = Text.empty()
        HOVERED_TEXT_SAMPLE_ROWS.forEachIndexed { index, row ->
            if (index > 0) details.append(Text.literal("\n"))
            details.append(
                Text.translatable(
                    MinecraftMessageKeys.ROUND_RESULT_PLAYER_LINE,
                    row.playerName,
                    row.previousRank.toString(),
                    row.currentRank.toString(),
                    row.rankSymbol,
                    row.previousScore.toString(),
                    row.currentScore.toString(),
                ),
            )
        }
        source.sendFeedback(
            {
                buildRoundResultChatText(
                    Text.translatable(MinecraftMessageKeys.GAME_ACTION_EXHAUSTIVE_DRAW),
                    details,
                )
            },
            false,
        )
        return COMMAND_SUCCESS
    }

    /** `hovered_text win_settlement`：以正式 round-result builder 預覽胡牌結算摘要。 */
    private fun previewWinSettlementHoveredText(source: ServerCommandSource): Int {
        val details = Text.empty()
            .append(Text.translatable(WinSettlementTranslationKeys.RON_SUMMARY, "Player", "AI-debug"))
            .append(Text.literal("\n"))
            .append(Text.translatable(WinSettlementTranslationKeys.HAN_FU, "3", "30"))
            .append(Text.literal("\n"))
            .append(Text.translatable(WinSettlementTranslationKeys.TOTAL_SCORE, "7700"))
        source.sendFeedback(
            { buildRoundResultChatText(Text.translatable(WinSettlementTranslationKeys.RON), details) },
            false,
        )
        return COMMAND_SUCCESS
    }

    /** `hovered_text match_settlement`：以正式 match-result builder 預覽最終排行 hover。 */
    private fun previewMatchSettlementHoveredText(source: ServerCommandSource): Int {
        val details = Text.empty()
        listOf("PlayerLongName123" to "120000", "AI-debug" to "45000", "PlayerC" to "25000", "PlayerD" to "-12000")
            .forEachIndexed { index, (name, score) ->
                if (index > 0) details.append(Text.literal("\n"))
                details.append(Text.translatable(MinecraftMessageKeys.RANKING_LINE, (index + 1).toString(), name, score))
            }
        source.sendFeedback({ buildMatchResultChatText(details) }, false)
        return COMMAND_SUCCESS
    }

    /** 走正式 scheduler 預覽末位至第一名的終局揭曉。 */
    private fun previewMatchSettlement(source: ServerCommandSource, playerCount: Int): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = virtualTableLayout(player.blockX, player.blockY, player.blockZ, player.horizontalFacing.toMahjongTableFacing())
        val scores = listOf(120_000, 45_000, 25_000, -12_000).take(playerCount)
        val playerIds = List(playerCount) { index -> if (index == 0) player.uuid.toKotlinUuid() else Uuid.random() }
        val request = MatchSettlementPresentationRequest(
            players = List(playerCount) { seatIndex ->
                MatchSettlementPlayerPresentation(
                    playerId = playerIds[seatIndex],
                    seatIndex = seatIndex,
                    isAi = seatIndex != 0,
                    initialSeatIndex = seatIndex,
                    finalScore = scores[seatIndex],
                    finalRank = seatIndex + 1,
                )
            },
        )
        val end = matchSettlementScheduler.schedule(
            world = world,
            tableId = Uuid.random(),
            controllerPos = BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ),
            placement = layout.showcaseStagePlacement(),
            earliestStartGameTime = world.time,
            request = request,
        ) ?: return COMMAND_FAILURE
        source.sendFeedback({ Text.literal("Match settlement preview active until game time $end") }, false)
        return COMMAND_SUCCESS
    }

    /** 走正式 scheduler 預覽一至三位贏家的完整胡牌結算與最終排行。 */
    private fun previewWinSettlement(
        source: ServerCommandSource,
        preview: WinSettlementPreview,
        winnerCount: Int,
    ): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = virtualTableLayout(player.blockX, player.blockY, player.blockZ, player.horizontalFacing.toMahjongTableFacing())
        val playerIds = List(4) { index -> if (index == 0) player.uuid.toKotlinUuid() else Uuid.random() }
        val tileAssetsById = mutableMapOf<Uuid, String>()
        val winners = List(winnerCount) { winnerIndex ->
            val handIds = List(DEFAULT_RULE_CONFIG.initialHandSize - 7) { tileIndex ->
                Uuid.random().also { tileAssetsById[it] = ALL_TILE_ASSET_KEYS[(winnerIndex * 7 + tileIndex) % (ALL_TILE_ASSET_KEYS.size - 1)] }
            }
            val ponIds = List(3) { Uuid.random().also { id -> tileAssetsById[id] = "s3" } }
            val kanIds = List(4) { Uuid.random().also { id -> tileAssetsById[id] = "p8" } }
            val winningTileId = Uuid.random().also { tileAssetsById[it] = if (winnerIndex == 0) "m1" else "p${winnerIndex + 1}" }
            val yakuman = preview == WinSettlementPreview.YAKUMAN
            val regularYakuEntries = if (preview == WinSettlementPreview.RON && winnerIndex == 0) {
                listOf(
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.reach", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.ippatsu", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.tsumo", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.pinfu", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.tanyao", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.ipeiko", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.sanshokudohjun", "2"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.ikkitsukan", "2"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.chanta", "2"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.toitoiho", "2"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.honitsu", "3"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.chinitsu", "6"),
                )
            } else {
                listOf(
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.reach", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.ippatsu", "1"),
                    WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.pinfu", "1"),
                )
            }
            WinSettlementWinnerPresentation(
                playerId = playerIds[winnerIndex],
                seatIndex = winnerIndex,
                responsiblePlayerId = playerIds.getOrNull(winnerCount),
                totalScore = if (yakuman) {
                    32_000
                } else if (preview == WinSettlementPreview.NAGASHI) {
                    8_000
                } else {
                    7_700
                },
                handTileIds = handIds,
                melds = listOf(
                    MeldPresentation(MeldType.PON, ponIds, ponIds.first(), RelativeDirection.Left, false),
                    MeldPresentation(MeldType.CLOSED_KAN, kanIds, null, RelativeDirection.Self, false),
                ),
                winningTileId = winningTileId,
                detailFields = buildList {
                    add(
                        WinSettlementDetailField(
                            "mahjongcraft:riichi_yaku",
                            WinSettlementDetailValue.Entries(
                                if (yakuman) {
                                    listOf(
                                        WinSettlementDetailValue.Entries.Entry(
                                            translationKey = "mahjongcraft.game.yaku.kokushimuso_jusanmenmachi",
                                            trailingTranslationKey = "mahjongcraft.game.score.yakuman_2x",
                                        ),
                                        WinSettlementDetailValue.Entries.Entry(
                                            translationKey = "mahjongcraft.game.yaku.daisangen",
                                            trailingTranslationKey = "mahjongcraft.game.score.yakuman_1x",
                                        ),
                                    )
                                } else if (preview != WinSettlementPreview.NAGASHI) {
                                    regularYakuEntries
                                } else {
                                    listOf(WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.nagashi_mangan"))
                                },
                            ),
                        ),
                    )
                    if (!yakuman && preview != WinSettlementPreview.NAGASHI) {
                        val totalHan = regularYakuEntries.sumOf { it.trailingText.toIntOrNull() ?: 0 }
                        add(WinSettlementDetailField("mahjongcraft:riichi_han_fu", WinSettlementDetailValue.Text(WinSettlementTranslationKeys.HAN_FU, listOf(totalHan.toString(), "30"))))
                    } else if (yakuman) {
                        add(WinSettlementDetailField("mahjongcraft:riichi_yakuman_total", WinSettlementDetailValue.Text("mahjongcraft.game.score.yakuman_3x")))
                    }
                    add(WinSettlementDetailField("mahjongcraft:riichi_dora", WinSettlementDetailValue.Tiles(handIds.take(2))))
                    add(WinSettlementDetailField("mahjongcraft:riichi_ura_dora", WinSettlementDetailValue.Tiles(handIds.drop(2).take(1))))
                },
            )
        }
        val ranking = ScoreRankingPresentation(
            listOf(31_000, 28_000, 24_000, 17_000).zip(listOf(26_000, 23_000, 19_000, 32_000)).mapIndexed { seatIndex, (before, after) ->
                ScoreRankingPlayer(playerIds[seatIndex], seatIndex, seatIndex != 0, before, after, seatIndex + 1, listOf(2, 3, 4, 1)[seatIndex])
            },
        )
        val request = WinSettlementPresentationRequest(
            outcomeId = if (preview == WinSettlementPreview.NAGASHI) {
                BuiltInRoundOutcomeIds.NAGASHI_MANGAN
            } else if (preview == WinSettlementPreview.TSUMO) {
                BuiltInRoundOutcomeIds.TSUMO
            } else {
                BuiltInRoundOutcomeIds.RON
            },
            templateKey = if (preview == WinSettlementPreview.NAGASHI) {
                WinSettlementPresentationRequestFactory.RIICHI_TEMPLATE_KEY
            } else {
                WinSettlementPresentationRequestFactory.RIICHI_TEMPLATE_KEY
            },
            isTsumo = preview == WinSettlementPreview.TSUMO,
            winners = winners,
            ranking = ranking,
        )
        return if (
            winSettlementScheduler.schedule(
                world,
                Uuid.random(),
                BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ),
                layout.showcaseStagePlacement(),
                world.time,
                request,
                tileAssetsById,
            ) != null
        ) {
            COMMAND_SUCCESS
        } else {
            COMMAND_FAILURE
        }
    }

    /** 使用正式 feedback publisher 發送建立牌桌位置的 hover 預覽。 */
    private fun previewGameCreatedLocationHoveredText(source: ServerCommandSource): Int {
        val player = source.player ?: return COMMAND_FAILURE
        feedbackPublisher.publish(
            player.uuid.toKotlinUuid(),
            MinecraftPlayerFeedback.GameCreated(
                TableLocation(
                    dimensionId = player.world.registryKey.value.toString(),
                    x = player.blockX,
                    y = player.blockY,
                    z = player.blockZ,
                ),
            ),
        )
        return COMMAND_SUCCESS
    }

    /** 使用正式 feedback publisher 發送遊戲設定 show／changed／unchanged hover 預覽。 */
    private fun previewGameConfigHoveredText(source: ServerCommandSource, variant: String): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val current = json.encodeToString(GameConfig(DEFAULT_RULE_CONFIG).toDto(networkRegistries))
        val changed = json.encodeToString(
            GameConfig(DEFAULT_RULE_CONFIG, GameFlowConfig(timeControl = ActionTimeControl.Short)).toDto(networkRegistries),
        )
        val feedback = when (variant) {
            CHANGED_ARGUMENT -> MinecraftPlayerFeedback.GameConfigChanged(current, changed)
            UNCHANGED_ARGUMENT -> MinecraftPlayerFeedback.GameConfigUnchanged(current)
            else -> MinecraftPlayerFeedback.ShowGameConfig(current)
        }
        feedbackPublisher.publish(player.uuid.toKotlinUuid(), feedback)
        return COMMAND_SUCCESS
    }

    /** 使用正式 config builder 發送目前 server config 的 hover 預覽。 */
    private fun previewServerConfigHoveredText(source: ServerCommandSource): Int {
        source.sendFeedback(
            {
                configShowMessage(
                    "Effective server config ",
                    serverConfigManager.displayPath,
                    serverConfigManager.formattedCurrentToml(),
                )
            },
            false,
        )
        return COMMAND_SUCCESS
    }

    /** 向 Fabric 登記每 tick 一次的臨時 entity 清除驅動，理由見 [scheduleCleanup] KDoc。 */
    private fun registerCleanupTicking() {
        ServerTickEvents.END_SERVER_TICK.register {
            val iterator = cleanupTasks.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                if (task.world.time >= task.endGameTime) {
                    task.entities.forEach(Entity::discard)
                    iterator.remove()
                }
            }
        }
    }

    /**
     * `win <tsumo|ron>`：完全自成一體地重播胡牌慶祝演出——依標準日麻初始手牌張數，臨時生成與正式
     * 對局相同數量的立牌
     * 「手牌」（先打亂順序生成，再用 [TileAnimationSteps.scheduleReorder] 飛到整理後的格位，實際演出
     * 「強制理牌重排」這一步），接著依 [isTsumo] 走跟正式對局一模一樣的時間軸：自摸時胡牌張重排至
     * 手牌右側保留間距的正式摸牌位，先單獨倒下、等待、其餘手牌一起倒下；榮和時省略胡牌張單獨倒下
     * 這一步，額外在牌河生成
     * 一張已經面朝上的胡牌張（模擬放銃者牌河/副露區的那張），跟正式對局的差異完全一致，見
     * `FabricGamePresentationPublisher.publishWinCelebration` KDoc。降臨特效鎖定的目標固定是胡牌張，
     * 沿用 [effectScheduler] 排定，特效播完後透過 `onComplete` 回呼清除這次生成的全部臨時 entity。
     */
    private fun previewWin(source: ServerCommandSource, isTsumo: Boolean, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val handSize = DEFAULT_RULE_CONFIG.initialHandSize
        val handPlacements = (0 until handSize).map { slot -> layout.handPlacement(handSize = handSize, tileIndex = slot) }
        val sortedPlacements = if (isTsumo) {
            listOf(layout.drawnTilePlacement(standingTileCount = handSize)) + handPlacements
        } else {
            handPlacements
        }
        val handTiles = sortedPlacements.shuffled().map { shuffledPlacement ->
            spawnFreeTile(world, shuffledPlacement, MahjongTilePose.STANDING, assetKey)
        }

        val reorderStartGameTime = world.time
        val reorderEndGameTime = reorderStartGameTime + MahjongTileTableLayout.WIN_REORDER_FLIGHT_DURATION_TICKS
        handTiles.forEachIndexed { index, tile -> TileAnimationSteps.scheduleReorder(tile, sortedPlacements[index], reorderStartGameTime) }

        val winningTile: MahjongTileEntity
        val handLaydownEndGameTime: Long
        if (isTsumo) {
            winningTile = handTiles.first()
            val winTileLaydownStartGameTime = reorderEndGameTime
            val winTileLaydownEndGameTime = winTileLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            TileAnimationSteps.scheduleLaydown(winningTile, winTileLaydownStartGameTime)
            val restLaydownStartGameTime = winTileLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
            (handTiles - winningTile).forEach { tile -> TileAnimationSteps.scheduleLaydown(tile, restLaydownStartGameTime) }
            handLaydownEndGameTime = restLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        } else {
            val discardedPlacement = layout.discardPlacement(discardIndex = 0)
            winningTile = spawnFreeTile(world, discardedPlacement, MahjongTilePose.FACE_UP, assetKey)
            val handLaydownStartGameTime = reorderEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
            handTiles.forEach { tile -> TileAnimationSteps.scheduleLaydown(tile, handLaydownStartGameTime) }
            handLaydownEndGameTime = handLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        }

        val effectStartGameTime = handLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_EFFECT_DELAY_TICKS
        val effectEndGameTime = effectStartGameTime + MahjongTileTableLayout.WIN_EFFECT_DURATION_TICKS
        val allSpawnedTiles = if (isTsumo) handTiles else handTiles + winningTile
        effectScheduler.schedule(
            world = world,
            targetTileId = winningTile.uuid.toKotlinUuid(),
            startGameTime = effectStartGameTime,
            endGameTime = effectEndGameTime,
            onComplete = { allSpawnedTiles.forEach(Entity::discard) },
        )
        return COMMAND_SUCCESS
    }

    /** `dice`：臨時生成 [DEBUG_DICE_COUNT] 顆骰子，重播擲骰動畫；動畫（含觀看緩衝）播完後自行清除。 */
    private fun previewDice(source: ServerCommandSource): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val tableId = Uuid.random()
        val placements = MahjongDiceTableLayout.placements(
            controllerX = layout.controllerX,
            controllerY = layout.controllerY,
            controllerZ = layout.controllerZ,
            tableId = tableId,
            tableFacing = layout.tableFacing,
            throwSide = MahjongTableSide.SOUTH,
            rollSequence = 0,
            diceCount = DEBUG_DICE_COUNT,
        )
        val dice = placements.map { placement ->
            MahjongDiceEntity(world = world).apply {
                refreshPositionAndAngles(
                    placement.finalPosition.x,
                    placement.finalPosition.y,
                    placement.finalPosition.z,
                    0.0f,
                    0.0f,
                )
            }.also(world::spawnEntity)
        }
        dice.zip(placements).forEach { (entity, placement) ->
            entity.startRoll(
                finalPoint = MahjongDicePoint.entries.random(),
                seed = Random.nextLong(),
                startDelayTicks = placement.startDelayTicks,
                startOffset = placement.startOffset,
            )
        }
        val lifetimeTicks = DiceRollAnimationSpec.DEFAULT_DURATION_TICKS + DiceRollAnimationSpec.EXTRA_VIEWING_TICKS
        scheduleCleanup(world, world.time + lifetimeTicks, dice)
        return COMMAND_SUCCESS
    }

    /**
     * `deal`：依標準日麻初始手牌張數，在正式牌牆座標臨時生成蓋牌，重播開局發牌
     * 動畫（起飛→落下→翻牌）飛到手牌列；不模擬多座位/多批次，全部視為單一批次、單一座位，理由見
     * [TileAnimationSteps.scheduleDealBatch] KDoc——它本身只吃單張牌與絕對時刻，不需要真正的多座位
     * 協調也能正確播放同一段動畫。
     */
    private fun previewDeal(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val handSize = DEFAULT_RULE_CONFIG.initialHandSize

        val finalPlacements = (0 until handSize).map { slot ->
            layout.handPlacement(handSize = handSize, tileIndex = slot)
        }
        val sourcePlacements = (0 until handSize).map { slot ->
            layout.wallPlacement(tileIndex = slot)
        }
        val tiles = sourcePlacements.map { placement -> spawnFreeTile(world, placement, MahjongTilePose.FACE_DOWN, assetKey) }

        val liftAbsoluteGameTime = world.time
        val flipAbsoluteGameTime = liftAbsoluteGameTime +
            MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_FLIP_GAP_TICKS
        tiles.forEachIndexed { index, tile ->
            TileAnimationSteps.scheduleDealBatch(tile, finalPlacements[index], finalPlacements[index], liftAbsoluteGameTime, flipAbsoluteGameTime)
        }
        val endGameTime = flipAbsoluteGameTime + MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, tiles)
        return COMMAND_SUCCESS
    }

    /**
     * `draw`：臨時生成一張牌在模擬牌牆位置（蓋牌），重播摸牌動畫（起飛→隱形傳送→翻面→落下）飛到
     * 摸牌位。
     */
    private fun previewDraw(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacement = layout.wallPlacement(tileIndex = 0)
        val finalPlacement = layout.drawnTilePlacement(standingTileCount = DEFAULT_RULE_CONFIG.initialHandSize)
        val tile = spawnFreeTile(world, sourcePlacement, MahjongTilePose.FACE_DOWN, assetKey)
        TileAnimationSteps.scheduleDrawnTile(tile, finalPlacement)
        val endGameTime = world.time +
            MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS +
            PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, listOf(tile))
        return COMMAND_SUCCESS
    }

    /** `discard`：臨時生成一張手牌位置的立牌，重播捨牌動畫（連續可見拋物線飛行）飛到模擬牌河位置。 */
    private fun previewDiscard(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacement = layout.handPlacement(handSize = DEFAULT_RULE_CONFIG.initialHandSize, tileIndex = 0)
        val finalPlacement = layout.discardPlacement(discardIndex = 0)
        val tile = spawnFreeTile(world, sourcePlacement, MahjongTilePose.STANDING, assetKey)
        TileAnimationSteps.scheduleDiscardFlight(tile, finalPlacement)
        val endGameTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, listOf(tile))
        return COMMAND_SUCCESS
    }

    /**
     * `meld <chi|pon|kan>`：臨時生成幾張手牌位置的立牌（吃/碰 3 張，槓 4 張），重播鳴牌動畫（連續可見
     * 拋物線飛行）飛到模擬副露區位置，姿態轉為面朝上。
     */
    private fun previewMeld(source: ServerCommandSource, type: MeldType, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val tileCount = if (type == MeldType.OPEN_KAN) MELD_KAN_TILE_COUNT else MELD_TILE_COUNT
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacements = (0 until tileCount).map { slot ->
            layout.handPlacement(handSize = DEFAULT_RULE_CONFIG.initialHandSize, tileIndex = slot)
        }
        val finalPlacements = layout.meldPlacements(type = type, tileCount = tileCount)
        val tiles = sourcePlacements.map { placement -> spawnFreeTile(world, placement, MahjongTilePose.STANDING, assetKey) }
        tiles.forEachIndexed { index, tile -> TileAnimationSteps.scheduleMeldClaim(tile, finalPlacements[index], MahjongTilePose.FACE_UP) }
        val endGameTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, tiles)
        return COMMAND_SUCCESS
    }

    /** 生成一張自由放置（不掛任何桌子/對局）的臨時牌 entity，理由見類別 KDoc 的牌面說明。 */
    private fun spawnFreeTile(
        world: ServerWorld,
        placement: MahjongTileWallPlacement,
        pose: MahjongTilePose,
        assetKey: String,
    ): MahjongTileEntity {
        val tile = MahjongTileEntity(world = world).apply {
            uuid = Uuid.random().toJavaUuid()
            refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            tilePose = pose
            tileAssetKey = assetKey
        }
        world.spawnEntity(tile)
        return tile
    }

    /** 省略 `tile` 引數時隨機抽一個內建牌面（排除佔位用的 `unknown`），否則正規化呼叫者輸入的字串。 */
    private fun resolveAssetKey(tileArg: String?): String = tileArg?.normalizedTileAssetKey()
        ?: ALL_TILE_ASSET_KEYS.dropLast(1).random()

    /** 以玩家腳下方塊為基準，將虛擬 controller 沿玩家視線前推，讓座位 0 的正式布局落在玩家面前。 */
    private fun virtualTableLayout(
        playerBlockX: Int,
        playerBlockY: Int,
        playerBlockZ: Int,
        tableFacing: MahjongTableFacing,
    ): VirtualTableLayout {
        val controller = virtualControllerPos(playerBlockX, playerBlockY, playerBlockZ, tableFacing)
        return VirtualTableLayout(controller.x, controller.y, controller.z, tableFacing)
    }

    /** 算出 debug 虛擬桌 controller 方塊座標。 */
    private fun virtualControllerPos(
        playerBlockX: Int,
        playerBlockY: Int,
        playerBlockZ: Int,
        tableFacing: MahjongTableFacing,
    ): net.minecraft.util.math.BlockPos {
        val (offsetX, offsetZ) = when (tableFacing) {
            MahjongTableFacing.NORTH -> 0 to -VIRTUAL_CONTROLLER_FORWARD_BLOCKS
            MahjongTableFacing.EAST -> VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0
            MahjongTableFacing.SOUTH -> 0 to VIRTUAL_CONTROLLER_FORWARD_BLOCKS
            MahjongTableFacing.WEST -> -VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0
        }
        return net.minecraft.util.math.BlockPos(playerBlockX + offsetX, playerBlockY, playerBlockZ + offsetZ)
    }

    /** 虛擬桌的正式布局呼叫參數；不對應任何實際 controller block 或對局狀態。 */
    private data class VirtualTableLayout(
        val controllerX: Int,
        val controllerY: Int,
        val controllerZ: Int,
        val tableFacing: MahjongTableFacing,
    ) {
        /** 取得座位 0 的正式手牌格位。 */
        fun handPlacement(handSize: Int, tileIndex: Int): MahjongTileWallPlacement = handPlacement(DEBUG_SEAT_INDEX, handSize, tileIndex)

        /** 取得指定座位的正式手牌格位，供多家和 showcase 使用。 */
        fun handPlacement(seatIndex: Int, handSize: Int, tileIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.handPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatIndex = seatIndex,
            handSize = handSize,
            tileIndex = tileIndex,
        )

        /** 取得與正式桌面完全一致的 showcase 世界中心。 */
        fun showcaseStagePlacement(): MahjongTileWallPlacement = MahjongTileTableLayout.showcaseStagePlacement(controllerX, controllerY, controllerZ)

        /** 取得座位 0 的正式摸牌格位。 */
        fun drawnTilePlacement(standingTileCount: Int): MahjongTileWallPlacement = MahjongTileTableLayout.drawnTilePlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatIndex = DEBUG_SEAT_INDEX,
            standingTileCount = standingTileCount,
        )

        /** 取得標準 17 墩牌牆第一面的正式牌張格位。 */
        fun wallPlacement(tileIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            dealerSeatIndex = DEBUG_SEAT_INDEX,
            stacksPerSide = STANDARD_WALL_STACKS_PER_SIDE,
            position = TileWallPosition(
                side = 0,
                stack = tileIndex / WALL_LAYERS_PER_STACK,
                layer = tileIndex % WALL_LAYERS_PER_STACK,
            ),
        )

        /** 取得座位 0 第一張正式牌河格位。 */
        fun discardPlacement(discardIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.discardPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatIndex = DEBUG_SEAT_INDEX,
            discardIndex = discardIndex,
            isSidewaysMarked = false,
            sidewaysMarkedDiscardIndex = null,
            wallRemaining = true,
        )

        /** 依正式副露游標、鳴牌來源與牌寬規則，取得單組吃、碰或明槓的格位。 */
        fun meldPlacements(type: MeldType, tileCount: Int): List<MahjongTileWallPlacement> {
            val sourceDirection = if (type == MeldType.CHI) RelativeDirection.Left else RelativeDirection.Across
            val sidewaysSlot = MahjongTileTableLayout.sidewaysSlotIndex(sourceDirection, tileCount)
            var cursorAlong = MahjongTileTableLayout.stickAreaWidth(stickCount = 0)
            return (tileCount - 1 downTo 0).map { slot ->
                val isSideways = slot == sidewaysSlot
                val halfWidth = if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
                cursorAlong += halfWidth
                val placement = MahjongTileTableLayout.meldPlacement(
                    controllerX = controllerX,
                    controllerY = controllerY,
                    controllerZ = controllerZ,
                    tableFacing = tableFacing,
                    seatIndex = DEBUG_SEAT_INDEX,
                    alongOffsetFromCorner = cursorAlong,
                    isSidewaysTile = isSideways,
                )
                cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
                placement
            }.reversed()
        }
    }

    /**
     * 排定 [entities] 在 [endGameTime]（絕對 game time）到期時自動清除——每個子指令的動畫總時長各不
     * 相同，呼叫端各自算好自己那套動畫（含收尾緩衝）實際播完的時刻。純記憶體佇列，不寫進世界存檔：
     * 這些本來就是全新生成、不影響任何真實對局狀態的臨時 entity，伺服器重啟後這批清除排程單純消失，
     * 頂多留下沒清乾淨的臨時裝飾牌，不影響任何遊戲邏輯。
     */
    private fun scheduleCleanup(world: ServerWorld, endGameTime: Long, entities: List<Entity>) {
        cleanupTasks += CleanupTask(world, endGameTime, entities)
    }

    /** 見 [scheduleCleanup] KDoc。 */
    private data class CleanupTask(val world: ServerWorld, val endGameTime: Long, val entities: List<Entity>)

    private companion object {
        const val OP_PERMISSION_LEVEL: Int = 2
        const val DEBUG_SUBCOMMAND: String = "debug"
        const val TILE_ARGUMENT: String = "tile"
        const val WIN_SUBCOMMAND: String = "win"
        const val SHOWCASE_SUBCOMMAND: String = "showcase"
        const val MULTI_RON_ARGUMENT: String = "multi_ron"
        const val WINNER_COUNT_ARGUMENT: String = "winner_count"
        const val CUE_ARGUMENT: String = "cue"
        const val PHASE_ARGUMENT: String = "phase"
        const val PHASE_NAME_ARGUMENT: String = "phase_name"
        val DEFAULT_SHOWCASE_CUE: String = BuiltInWinCelebrationCueIds.riichiYakuman("kokushi_musou")
        const val TSUMO_ARGUMENT: String = "tsumo"
        const val RON_ARGUMENT: String = "ron"
        const val DICE_SUBCOMMAND: String = "dice"
        const val DEAL_SUBCOMMAND: String = "deal"
        const val DRAW_SUBCOMMAND: String = "draw"
        const val DISCARD_SUBCOMMAND: String = "discard"
        const val MELD_SUBCOMMAND: String = "meld"
        const val CONTINUING_WIN_SUBCOMMAND: String = "continuing_win"
        const val WIN_SHOWCASE_OVERRIDE_SUBCOMMAND: String = "win_showcase_override"
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
        const val EXHAUSTIVE_DRAW_SETTLEMENT_SUBCOMMAND: String = "exhaustive_draw_settlement"
        const val WIN_SETTLEMENT_SUBCOMMAND: String = "win_settlement"
        const val MATCH_SETTLEMENT_SUBCOMMAND: String = "match_settlement"
        const val MATCH_PROGRESSION_SUBCOMMAND: String = "match_progression"
        const val HOVERED_TEXT_SUBCOMMAND: String = "hovered_text"
        const val EXHAUSTIVE_DRAW_SETTLEMENT_ARGUMENT: String = "exhaustive_draw_settlement"
        const val GAME_CREATED_LOCATION_ARGUMENT: String = "game_created_location"
        const val GAME_CONFIG_ARGUMENT: String = "game_config"
        const val SERVER_CONFIG_ARGUMENT: String = "server_config"
        const val SHOW_ARGUMENT: String = "show"
        const val CHANGED_ARGUMENT: String = "changed"
        const val UNCHANGED_ARGUMENT: String = "unchanged"
        const val NORMAL_ARGUMENT: String = "normal"
        const val KYUUSHU_ARGUMENT: String = "kyuushu"
        const val YAKUMAN_ARGUMENT: String = "yakuman"
        const val NAGASHI_ARGUMENT: String = "nagashi"
        const val ABORTIVE_ARGUMENT: String = "abortive"
        const val SCORE_ARGUMENT: String = "score"
        const val SCORE_DELTA_ARGUMENT: String = "delta"
        const val TENPAI_COUNT_ARGUMENT: String = "tenpai_count"
        const val PLAYER_COUNT_ARGUMENT: String = "player_count"
        const val REASON_ARGUMENT: String = "reason"
        const val CHI_ARGUMENT: String = "chi"
        const val PON_ARGUMENT: String = "pon"
        const val KAN_ARGUMENT: String = "kan"
        const val DEFAULT_SCORE_DELTA: Int = 9_000
        val SHOWCASE_PHASES: List<String> = listOf("launch", "orbit", "place", "ignite", "explode", "reveal")
        val DEFAULT_WAITING_TILE_ASSETS: List<String> = listOf("m1", "m4", "m7")
        val DEBUG_OPTION_IDS: List<String> = listOf("mahjongcraft:alpha", "mahjongcraft:beta", "mahjongcraft:gamma")
        val KYUUSHU_PREVIEW_ASSETS: List<String> = listOf(
            "m1", "m9", "p1", "p9", "s1", "s9", "east", "south", "west", "north", "red_dragon", "green_dragon", "white_dragon", "m1",
        )
        val HOVERED_TEXT_SAMPLE_ROWS: List<HoveredTextSampleRow> = listOf(
            HoveredTextSampleRow("Player", 4, 1, "↑", 16_000, 34_000),
            HoveredTextSampleRow("AI-1a2b", 1, 2, "↓", 31_000, 25_000),
            HoveredTextSampleRow("AI-3c4d", 2, 3, "↓", 28_000, 22_000),
            HoveredTextSampleRow("AI-5e6f", 3, 4, "↓", 25_000, 19_000),
        )

        /** 虛擬 controller 與玩家腳下方塊的水平距離，使近側手牌落在玩家前方約一格處。 */
        const val VIRTUAL_CONTROLLER_FORWARD_BLOCKS: Int = 3

        /** 預覽固定使用座位 0，對應虛擬桌面向玩家的近側。 */
        const val DEBUG_SEAT_INDEX: Int = 0

        /** 內建日麻 progression debug 情境固定使用的玩家數。 */
        const val RIICHI_PLAYER_COUNT: Int = 4

        /** Development preparation 測試步驟種類。 */
        enum class PreparationPreview {
            CONFIRM,
            CHOICE,
            TILES,
        }

        /** 標準四人日麻共 136 張牌，四面各 17 墩。 */
        const val STANDARD_WALL_STACKS_PER_SIDE: Int = 17

        /** 每墩牌牆固定上下兩層。 */
        const val WALL_LAYERS_PER_STACK: Int = 2

        /** 預覽牌數跟隨標準日麻規則的單一設定來源。 */
        val DEFAULT_RULE_CONFIG: RiichiRuleConfig = RiichiRuleConfig()

        /** `dice` 一次生成的骰子數量。 */
        const val DEBUG_DICE_COUNT: Int = 2

        /** 動畫播完到實際清除臨時 entity 之間的觀看緩衝，理由同正式對局的觀看緩衝設計。 */
        const val PREVIEW_VIEWING_BUFFER_TICKS: Int = 20

        /** `meld kan` 需要的牌數。 */
        const val MELD_KAN_TILE_COUNT: Int = 4

        /** `meld chi`／`meld pon` 需要的牌數。 */
        const val MELD_TILE_COUNT: Int = 3

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
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

    /** 正式 round-result hover builder 的單列測試資料。 */
    private data class HoveredTextSampleRow(
        val playerName: String,
        val previousRank: Int,
        val currentRank: Int,
        val rankSymbol: String,
        val previousScore: Int,
        val currentScore: Int,
    )

    private enum class WinSettlementPreview {
        RON,
        TSUMO,
        YAKUMAN,
        NAGASHI,
    }
}

/**
 * 將 debug 指令提供的 cue 展開成指定贏家數量；單一 cue 套用至所有贏家，多個 cue 則依序對應，
 * 未提供的尾端位置使用 [defaultCue]。
 */
internal fun expandShowcaseCues(
    supplied: List<String>,
    winnerCount: Int,
    defaultCue: String,
): List<String> = when (supplied.size) {
    0 -> List(winnerCount) { defaultCue }
    1 -> List(winnerCount) { supplied.single() }
    else -> List(winnerCount) { index -> supplied.getOrNull(index) ?: defaultCue }
}

/**
 * 依目前輸入內容建立完整 showcase cue key 候選；逗號清單只替換最後一段。
 */
internal fun buildShowcaseCueSuggestions(
    remaining: String,
    cueKeys: Set<String>,
    allowMultiple: Boolean,
): List<String> {
    val separatorIndex = if (allowMultiple) remaining.lastIndexOf(',') else -1
    val completedPrefix = remaining.takeIf { separatorIndex >= 0 }?.substring(0, separatorIndex + 1).orEmpty()
    val currentToken = remaining.substring(separatorIndex + 1)
    return (cueKeys + BuiltInWinCelebrationCueIds.GENERIC)
        .asSequence()
        .distinct()
        .filter { candidate -> candidate.startsWith(currentToken, ignoreCase = true) }
        .sorted()
        .map { candidate -> completedPrefix + candidate }
        .toList()
}
