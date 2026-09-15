package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression

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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import org.koin.core.annotation.Single

/**
 * 建立 `/mahjongcraft debug match_progression`：正式終局推進 policy 的唯讀決策預覽。
 *
 * 每個情境以呼叫者目前牌桌的玩家身分建立一份只存在於記憶體的 [MatchProgressionContext]——替換分數、
 * 對局長度與局位後交給正式的 [RiichiMatchProgressionPolicy]，把它的決策直接回報給呼叫者。牌桌本身的
 * 權威狀態完全不變，因此本類別只取得讀取用的 [GameRepository]，不持有任何可寫入權威狀態的依賴。
 *
 * @property gameRepository 讀取呼叫者目前牌桌的狀態作為情境基礎。
 * @property playerTableScope 解析呼叫者目前入座的桌子。
 */
@Single
class FabricDebugProgressionCommand(
    private val gameRepository: GameRepository,
    private val playerTableScope: DebugPlayerTableScope,
) {
    /** 建立不修改權威對局、只預覽日麻 progression 決策的測試指令樹。 */
    fun buildMatchProgressionCommand(): LiteralArgumentBuilder<ServerCommandSource> = MatchProgressionPreview.entries.fold(literal(MATCH_PROGRESSION_SUBCOMMAND)) { node, preview ->
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

    private companion object {
        /** `match_progression` 子指令 literal。 */
        const val MATCH_PROGRESSION_SUBCOMMAND: String = "match_progression"

        /** 內建日麻 progression debug 情境固定使用的玩家數。 */
        const val RIICHI_PLAYER_COUNT: Int = 4

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1
    }
}
