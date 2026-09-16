package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.service.RiichiWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationCinematicTimeline
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinRoundContinuationMode
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricExhaustiveDrawSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricMatchSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationShowcaseScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewDefaults
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayoutFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.entity.Entity
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 建立 `/mahjongcraft debug` 底下的對局演出預覽子指令：`win`、`showcase`、
 * `exhaustive_draw_settlement`、`win_settlement`、`match_settlement`、`continuing_win` 與
 * `win_showcase_override`。
 *
 * 所有預覽都走正式的 presenter 與 scheduler，只是改用臨時生成、不掛在任何桌子／對局底下的 entity 與
 * 取樣資料，因此呼叫者不需要真的坐在桌邊或身處進行中的對局。`continuing_win` 與
 * `win_showcase_override` 例外：它們改寫的是本桌下一次胡牌的判定與演出覆寫狀態，因此要求呼叫者已入座。
 *
 * 臨時 entity 的清除時機刻意不統一：`win` 把清除掛在 [FabricWinCelebrationEffectScheduler] 的
 * `onComplete`，讓臨時牌與特效 entity 同刻消失並共用它的取消路徑；`showcase` 在 stage 同步保存完牌面與
 * 起始位置後立即 discard；只有結算預覽走 [DebugPreviewEntityLifecycle] 的到期佇列。
 *
 * @property debugWinRoundContinuationState 保存本桌的連莊判定覆寫模式。
 * @property debugWinShowcaseOverride 保存本桌下一次胡牌演出的 cue 覆寫。
 * @property effectScheduler 排定 `win` 胡牌慶祝演出的降臨特效。
 * @property showcaseScheduler 排定 `showcase` 的鞘翅煙火演出。
 * @property showcaseRegistry 解析與列舉可用的 showcase cue。
 * @property exhaustiveDrawSettlementScheduler 排定流局結算演出。
 * @property winSettlementScheduler 排定胡牌結算演出。
 * @property matchSettlementScheduler 排定終局排名演出。
 * @property exhaustiveDrawReasonDisplayNameRegistry 列舉可用的中止流局原因 ID。
 * @property playerTableScope 解析呼叫者目前入座的桌子。
 * @property tilePreviewSupport 解析牌面、生成臨時牌 entity 並提供 `tile` 引數節點。
 * @property layoutFactory 依呼叫者座標建立虛擬桌布局。
 * @property entityLifecycle 排定結算預覽臨時 entity 的到期清除。
 */
@Single
class FabricDebugPresentationCommand(
    private val debugWinRoundContinuationState: DebugWinRoundContinuationState,
    private val debugWinShowcaseOverride: DebugWinShowcaseOverride,
    private val effectScheduler: FabricWinCelebrationEffectScheduler,
    private val showcaseScheduler: FabricWinCelebrationShowcaseScheduler,
    private val showcaseRegistry: WinCelebrationShowcaseRegistry,
    private val exhaustiveDrawSettlementScheduler: FabricExhaustiveDrawSettlementPresentationScheduler,
    private val winSettlementScheduler: FabricWinSettlementPresentationScheduler,
    private val matchSettlementScheduler: FabricMatchSettlementPresentationScheduler,
    private val exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
    private val playerTableScope: DebugPlayerTableScope,
    private val tilePreviewSupport: DebugTilePreviewSupport,
    private val layoutFactory: DebugVirtualTableLayoutFactory,
    private val entityLifecycle: DebugPreviewEntityLifecycle,
) {
    /** 建立 `win <tsumo|ron> [tile]`：完整重播胡牌慶祝演出。 */
    fun buildWinCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(WIN_SUBCOMMAND)
        .then(tilePreviewSupport.withOptionalTileArgument(literal(TSUMO_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = true, tileArg) })
        .then(tilePreviewSupport.withOptionalTileArgument(literal(RON_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = false, tileArg) })

    /** 建立 `showcase`：單家、多家、指定 cue 與單一 phase 的鞘翅煙火預覽。 */
    fun buildShowcaseCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(SHOWCASE_SUBCOMMAND)
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
        )

    /** 建立 `exhaustive_draw_settlement`：一般、副露、九種九牌、分數變動與流局滿貫外的中止流局預覽。 */
    fun buildExhaustiveDrawSettlementCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(EXHAUSTIVE_DRAW_SETTLEMENT_SUBCOMMAND)
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
        .then(
            literal(MELDS_ARGUMENT).then(
                argument(MELD_COUNT_ARGUMENT, IntegerArgumentType.integer(1, MAX_DEBUG_MELD_COUNT))
                    .suggests(::suggestDebugMeldCounts)
                    .executes { context ->
                        previewSettlement(
                            context.source,
                            RiichiExhaustiveDrawReason.Normal.id,
                            tenpaiCount = 1,
                            meldCount = IntegerArgumentType.getInteger(context, MELD_COUNT_ARGUMENT),
                        )
                    },
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
        )

    /** 建立 `win_settlement`：自摸、榮和（可指定贏家數）、役滿、包牌役滿與流局滿貫的結算預覽。 */
    fun buildWinSettlementCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(WIN_SETTLEMENT_SUBCOMMAND)
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
        .then(literal(PAO_ARGUMENT).executes { context -> previewWinSettlement(context.source, WinSettlementPreview.PAO, 1) })
        .then(literal(NAGASHI_ARGUMENT).executes { context -> previewWinSettlement(context.source, WinSettlementPreview.NAGASHI, 1) })

    /** 建立 `match_settlement`：終局排名揭曉預覽，可指定玩家數。 */
    fun buildMatchSettlementCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(MATCH_SETTLEMENT_SUBCOMMAND)
        .executes { context -> previewMatchSettlement(context.source, 4) }
        .then(
            argument(PLAYER_COUNT_ARGUMENT, IntegerArgumentType.integer(2, 4))
                .executes { context ->
                    previewMatchSettlement(
                        context.source,
                        IntegerArgumentType.getInteger(context, PLAYER_COUNT_ARGUMENT),
                    )
                },
        )

    /** 建立 `continuing_win`：讀取或設定本桌的連莊判定覆寫模式。 */
    fun buildContinuingWinCommand(): LiteralArgumentBuilder<ServerCommandSource> = DebugWinRoundContinuationMode.entries.fold(
        literal(CONTINUING_WIN_SUBCOMMAND)
            .executes { context -> reportContinuingWinMode(context.source) },
    ) { node, mode ->
        node.then(
            literal(mode.name.lowercase()).executes { context ->
                setContinuingWinMode(context.source, mode)
            },
        )
    }

    /** 建立 `win_showcase_override`：指定或清除本桌下一次胡牌演出的 cue。 */
    fun buildWinShowcaseOverrideCommand(): LiteralArgumentBuilder<ServerCommandSource> = withOptionalCueArgument(
        literal(WIN_SHOWCASE_OVERRIDE_SUBCOMMAND).then(
            literal(CLEAR_ARGUMENT).executes { context -> clearWinShowcaseOverride(context.source) },
        ),
        allowMultiple = false,
    ) { source, cue -> armWinShowcaseOverride(source, cue) }

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
    private fun setContinuingWinMode(source: ServerCommandSource, mode: DebugWinRoundContinuationMode): Int = playerTableScope.runOnMain(source) { tableId ->
        debugWinRoundContinuationState.setMode(tableId, mode)
        "Continuing-win debug mode for table $tableId set to ${mode.name}"
    }

    /** 回報執行者目前所在那一桌的中途胡牌模式，並一併列出全伺服器其他仍有設定的桌子。 */
    private fun reportContinuingWinMode(source: ServerCommandSource): Int = playerTableScope.runOnMain(source) { tableId ->
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
    private fun armWinShowcaseOverride(source: ServerCommandSource, cueKey: String?): Int = playerTableScope.runOnMain(source) { tableId ->
        val resolvedCue = cueKey ?: DEFAULT_SHOWCASE_CUE
        if (debugWinShowcaseOverride.arm(tableId, resolvedCue)) {
            "Next win on table $tableId will play showcase cue $resolvedCue (presentation only, one-shot)"
        } else {
            "Showcase override is available in development environments only"
        }
    }

    /** 解除執行者目前所在那一桌尚未用掉的 showcase 覆寫武裝。 */
    private fun clearWinShowcaseOverride(source: ServerCommandSource): Int = playerTableScope.runOnMain(source) { tableId ->
        debugWinShowcaseOverride.clear(tableId)
        "Showcase override for table $tableId cleared"
    }

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
    /** 補全標準日麻允許的副露組數。 */
    private fun suggestDebugMeldCounts(
        @Suppress("UNUSED_PARAMETER") context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        (1..MAX_DEBUG_MELD_COUNT).forEach(builder::suggest)
        return builder.buildFuture()
    }

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
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val controllerPos = BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ)
        val tableId = Uuid.random()
        val wingTiles = cues.mapIndexed { seat, cue ->
            val tiles = List(DebugPreviewDefaults.RULE_CONFIG.initialHandSize) { index ->
                val asset = ALL_TILE_ASSET_KEYS.dropLast(1)[(index + seat * 5) % (ALL_TILE_ASSET_KEYS.size - 1)]
                val tile = tilePreviewSupport.spawnFreeTile(world, layout.handPlacement(seat, DebugPreviewDefaults.RULE_CONFIG.initialHandSize, index), MahjongTilePose.STANDING, asset)
                tile to asset
            }
            Triple(seat, cue, tiles)
        }
        val winningAsset = ALL_TILE_ASSET_KEYS.first()
        val winningPlacement = if (isTsumo) layout.drawnTilePlacement(DebugPreviewDefaults.RULE_CONFIG.initialHandSize) else layout.discardPlacement(0)
        val winningTile = tilePreviewSupport.spawnFreeTile(world, winningPlacement, MahjongTilePose.FACE_UP, winningAsset)
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
        meldCount: Int = 0,
    ): Int {
        if (':' !in reasonId) return COMMAND_FAILURE
        if (playerCount !in 2..4 || tenpaiCount !in 0..playerCount) return COMMAND_FAILURE
        if (meldCount !in 0..MAX_DEBUG_MELD_COUNT) return COMMAND_FAILURE
        val player = source.player ?: return COMMAND_FAILURE
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
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
        val previewMeldTiles = List(meldCount) { meldIndex ->
            List(DebugPreviewDefaults.MELD_TILE_COUNT) { tileIndex ->
                tilePreviewSupport.spawnFreeTile(
                    player.serverWorld,
                    layout.handPlacement(handSize = DebugPreviewDefaults.RULE_CONFIG.initialHandSize, tileIndex = tileIndex),
                    MahjongTilePose.FACE_UP,
                    ALL_TILE_ASSET_KEYS[(meldIndex * DebugPreviewDefaults.MELD_TILE_COUNT + tileIndex) % (ALL_TILE_ASSET_KEYS.size - 1)],
                )
            }
        }
        val previewMelds = previewMeldTiles.map { tiles ->
            MahjongMeldTileGroup(
                type = MeldType.PON,
                tileIds = tiles.map { it.uuid.toKotlinUuid() },
                calledTileId = tiles[1].uuid.toKotlinUuid(),
                sourceDirection = RelativeDirection.Across,
                allTilesFaceDown = false,
            )
        }
        layout.meldPlacements(previewMelds).forEach { (tileId, tilePlacement) ->
            previewMeldTiles.flatten().first { it.uuid.toKotlinUuid() == tileId }
                .refreshPositionAndAngles(tilePlacement.x, tilePlacement.y, tilePlacement.z, tilePlacement.yaw, 0.0f)
        }
        val reservedCornerWidths = if (previewMelds.isEmpty()) {
            emptyMap()
        } else {
            mapOf(DebugVirtualTableLayout.DEBUG_SEAT_INDEX to MahjongTileTableLayout.meldAreaWidth(previewMelds))
        }
        val previewTilesBySeat = List(playerCount) { seat ->
            val handSize = if (seat == DebugVirtualTableLayout.DEBUG_SEAT_INDEX && meldCount > 0) {
                maxOf(MIN_DEBUG_CONCEALED_HAND_SIZE, DebugPreviewDefaults.RULE_CONFIG.initialHandSize - meldCount * DebugPreviewDefaults.MELD_TILE_COUNT)
            } else {
                DebugPreviewDefaults.RULE_CONFIG.initialHandSize
            }
            val cornerYieldShift = MahjongTileTableLayout.handCornerYieldShift(
                handSize = handSize,
                reservedCornerWidth = reservedCornerWidths[seat] ?: 0.0,
            )
            val assets = if (proof && seat == 0) {
                KYUUSHU_PREVIEW_ASSETS
            } else {
                List(handSize) { index ->
                    ALL_TILE_ASSET_KEYS[(seat * 7 + index) % (ALL_TILE_ASSET_KEYS.size - 1)]
                }
            }
            assets.mapIndexed { index, asset ->
                tilePreviewSupport.spawnFreeTile(
                    player.serverWorld,
                    layout.handPlacement(seat, assets.size, index, cornerYieldShift),
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
            tableFacing = layout.tableFacing,
            placement = layout.showcaseStagePlacement(),
            request = ExhaustiveDrawSettlementPresentationRequest(reasonId, players),
            waitingTileAssetsBySeat = players.filter { it.handPresentation == ExhaustiveDrawSettlementHandPresentation.REVEAL_TENPAI }
                .associate { it.ranking.seatIndex to DEFAULT_WAITING_TILE_ASSETS },
            revealedTileAssetsById = revealedAssetsById,
            reservedCornerWidthsBySeat = reservedCornerWidths,
        ) ?: return COMMAND_FAILURE
        entityLifecycle.schedule(
            player.serverWorld,
            end,
            previewTilesBySeat.flatten().map(Pair<MahjongTileEntity, String>::first) + previewMeldTiles.flatten(),
        )
        source.sendFeedback({ net.minecraft.text.Text.literal("Exhaustive draw settlement preview active until game time $end") }, false)
        return COMMAND_SUCCESS
    }

    /** 走正式 scheduler 預覽末位至第一名的終局揭曉。 */
    private fun previewMatchSettlement(source: ServerCommandSource, playerCount: Int): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = layoutFactory.create(player.blockX, player.blockY, player.blockZ, player.horizontalFacing.toMahjongTableFacing())
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
        val layout = layoutFactory.create(player.blockX, player.blockY, player.blockZ, player.horizontalFacing.toMahjongTableFacing())
        val playerIds = List(4) { index -> if (index == 0) player.uuid.toKotlinUuid() else Uuid.random() }
        val tileAssetsById = mutableMapOf<Uuid, String>()
        val winners = List(winnerCount) { winnerIndex ->
            val handIds = List(DebugPreviewDefaults.RULE_CONFIG.initialHandSize - 7) { tileIndex ->
                Uuid.random().also { tileAssetsById[it] = ALL_TILE_ASSET_KEYS[(winnerIndex * 7 + tileIndex) % (ALL_TILE_ASSET_KEYS.size - 1)] }
            }
            val ponIds = List(3) { Uuid.random().also { id -> tileAssetsById[id] = "s3" } }
            val kanIds = List(4) { Uuid.random().also { id -> tileAssetsById[id] = "p8" } }
            val winningTileId = Uuid.random().also { tileAssetsById[it] = if (winnerIndex == 0) "m1" else "p${winnerIndex + 1}" }
            val yakuman = preview == WinSettlementPreview.YAKUMAN || preview == WinSettlementPreview.PAO
            // 自摸與流局滿貫沒有放銃者；只有榮和（含役滿榮和）才歸咎到特定玩家。
            val dealsIn = preview == WinSettlementPreview.RON || yakuman
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
                responsiblePlayerId = if (dealsIn) playerIds.getOrNull(winnerCount) else null,
                totalScore = if (yakuman) {
                    32_000
                } else if (preview == WinSettlementPreview.NAGASHI) {
                    8_000
                } else {
                    7_700
                },
                standingTileIds = handIds,
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
        // 包牌預覽：座位 0 役滿榮和 32000，座位 1 放銃、座位 2 為包牌責任者，各付一半；其餘預覽沿用固定示意分數。
        val (scoresBefore, scoresAfter, ranksAfter) = if (preview == WinSettlementPreview.PAO) {
            Triple(listOf(25_000, 25_000, 25_000, 25_000), listOf(57_000, 9_000, 9_000, 25_000), listOf(1, 3, 4, 2))
        } else {
            Triple(listOf(31_000, 28_000, 24_000, 17_000), listOf(26_000, 23_000, 19_000, 32_000), listOf(2, 3, 4, 1))
        }
        val ranking = ScoreRankingPresentation(
            scoresBefore.zip(scoresAfter).mapIndexed { seatIndex, (before, after) ->
                ScoreRankingPlayer(playerIds[seatIndex], seatIndex, seatIndex != 0, before, after, seatIndex + 1, ranksAfter[seatIndex])
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
            // preview 目前只示範 riichi 規則的兩種一般胡牌，以及其特有的流局滿貫（riichi 自訂的「胡牌等效」特殊結果）。
            // 這些結果一律對應 RiichiWinSettlementDetailResolver.TEMPLATE_KEY；
            // WinSettlementPresentationRequestFactory.GENERIC_TEMPLATE_KEY 是給未登記 resolver 的規則模組
            // （例如第三方擴充）使用的後備值，此指令目前沒有對應的 preview 變體可以示範。
            templateKey = RiichiWinSettlementDetailResolver.TEMPLATE_KEY,
            isTsumo = preview == WinSettlementPreview.TSUMO,
            winners = winners,
            ranking = ranking,
            paymentReasonIdsByPlayerId = if (preview == WinSettlementPreview.PAO) {
                mapOf(playerIds[2] to BuiltInPaymentReasonIds.PAO)
            } else {
                emptyMap()
            },
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
        val assetKey = tilePreviewSupport.resolveAssetKey(tileArg)
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val handSize = DebugPreviewDefaults.RULE_CONFIG.initialHandSize
        val handPlacements = (0 until handSize).map { slot -> layout.handPlacement(handSize = handSize, tileIndex = slot) }
        val sortedPlacements = if (isTsumo) {
            listOf(layout.drawnTilePlacement(standingTileCount = handSize)) + handPlacements
        } else {
            handPlacements
        }
        val handTiles = sortedPlacements.shuffled().map { shuffledPlacement ->
            tilePreviewSupport.spawnFreeTile(world, shuffledPlacement, MahjongTilePose.STANDING, assetKey)
        }

        val reorderStartGameTime = world.time
        val reorderEndGameTime = reorderStartGameTime + MahjongTileTableLayout.WIN_REORDER_FLIGHT_DURATION_TICKS
        handTiles.forEachIndexed { index, tile ->
            TileAnimationSteps.scheduleReorder(
                tile,
                sortedPlacements[index],
                reorderStartGameTime,
            )
        }

        val winningTile: MahjongTileEntity
        val handLaydownEndGameTime: Long
        if (isTsumo) {
            winningTile = handTiles.first()
            val winTileLaydownStartGameTime = reorderEndGameTime
            val winTileLaydownEndGameTime = winTileLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            TileAnimationSteps.scheduleLaydown(winningTile, winTileLaydownStartGameTime, playGroupSound = true)
            val restLaydownStartGameTime = winTileLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
            val restTiles = handTiles - winningTile
            restTiles.forEachIndexed { index, tile ->
                TileAnimationSteps.scheduleLaydown(tile, restLaydownStartGameTime, playGroupSound = index == restTiles.size / 2)
            }
            handLaydownEndGameTime = restLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        } else {
            val discardedPlacement = layout.discardPlacement(discardIndex = 0)
            winningTile = tilePreviewSupport.spawnFreeTile(world, discardedPlacement, MahjongTilePose.FACE_UP, assetKey)
            val handLaydownStartGameTime = reorderEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
            handTiles.forEachIndexed { index, tile ->
                TileAnimationSteps.scheduleLaydown(tile, handLaydownStartGameTime, playGroupSound = index == handTiles.size / 2)
            }
            handLaydownEndGameTime = handLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        }

        val effectStartGameTime = handLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_EFFECT_DELAY_TICKS
        val effectEndGameTime = effectStartGameTime + MahjongTileTableLayout.WIN_EFFECT_DURATION_TICKS
        val allSpawnedTiles = if (isTsumo) handTiles else handTiles + winningTile
        effectScheduler.schedule(
            world = world,
            tableId = Uuid.random(),
            targetTileId = winningTile.uuid.toKotlinUuid(),
            startGameTime = effectStartGameTime,
            endGameTime = effectEndGameTime,
            onComplete = { allSpawnedTiles.forEach(Entity::discard) },
        )
        return COMMAND_SUCCESS
    }

    /** `win_settlement` 的固定測試情境。 */
    private enum class WinSettlementPreview {
        RON,
        TSUMO,
        YAKUMAN,
        PAO,
        NAGASHI,
    }

    private companion object {
        /** `win` 子指令 literal。 */
        const val WIN_SUBCOMMAND: String = "win"

        /** `showcase` 子指令 literal。 */
        const val SHOWCASE_SUBCOMMAND: String = "showcase"

        /** `showcase multi_ron` literal。 */
        const val MULTI_RON_ARGUMENT: String = "multi_ron"

        /** 贏家數量引數名稱。 */
        const val WINNER_COUNT_ARGUMENT: String = "winner_count"

        /** showcase cue 引數名稱。 */
        const val CUE_ARGUMENT: String = "cue"

        /** `showcase phase` literal。 */
        const val PHASE_ARGUMENT: String = "phase"

        /** showcase phase 名稱引數。 */
        const val PHASE_NAME_ARGUMENT: String = "phase_name"

        /** 省略 cue 時使用的預設演出。 */
        val DEFAULT_SHOWCASE_CUE: String = BuiltInWinCelebrationCueIds.riichiYakuman("kokushi_musou")

        /** 自摸 literal。 */
        const val TSUMO_ARGUMENT: String = "tsumo"

        /** 榮和 literal。 */
        const val RON_ARGUMENT: String = "ron"

        /** `continuing_win` 子指令 literal。 */
        const val CONTINUING_WIN_SUBCOMMAND: String = "continuing_win"

        /** `win_showcase_override` 子指令 literal。 */
        const val WIN_SHOWCASE_OVERRIDE_SUBCOMMAND: String = "win_showcase_override"

        /** 清除覆寫的 literal。 */
        const val CLEAR_ARGUMENT: String = "clear"

        /** `exhaustive_draw_settlement` 子指令 literal。 */
        const val EXHAUSTIVE_DRAW_SETTLEMENT_SUBCOMMAND: String = "exhaustive_draw_settlement"

        /** `win_settlement` 子指令 literal。 */
        const val WIN_SETTLEMENT_SUBCOMMAND: String = "win_settlement"

        /** `match_settlement` 子指令 literal。 */
        const val MATCH_SETTLEMENT_SUBCOMMAND: String = "match_settlement"

        /** 一般流局 literal。 */
        const val NORMAL_ARGUMENT: String = "normal"

        /** 副露布局預覽 literal。 */
        const val MELDS_ARGUMENT: String = "melds"

        /** 副露組數引數名稱。 */
        const val MELD_COUNT_ARGUMENT: String = "meld_count"

        /** 九種九牌流局 literal。 */
        const val KYUUSHU_ARGUMENT: String = "kyuushu"

        /** 役滿結算 literal。 */
        const val YAKUMAN_ARGUMENT: String = "yakuman"

        /** 流局滿貫結算 literal。 */
        const val NAGASHI_ARGUMENT: String = "nagashi"

        /** 包牌役滿結算預覽 literal。 */
        const val PAO_ARGUMENT: String = "pao"

        /** 中止流局 literal。 */
        const val ABORTIVE_ARGUMENT: String = "abortive"

        /** 分數變動預覽 literal。 */
        const val SCORE_ARGUMENT: String = "score"

        /** 分數變動量引數名稱。 */
        const val SCORE_DELTA_ARGUMENT: String = "delta"

        /** 聽牌人數引數名稱。 */
        const val TENPAI_COUNT_ARGUMENT: String = "tenpai_count"

        /** 玩家人數引數名稱。 */
        const val PLAYER_COUNT_ARGUMENT: String = "player_count"

        /** 流局原因 ID 引數名稱。 */
        const val REASON_ARGUMENT: String = "reason"

        /** 省略參數時使用的分數變動量。 */
        const val DEFAULT_SCORE_DELTA: Int = 9_000

        /** 可單獨重播的 showcase 階段名稱。 */
        val SHOWCASE_PHASES: List<String> = listOf("launch", "orbit", "place", "ignite", "explode", "reveal")

        /** 結算預覽中每位玩家的固定聽牌張。 */
        val DEFAULT_WAITING_TILE_ASSETS: List<String> = listOf("m1", "m4", "m7")

        /** 九種九牌流局預覽使用的固定手牌。 */
        val KYUUSHU_PREVIEW_ASSETS: List<String> = listOf(
            "m1", "m9", "p1", "p9", "s1", "s9", "east", "south", "west", "north", "red_dragon", "green_dragon", "white_dragon", "m1",
        )

        /** 內建規則目前需要驗證的最大副露組數。 */
        const val MAX_DEBUG_MELD_COUNT: Int = 5

        /** 副露布局預覽至少保留的暗手張數。 */
        const val MIN_DEBUG_CONCEALED_HAND_SIZE: Int = 1

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
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
