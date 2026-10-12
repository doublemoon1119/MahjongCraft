package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawHandDisclosure
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationCinematicTimeline
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.showcaseWingCards
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.STANDARD_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.winningHandFaceDownIndices
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
 * @property debugWinRoundContinuationState 保存本桌胡牌後本局繼續的判定覆寫模式，不是莊家連莊設定。
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
    /** 預覽排列牌面時輪流使用的牌面，見 [DebugTilePreviewSupport.previewAssetKeys]。 */
    private val previewAssets: List<String> get() = tilePreviewSupport.previewAssetKeys

    /**
     * 建立 `win <tsumo|ron> [tile]`：完整重播胡牌慶祝演出。
     *
     * @return 已包含引數與執行處理的子指令節點，供上層指令註冊。
     */
    fun buildWinCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(WIN_SUBCOMMAND)
        .then(tilePreviewSupport.withOptionalTileArgument(literal(TSUMO_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = true, tileArg) })
        .then(tilePreviewSupport.withOptionalTileArgument(literal(RON_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = false, tileArg) })

    /**
     * 建立 `showcase`：單家、多家、指定 cue、單一 phase 與含副露的鞘翅煙火預覽。
     *
     * @return 已包含引數與執行處理的子指令節點，供上層指令註冊。
     */
    fun buildShowcaseCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(SHOWCASE_SUBCOMMAND)
        .then(
            literal(MELDS_ARGUMENT)
                .executes { context -> previewMeldShowcase(context.source, listOf(MELD_SHOWCASE_CUE)) }
                .then(
                    argument(WINNER_COUNT_ARGUMENT, IntegerArgumentType.integer(1, 3))
                        .executes { context ->
                            val count = IntegerArgumentType.getInteger(context, WINNER_COUNT_ARGUMENT)
                            previewMeldShowcase(context.source, List(count) { MELD_SHOWCASE_CUE })
                        }
                        .then(
                            argument(CUE_ARGUMENT, StringArgumentType.greedyString())
                                .suggests(::suggestShowcaseCueList)
                                .executes { context ->
                                    val count = IntegerArgumentType.getInteger(context, WINNER_COUNT_ARGUMENT)
                                    val supplied = StringArgumentType.getString(context, CUE_ARGUMENT).split(",").filter(String::isNotBlank)
                                    previewMeldShowcase(context.source, expandShowcaseCues(supplied, count, MELD_SHOWCASE_CUE))
                                },
                        ),
                ),
        )
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

    /**
     * 建立 `exhaustive_draw_settlement`：一般、副露、九種九牌、分數變動與流局滿貫外的中止流局預覽。
     *
     * @return 已包含引數與執行處理的子指令節點，供上層指令註冊。
     */
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

    /**
     * 建立 `win_settlement`：自摸、榮和（可指定贏家數）、役滿、包牌役滿與流局滿貫的結算預覽。
     *
     * @return 已包含引數與執行處理的子指令節點，供上層指令註冊。
     */
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

    /**
     * 建立 `match_settlement`：終局排名揭曉預覽，可指定玩家數。
     *
     * @return 已包含引數與執行處理的子指令節點，供上層指令註冊。
     */
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

    /**
     * 建立 `continuing_win`：讀取或設定本桌胡牌後本局繼續的覆寫模式。
     *
     * @return 已包含引數與執行處理的子指令節點，供上層指令註冊。
     */
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

    /**
     * 建立 `win_showcase_override`：指定或清除本桌下一次胡牌演出的 cue。
     *
     * @return 已包含引數與執行處理的子指令節點，供上層指令註冊。
     */
    fun buildWinShowcaseOverrideCommand(): LiteralArgumentBuilder<ServerCommandSource> = withOptionalCueArgument(
        literal(WIN_SHOWCASE_OVERRIDE_SUBCOMMAND).then(
            literal(CLEAR_ARGUMENT).executes { context -> clearWinShowcaseOverride(context.source) },
        ),
        allowMultiple = false,
    ) { source, cue -> armWinShowcaseOverride(source, cue) }

    /**
     * 設定發起者已入座桌子的胡牌後流程覆寫模式；不生成預覽實體，也不影響其他桌。
     *
     * @param source 指令發起來源。
     * @param mode 本桌要採用的中途胡牌模式。
     * @return 指令執行狀態碼。
     */
    private fun setContinuingWinMode(source: ServerCommandSource, mode: DebugWinRoundContinuationMode): Int = playerTableScope.runOnMain(source) { tableId ->
        debugWinRoundContinuationState.setMode(tableId, mode)
        "Continuing-win debug mode for table $tableId set to ${mode.name}"
    }

    /**
     * 回報本桌中途胡牌模式與其他仍有設定的桌子。
     *
     * @param source 指令發起來源。
     * @return 指令執行狀態碼。
     */
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
     *
     * @param source 指令發起來源。
     * @param cueKey 展示理由識別碼；null 時使用預設演出。
     * @return 指令執行狀態碼。
     */
    private fun armWinShowcaseOverride(source: ServerCommandSource, cueKey: String?): Int = playerTableScope.runOnMain(source) { tableId ->
        val resolvedCue = cueKey ?: DEFAULT_SHOWCASE_CUE
        if (debugWinShowcaseOverride.arm(tableId, resolvedCue)) {
            "Next win on table $tableId will play showcase cue $resolvedCue (presentation only, one-shot)"
        } else {
            "Showcase override is available in development environments only"
        }
    }

    /**
     * 清除本桌尚未消耗的演出覆寫。
     *
     * @param source 指令發起來源。
     * @return 指令執行狀態碼。
     */
    private fun clearWinShowcaseOverride(source: ServerCommandSource): Int = playerTableScope.runOnMain(source) { tableId ->
        debugWinShowcaseOverride.clear(tableId)
        "Showcase override for table $tableId cleared"
    }

    /**
     * 為演出指令加入可省略的展示理由引數。
     *
     * @param node 要擴充的指令節點。
     * @param allowMultiple 是否接受逗號分隔清單，否則要求單一識別碼。
     * @param onExecute 以來源與可空引數執行指令的回呼。
     * @return 加入引數與執行回呼的原節點。
     */
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

    /**
     * 列出單一展示理由的 Tab 補全候選。
     *
     * @param context 指令情境，目前不參與候選篩選。
     * @param builder 接收補全候選的建構器。
     * @return 非同步補全結果。
     */
    private fun suggestSingleShowcaseCue(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestShowcaseCues(context, builder, allowMultiple = false)

    /**
     * 補全逗號分隔展示理由清單的最後一段。
     *
     * @param context 指令情境，目前不參與候選篩選。
     * @param builder 接收補全候選的建構器。
     * @return 保留已輸入前綴的非同步補全結果。
     */
    private fun suggestShowcaseCueList(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestShowcaseCues(context, builder, allowMultiple = true)

    /**
     * 從正式登錄表建立展示理由補全，不另維護識別碼清單。
     *
     * @param context 指令情境，目前不參與候選篩選。
     * @param builder 含未完成輸入的補全建構器。
     * @param allowMultiple 是否只補全逗號清單的最後一段。
     * @return 非同步補全結果。
     */
    private fun suggestShowcaseCues(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
        allowMultiple: Boolean,
    ): CompletableFuture<Suggestions> {
        buildShowcaseCueSuggestions(builder.remaining, showcaseRegistry.cueKeys, allowMultiple).forEach(builder::suggest)
        return builder.buildFuture()
    }

    /**
     * 補全 TNT 演出時間線可跳轉的固定階段。
     *
     * @param context 指令情境，目前不參與候選篩選。
     * @param builder 含未完成階段名稱的補全建構器。
     * @return 非同步補全結果。
     */
    private fun suggestShowcasePhases(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        SHOWCASE_PHASES.filter { it.startsWith(builder.remaining, ignoreCase = true) }.forEach(builder::suggest)
        return builder.buildFuture()
    }

    /**
     * 補全此預覽工具允許的副露組數；上限涵蓋五組副露的版面驗證，不代表標準日麻規則上限。
     *
     * @param context 指令情境，目前不參與候選篩選。
     * @param builder 接收組數候選的補全建構器。
     * @return 非同步補全結果。
     */
    private fun suggestDebugMeldCounts(
        @Suppress("UNUSED_PARAMETER") context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        (1..MAX_DEBUG_MELD_COUNT).forEach(builder::suggest)
        return builder.buildFuture()
    }

    /**
     * 補全已登記的途中流局原因，排除另有入口的一般流局與九種九牌。
     *
     * @param context 指令情境，目前不參與候選篩選。
     * @param builder 含未完成原因識別碼的補全建構器。
     * @return 依識別碼排序的非同步補全結果。
     */
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

    /**
     * 在玩家面前生成完整鞘翅煙火演出。
     *
     * @param source 指令發起來源。
     * @param isTsumo 是否採用自摸的胡牌張位置。
     * @param cues 依贏家順序排列的展示理由識別碼。
     * @param initialElapsedTicks 演出起點已經過的 tick 數，用於跳轉階段。
     * @return 成功排定時為成功狀態碼，識別碼、玩家或排程不可用時為失敗狀態碼。
     */
    private fun previewShowcase(source: ServerCommandSource, isTsumo: Boolean, cues: List<String>, initialElapsedTicks: Int = 0): Int {
        if (cues.any { showcaseRegistry.find(it) == null }) return COMMAND_FAILURE
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val controllerPos = BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ)
        val tableId = Uuid.random()
        val wingTiles = cues.mapIndexed { seat, cue ->
            val tiles = List(DebugPreviewDefaults.RULE_CONFIG.initialHandSize) { index ->
                val asset = previewAssets[(index + seat * 5) % previewAssets.size]
                val tile = tilePreviewSupport.spawnFreeTile(world, layout.handPlacement(seat, DebugPreviewDefaults.RULE_CONFIG.initialHandSize, index), MahjongTilePose.STANDING, asset)
                tile to asset
            }
            Triple(seat, cue, tiles)
        }
        val winningAsset = STANDARD_TILE_ASSET_KEYS.first()
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
                FabricWinCelebrationShowcaseScheduler.Wing(
                    seatIndex = seat,
                    cueIds = listOf(cue),
                    cards = tiles.map { (tile, asset) -> FabricWinCelebrationShowcaseScheduler.Card(tileId = tile.uuid.toKotlinUuid(), assetKey = asset) },
                    standYaw = MahjongTileTableLayout.seatYaw(layout.tableFacing, seat),
                )
            },
        ) ?: return COMMAND_FAILURE
        // Stage 已同步保存所有牌面與起始位置；debug 臨時牌不必繼續 tick 到演出結束。
        (wingTiles.flatMap { it.third.map(Pair<MahjongTileEntity, String>::first) } + winningTile).forEach(Entity::discard)
        return COMMAND_SUCCESS
    }

    /**
     * `showcase melds [winner_count] [cues]`：每位贏家都是單騎和牌，副露依序為吃（上家）、加槓（對家）、暗槓與大明槓（下家）。
     *
     * 一位贏家時為自摸，多位時為同一張捨牌的多家榮和。驗證副露起飛：橫置的鳴取牌與加槓疊上的牌在站起來時轉正，
     * 暗槓兩端露出牌背，編隊中各組之間留空隙，以及接近最多張數時多翼並排的寬度。
     *
     * @param source 指令發起來源。
     * @param cues 依贏家順序排列的展示理由識別碼。
     * @return 指令執行狀態碼。
     */
    private fun previewMeldShowcase(source: ServerCommandSource, cues: List<String>): Int {
        if (cues.any { showcaseRegistry.find(it) == null }) return COMMAND_FAILURE
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val isTsumo = cues.size == 1
        val standingAssets = listOf(MELD_SHOWCASE_WAIT_ASSET)
        val spawned = mutableListOf<MahjongTileEntity>()
        val wings = cues.mapIndexed { seat, cue ->
            val assets = mutableMapOf<Uuid, String>()

            /**
             * 建立牌張識別碼並登記其預覽素材。
             *
             * @param tileAssets 依順序排列的牌面素材識別字。
             * @return 對應的隨機牌張識別碼。
             */
            fun ids(vararg tileAssets: String): List<Uuid> = tileAssets.map { asset -> Uuid.random().also { assets[it] = asset } }
            val standing = ids(*standingAssets.toTypedArray())
            val chi = ids("s1", "s2", "s3")
            val pon = ids("p5", "p5", "p5")
            val addedKan = pon + ids("p5")
            val closedKan = ids("east", "east", "east", "east")
            val openKan = ids("m9", "m9", "m9", "m9")
            val melds = listOf(
                MahjongMeldTileGroup(MeldType.CHI, chi, chi.first(), RelativeDirection.Left, allTilesFaceDown = false),
                MahjongMeldTileGroup(MeldType.ADDED_KAN, addedKan, pon.first(), RelativeDirection.Across, allTilesFaceDown = false),
                MahjongMeldTileGroup(MeldType.CLOSED_KAN, closedKan, null, RelativeDirection.Self, allTilesFaceDown = false),
                MahjongMeldTileGroup(MeldType.OPEN_KAN, openKan, openKan.first(), RelativeDirection.Right, allTilesFaceDown = false),
            )
            val closedKanFaceDown = winningHandFaceDownIndices(MeldType.CLOSED_KAN, closedKan.size).map(closedKan::get).toSet()
            val placements = layout.meldPlacements(melds, seat) +
                standing.mapIndexed { index, id -> id to layout.handPlacement(seat, standing.size, index) }
            val entityIds = placements.mapValues { (id, placement) ->
                val pose = when {
                    id in standing -> MahjongTilePose.STANDING
                    id in closedKanFaceDown -> MahjongTilePose.FACE_DOWN
                    else -> MahjongTilePose.FACE_UP
                }
                tilePreviewSupport.spawnFreeTile(world, placement, pose, assets.getValue(id)).also(spawned::add).uuid.toKotlinUuid()
            }
            FabricWinCelebrationShowcaseScheduler.Wing(
                seatIndex = seat,
                cueIds = listOf(cue),
                cards = showcaseWingCards(standingTileIds = standing, melds = melds, assetKeyOf = assets::get)
                    .map { it.copy(tileId = entityIds.getValue(it.tileId)) },
                standYaw = MahjongTileTableLayout.seatYaw(layout.tableFacing, seat),
            )
        }
        val winningPlacement = if (isTsumo) layout.drawnTilePlacement(standingAssets.size) else layout.discardPlacement(0)
        val winningTile = tilePreviewSupport.spawnFreeTile(world, winningPlacement, MahjongTilePose.FACE_UP, MELD_SHOWCASE_WAIT_ASSET)
        showcaseScheduler.schedule(
            world = world,
            tableId = Uuid.random(),
            controllerPos = BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ),
            stagePlacement = layout.showcaseStagePlacement(),
            startGameTime = world.time,
            winningTileId = winningTile.uuid.toKotlinUuid(),
            winningTileAssetKey = MELD_SHOWCASE_WAIT_ASSET,
            wings = wings,
        ) ?: return COMMAND_FAILURE
        // Stage 已同步保存所有牌面與起始位置；debug 臨時牌不必繼續 tick 到演出結束。
        (spawned + winningTile).forEach(Entity::discard)
        return COMMAND_SUCCESS
    }

    /**
     * 直接從指定演出階段開始預覽。
     *
     * @param source 指令發起來源。
     * @param phase 要跳轉的固定階段名稱。
     * @param cue 展示理由識別碼。
     * @return 指令執行狀態碼；未知階段會失敗。
     */
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

    /**
     * 使用正式持久化舞台預覽流局排行榜；分數與牌面是示意資料，不執行規則結算。
     *
     * @param source 指令發起來源。
     * @param reasonId 流局原因識別碼。
     * @param tenpaiCount 要顯示為聽牌的玩家數。
     * @param proof 是否為第一位玩家公開九種九牌證明手牌。
     * @param scoreDelta 末位玩家取得、其餘玩家分攤的示意分數變動。
     * @param playerCount 預覽玩家數。
     * @param meldCount 第一位玩家的示意副露組數。
     * @return 指令執行狀態碼。
     */
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
                    previewAssets[(meldIndex * DebugPreviewDefaults.MELD_TILE_COUNT + tileIndex) % previewAssets.size],
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
                    previewAssets[(seat * 7 + index) % previewAssets.size]
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
            val handDisclosure = when {
                proof && seat == 0 -> ExhaustiveDrawHandDisclosure.PROOF
                seat < tenpaiCount -> ExhaustiveDrawHandDisclosure.TENPAI
                else -> ExhaustiveDrawHandDisclosure.CONCEALED
            }
            val handTiles = previewTilesBySeat[seat]
            if (handDisclosure != ExhaustiveDrawHandDisclosure.CONCEALED) {
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
                handDisclosure = handDisclosure,
                revealedHandTileIds = if (handDisclosure == ExhaustiveDrawHandDisclosure.CONCEALED) emptyList() else handTiles.map { it.first.uuid.toKotlinUuid() },
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
            waitingTileAssetsBySeat = players.filter { it.handDisclosure == ExhaustiveDrawHandDisclosure.TENPAI }
                .associate { it.ranking.seatIndex to DEFAULT_WAITING_TILE_ASSETS },
            revealedTileAssetsById = revealedAssetsById,
            reservedCornerWidthsBySeat = reservedCornerWidths,
        ) ?: return COMMAND_FAILURE
        entityLifecycle.schedule(
            player.serverWorld,
            end,
            previewTilesBySeat.flatten().map(Pair<MahjongTileEntity, String>::first) + previewMeldTiles.flatten(),
        )
        source.sendFeedback({ Text.literal("Exhaustive draw settlement preview active until game time $end") }, false)
        return COMMAND_SUCCESS
    }

    /**
     * 使用正式排程器預覽末位至第一名的終局揭曉。
     *
     * @param source 指令發起來源。
     * @param playerCount 已由指令驗證的玩家數。
     * @return 指令執行狀態碼。
     */
    private fun previewMatchSettlement(source: ServerCommandSource, playerCount: Int): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = layoutFactory.create(player.blockX, player.blockY, player.blockZ, player.horizontalFacing.toMahjongTableFacing())
        val scores = listOf(120_000, 45_000, 25_000, -12_000).take(playerCount)
        val playerIds = List(playerCount) { index -> if (index == 0) player.uuid.toKotlinUuid() else Uuid.random() }
        val request = MatchSettlementPresentationRequest(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
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

    /**
     * 使用正式排程器預覽胡牌結算與排行；役種、手牌與分數皆為版面測試資料，不保證構成合法和牌。
     *
     * @param source 指令發起來源。
     * @param preview 要呈現的結算情境。
     * @param winnerCount 已由指令驗證的贏家數。
     * @return 指令執行狀態碼。
     */
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
                Uuid.random().also { tileAssetsById[it] = previewAssets[(winnerIndex * 7 + tileIndex) % previewAssets.size] }
            }
            val ponIds = List(3) { Uuid.random().also { id -> tileAssetsById[id] = "s3" } }
            val kanIds = List(4) { Uuid.random().also { id -> tileAssetsById[id] = "p8" } }
            val winningTileId = Uuid.random().also { tileAssetsById[it] = if (winnerIndex == 0) "m1" else "p${winnerIndex + 1}" }
            val yakuman = preview == WinSettlementPreview.YAKUMAN || preview == WinSettlementPreview.PAO
            // 自摸與流局滿貫沒有放銃者；只有榮和（含役滿榮和）才歸咎到特定玩家。
            val dealsIn = preview == WinSettlementPreview.RON || yakuman
            val regularYakuEntries = if (preview == WinSettlementPreview.RON && winnerIndex == 0) {
                listOf(
                    YakuType.Riichi to 1,
                    YakuType.Ippatsu to 1,
                    YakuType.Menzentsumo to 1,
                    YakuType.Pinfu to 1,
                    YakuType.Tanyao to 1,
                    YakuType.Iipeikou to 1,
                    YakuType.SanshokuDoujun to 2,
                    YakuType.Ittuitsu to 2,
                    YakuType.Honchan to 2,
                    YakuType.Toitoi to 2,
                    YakuType.Honitsu to 3,
                    YakuType.Chinitsu to 6,
                )
            } else {
                listOf(
                    YakuType.Riichi to 1,
                    YakuType.Ippatsu to 1,
                    YakuType.Pinfu to 1,
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
                            RiichiWinSettlementIds.YAKU_FIELD,
                            WinSettlementDetailValue.Entries(
                                if (yakuman) {
                                    listOf(
                                        RiichiWinSettlementIds.yakumanEntry(YakuType.KokushiMusou13, 2),
                                        RiichiWinSettlementIds.yakumanEntry(YakuType.Daisangen, 1),
                                    )
                                } else if (preview != WinSettlementPreview.NAGASHI) {
                                    regularYakuEntries.map { (type, han) -> RiichiWinSettlementIds.yakuEntry(type, han) }
                                } else {
                                    listOf(WinSettlementDetailEntry(RiichiWinSettlementIds.NAGASHI_MANGAN))
                                },
                            ),
                        ),
                    )
                    if (!yakuman && preview != WinSettlementPreview.NAGASHI) {
                        val totalHan = regularYakuEntries.sumOf { (_, han) -> han }
                        add(
                            WinSettlementDetailField(
                                RiichiWinSettlementIds.HAN_FU_FIELD,
                                WinSettlementDetailValue.Quantities(
                                    listOf(
                                        WinSettlementQuantity(RiichiWinSettlementIds.HAN, totalHan),
                                        WinSettlementQuantity(RiichiWinSettlementIds.FU, PREVIEW_FU),
                                    ),
                                ),
                            ),
                        )
                    } else if (yakuman) {
                        add(
                            WinSettlementDetailField(
                                RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD,
                                WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, PREVIEW_YAKUMAN_MULTIPLIER))),
                            ),
                        )
                    }
                    add(WinSettlementDetailField(RiichiWinSettlementIds.DORA_FIELD, WinSettlementDetailValue.Tiles(handIds.take(2))))
                    add(WinSettlementDetailField(RiichiWinSettlementIds.URA_DORA_FIELD, WinSettlementDetailValue.Tiles(handIds.drop(2).take(1))))
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
                RiichiRoundOutcomeIds.NAGASHI_MANGAN
            } else if (preview == WinSettlementPreview.TSUMO) {
                BuiltInRoundOutcomeIds.TSUMO
            } else {
                BuiltInRoundOutcomeIds.RON
            },
            // preview 只示範日麻的兩種一般胡牌與流局滿貫，平台依規則選用日麻模板。
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
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
     * 以臨時牌預覽理牌、倒牌與胡牌特效。自摸另生成獨立和牌張，榮和則在牌河位置生成和牌張；特效結束後清除臨時實體。
     *
     * @param source 指令發起來源。
     * @param isTsumo 是否採用自摸時間線。
     * @param tileArg 牌面素材引數；null 時由預覽工具選用預設值。
     * @return 指令執行狀態碼。
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
        /** 一般榮和。 */
        RON,

        /** 一般自摸。 */
        TSUMO,

        /** 役滿榮和。 */
        YAKUMAN,

        /** 含包牌責任的役滿榮和。 */
        PAO,

        /** 流局滿貫。 */
        NAGASHI,
    }

    /** 演出預覽的子指令名稱、引數名稱與取樣設定。 */
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

        /** `showcase melds` 省略 cue 時使用的演出。 */
        val MELD_SHOWCASE_CUE: String = BuiltInWinCelebrationCueIds.riichiYakuman("sukantsu")

        /** `showcase melds` 的單騎待牌，也是和牌張。 */
        const val MELD_SHOWCASE_WAIT_ASSET: String = "north"

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

        /** 一般和牌預覽顯示的符數。 */
        const val PREVIEW_FU: Int = 30

        /** 役滿預覽顯示的役滿合計倍數。 */
        const val PREVIEW_YAKUMAN_MULTIPLIER: Int = 3

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
 *
 * @param supplied 指令提供的展示理由清單。
 * @param winnerCount 要產生的贏家數，須為非負值。
 * @param defaultCue 未提供展示理由時的替代值。
 * @return 長度等於贏家數的展示理由清單；多餘輸入忽略。
 * @throws IllegalArgumentException 贏家數為負值時拋出。
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
 *
 * @param remaining 尚未完成的引數文字。
 * @param cueKeys 可供選擇的展示理由識別碼。
 * @param allowMultiple 是否接受逗號分隔清單。
 * @return 不區分大小寫比對前綴後，依識別碼排序的完整補全候選。
 */
internal fun buildShowcaseCueSuggestions(
    remaining: String,
    cueKeys: Set<String>,
    allowMultiple: Boolean,
): List<String> {
    val separatorIndex = if (allowMultiple) remaining.lastIndexOf(',') else -1
    val completedPrefix = remaining.takeIf { separatorIndex >= 0 }?.substring(0, separatorIndex + 1).orEmpty()
    val currentToken = remaining.substring(separatorIndex + 1)
    return cueKeys
        .asSequence()
        .filter { candidate -> candidate.startsWith(currentToken, ignoreCase = true) }
        .sorted()
        .map { candidate -> completedPrefix + candidate }
        .toList()
}
