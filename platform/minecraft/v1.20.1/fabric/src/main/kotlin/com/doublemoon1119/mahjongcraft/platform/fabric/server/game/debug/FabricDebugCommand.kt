package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation.FabricDebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.FabricDebugDecisionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.FabricDebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.FabricDebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text.FabricDebugTextCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

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
 * - `decision_hud`、`preparation`：[FabricDebugDecisionCommand]
 *
 * 尚未分家的 `match_progression` 仍由本類別直接建立。
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
 * @property decisionCommand 建立玩家決策互動預覽子指令樹。
 * @property playerTableScope 解析呼叫者目前入座的桌子。
 * @property entityLifecycle 保管並驅動臨時 entity 的到期清除。
 */
@Single
class FabricDebugCommand(
    private val minecraftEnvironment: MinecraftEnvironment,
    private val gameRepository: GameRepository,
    private val debugGameScenarioCommand: FabricDebugScenarioCommand,
    private val animationCommand: FabricDebugAnimationCommand,
    private val presentationCommand: FabricDebugPresentationCommand,
    private val textCommand: FabricDebugTextCommand,
    private val decisionCommand: FabricDebugDecisionCommand,
    private val playerTableScope: DebugPlayerTableScope,
    private val entityLifecycle: DebugPreviewEntityLifecycle,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
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
            .then(decisionCommand.buildDecisionHudCommand())
            .then(animationCommand.buildMeldCommand())
            .then(presentationCommand.buildContinuingWinCommand())
            .then(presentationCommand.buildWinShowcaseOverrideCommand())
            .then(decisionCommand.buildPreparationCommand()),
    )

    /** 建立不修改權威對局、只預覽日麻 progression 決策的測試指令樹。 */
    private fun matchProgressionCommand(): LiteralArgumentBuilder<ServerCommandSource> = MatchProgressionPreview.entries.fold(literal(MATCH_PROGRESSION_SUBCOMMAND)) { node, preview ->
        node.then(
            literal(preview.commandName).executes { context ->
                previewMatchProgression(context.source, preview)
            },
        )
    }

    /** 使用執行者目前牌桌的玩家身分建立唯讀情境，回報正式日麻 policy 的決策。 */
    private fun previewMatchProgression(source: ServerCommandSource, preview: MatchProgressionPreview): Int = playerTableScope.runSuspending(source) { tableId, _ ->
        val current = gameRepository.getTableState(tableId)
            ?: return@runSuspending "Game not found"
        if (current.players.size != RIICHI_PLAYER_COUNT) {
            return@runSuspending "Match progression previews require four players"
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

    private companion object {
        const val OP_PERMISSION_LEVEL: Int = 2
        const val DEBUG_SUBCOMMAND: String = "debug"
        const val MATCH_PROGRESSION_SUBCOMMAND: String = "match_progression"

        /** 內建日麻 progression debug 情境固定使用的玩家數。 */
        const val RIICHI_PLAYER_COUNT: Int = 4

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
