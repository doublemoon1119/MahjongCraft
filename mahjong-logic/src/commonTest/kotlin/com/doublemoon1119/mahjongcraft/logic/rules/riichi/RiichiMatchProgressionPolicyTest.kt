package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundTransition
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** [RiichiMatchProgressionPolicy] 的南入、西入、驟死、和了止め與擊飛測試。 */
class RiichiMatchProgressionPolicyTest {
    /** 東四過莊且第一名未達返點時，應進入 EXTRA 南一。 */
    @Test
    fun `east four below target advances to extra south one`() {
        val context = context(RiichiGameLength.East, 4, Wind.EAST, MatchRoundPhase.REGULAR, listOf(29_900, 25_100, 25_000, 20_000))

        assertEquals(
            MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(MatchRoundPosition(4, Wind.SOUTH, 1, MatchRoundPhase.EXTRA)),
            ),
            policy(context).decide(context),
        )
    }

    /** 東四過莊且第一名已達返點時，應直接終局。 */
    @Test
    fun `east four at target ends match`() {
        val context = context(RiichiGameLength.East, 4, Wind.EAST, MatchRoundPhase.REGULAR, listOf(30_000, 25_000, 25_000, 20_000))

        assertEquals(
            MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED),
            policy(context).decide(context),
        )
    }

    /** 南四過莊且第一名未達返點時，半莊應西入。 */
    @Test
    fun `south four below target advances to extra west one`() {
        val context = context(RiichiGameLength.TwoWinds, 8, Wind.SOUTH, MatchRoundPhase.REGULAR, listOf(29_900, 25_100, 25_000, 20_000))

        assertEquals(
            MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(MatchRoundPosition(8, Wind.WEST, 1, MatchRoundPhase.EXTRA)),
            ),
            policy(context).decide(context),
        )
    }

    /** 延長局任一結算後第一名達返點，即使尚未到上限也應驟死終局。 */
    @Test
    fun `extra round target uses sudden death`() {
        val context = context(RiichiGameLength.East, 5, Wind.SOUTH, MatchRoundPhase.EXTRA, listOf(30_100, 25_000, 24_900, 20_000))

        assertEquals(
            MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED),
            policy(context).decide(context),
        )
    }

    /** 東風戰延長至南四仍無人達返點時，不得二次延長。 */
    @Test
    fun `extra round limit forces match end`() {
        val context = context(RiichiGameLength.East, 8, Wind.SOUTH, MatchRoundPhase.EXTRA, listOf(29_900, 25_100, 25_000, 20_000))

        assertEquals(
            MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.EXTRA_ROUND_LIMIT_REACHED),
            policy(context).decide(context),
        )
    }

    /** 原定最後局莊家居首、達返點且應連莊時，固定套用和了止め／聽牌止め。 */
    @Test
    fun `dealer top finish ends instead of repeating final regular round`() {
        val context = context(
            RiichiGameLength.East,
            4,
            Wind.EAST,
            MatchRoundPhase.REGULAR,
            listOf(25_000, 24_000, 21_000, 30_000),
            directive = RoundTransitionDirective.REPEAT_DEALER,
            dealerIndex = 3,
            rankedIndices = listOf(3, 0, 1, 2),
        )

        assertEquals(
            MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.DEALER_TOP_FINISH),
            policy(context).decide(context),
        )
    }

    /** 原定最後局的途中流局即使連莊，也不得誤套用和了止め。 */
    @Test
    fun `abortive draw does not trigger dealer top finish`() {
        val context = context(
            RiichiGameLength.East,
            4,
            Wind.EAST,
            MatchRoundPhase.REGULAR,
            listOf(25_000, 24_000, 21_000, 30_000),
            directive = RoundTransitionDirective.REPEAT_DEALER,
            dealerIndex = 3,
            rankedIndices = listOf(3, 0, 1, 2),
            classification = RoundCompletionClassification.ABORTIVE_DRAW,
        )

        assertEquals(
            MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound),
            policy(context).decide(context),
        )
    }

    /** 自訂返點必須直接控制是否進入延長賽，不得使用另一份隱藏常數。 */
    @Test
    fun `custom minimum points controls overtime`() {
        val context = context(RiichiGameLength.East, 4, Wind.EAST, MatchRoundPhase.REGULAR, listOf(34_000, 25_000, 21_000, 20_000))
        val customPolicy = RiichiMatchProgressionPolicy(
            RiichiRuleConfig(gameLength = RiichiGameLength.East, scoreConfig = RiichiScoreConfig(minPointsToWin = 35_000)),
        )

        assertEquals(
            MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(MatchRoundPosition(4, Wind.SOUTH, 1, MatchRoundPhase.EXTRA)),
            ),
            customPolicy.decide(context),
        )
    }

    /** 擊飛優先於返點、連莊與延長判定。 */
    @Test
    fun `bust ends match before overtime decisions`() {
        val context = context(RiichiGameLength.East, 2, Wind.EAST, MatchRoundPhase.REGULAR, listOf(50_000, 30_100, 20_000, -100))

        assertEquals(
            MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.PLAYER_BUSTED),
            policy(context).decide(context),
        )
    }

    /** 依測試 context 的規則設定建立 policy。 */
    private fun policy(context: MatchProgressionContext): RiichiMatchProgressionPolicy = RiichiMatchProgressionPolicy(context.tableState.config as RiichiRuleConfig)

    /** 建立指定局位、分數、莊家決策與權威排行的 progression context。 */
    private fun context(
        gameLength: RiichiGameLength,
        roundNumber: Int,
        prevalentWind: Wind,
        phase: MatchRoundPhase,
        scores: List<Int>,
        directive: RoundTransitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
        dealerIndex: Int = 0,
        rankedIndices: List<Int> = scores.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it }),
        classification: RoundCompletionClassification = RoundCompletionClassification.WIN,
    ): MatchProgressionContext {
        val players = scores.mapIndexed { index, score ->
            FakeMahjongPlayerFactory.create(initialSeat = Wind.entries[index]).copy(score = score)
        }
        val baseState = FakeTableStateFactory.create(
            players = players,
            dealerPlayerId = players[dealerIndex].id,
            config = RiichiRuleConfig(gameLength = gameLength),
            prevalentWind = prevalentWind,
            roundNumber = roundNumber,
        )
        val state = baseState.copy(
            roundPosition = MatchRoundPosition(roundNumber - 1, prevalentWind, (roundNumber - 1) % 4 + 1, phase),
        )
        return progressionContext(state, directive, rankedIndices, classification)
    }

    /** 由桌況建立最小完整的本局摘要與權威排名。 */
    private fun progressionContext(
        state: TableState,
        directive: RoundTransitionDirective,
        rankedIndices: List<Int>,
        classification: RoundCompletionClassification,
    ): MatchProgressionContext = MatchProgressionContext(
        tableState = state,
        completion = RoundCompletionSummary(
            outcomeId = "test:round_completed",
            classification = classification,
            beneficiaryPlayerIds = if (directive == RoundTransitionDirective.REPEAT_DEALER) {
                setOf(state.dealerPlayerId)
            } else {
                emptySet()
            },
            transitionDirective = directive,
            settledScoresByPlayerId = state.players.associate { it.id to it.score },
        ),
        rankedPlayerIds = rankedIndices.map { state.players[it].id },
    )
}
