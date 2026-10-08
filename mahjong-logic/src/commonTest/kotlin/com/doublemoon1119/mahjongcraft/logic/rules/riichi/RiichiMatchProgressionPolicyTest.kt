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

    /** 延長局包含莊家的雙響，只有子家達返點並居首時，莊家連莊優先。 */
    @Test
    fun `extra round double ron with the dealer repeats when only a non-dealer reaches the target`() {
        val context = context(
            RiichiGameLength.East,
            6,
            Wind.SOUTH,
            MatchRoundPhase.EXTRA,
            listOf(24_000, 26_000, 32_000, 18_000),
            directive = RoundTransitionDirective.REPEAT_DEALER,
            dealerIndex = 1,
            beneficiaryIndices = listOf(1, 2),
        )

        assertEquals(
            MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound),
            policy(context).decide(context),
        )
    }

    /** 延長局荒牌流局莊家聽牌連莊時，子家靠不聽罰符達返點也不終局。 */
    @Test
    fun `extra round dealer tenpai repeats when a non-dealer reaches the target through noten payments`() {
        val context = context(
            RiichiGameLength.East,
            6,
            Wind.SOUTH,
            MatchRoundPhase.EXTRA,
            listOf(23_500, 26_500, 30_500, 19_500),
            directive = RoundTransitionDirective.REPEAT_DEALER,
            dealerIndex = 1,
            classification = RoundCompletionClassification.EXHAUSTIVE_DRAW,
            beneficiaryIndices = listOf(1, 2),
        )

        assertEquals(
            MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound),
            policy(context).decide(context),
        )
    }

    /** 延長最後局莊家連莊且未居首時重打同一局，不以延長局數用完終局。 */
    @Test
    fun `extra last round repeats while the dealer keeps the seat`() {
        val context = context(
            RiichiGameLength.East,
            8,
            Wind.SOUTH,
            MatchRoundPhase.EXTRA,
            listOf(29_000, 25_000, 24_000, 22_000),
            directive = RoundTransitionDirective.REPEAT_DEALER,
            dealerIndex = 3,
        )

        assertEquals(
            MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound),
            policy(context).decide(context),
        )
    }

    /** 延長局莊家本人連莊並以達返點居首時，套用和了止め終局。 */
    @Test
    fun `extra round dealer top finish ends the match`() {
        val context = context(
            RiichiGameLength.East,
            6,
            Wind.SOUTH,
            MatchRoundPhase.EXTRA,
            listOf(22_000, 31_000, 27_000, 20_000),
            directive = RoundTransitionDirective.REPEAT_DEALER,
            dealerIndex = 1,
        )

        assertEquals(
            MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.DEALER_TOP_FINISH),
            policy(context).decide(context),
        )
    }

    /** 原定最後局包含莊家的雙響，只有子家達返點並居首時，莊家連莊優先。 */
    @Test
    fun `final regular round double ron with the dealer repeats when only a non-dealer reaches the target`() {
        val context = context(
            RiichiGameLength.East,
            4,
            Wind.EAST,
            MatchRoundPhase.REGULAR,
            listOf(31_000, 24_000, 20_000, 25_000),
            directive = RoundTransitionDirective.REPEAT_DEALER,
            dealerIndex = 3,
            beneficiaryIndices = listOf(3, 0),
        )

        assertEquals(
            MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound),
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

    /** 荒牌流局與途中流局即使過莊，本場數仍繼續累加。 */
    @Test
    fun `draws that advance the dealer continue the combo`() {
        listOf(RoundCompletionClassification.EXHAUSTIVE_DRAW, RoundCompletionClassification.ABORTIVE_DRAW).forEach { classification ->
            val context = context(
                RiichiGameLength.East,
                2,
                Wind.EAST,
                MatchRoundPhase.REGULAR,
                listOf(25_000, 25_000, 25_000, 25_000),
                classification = classification,
            )

            assertEquals(
                MatchProgressionDecision.ContinueMatch(
                    MatchRoundTransition.AdvanceTo(
                        nextPosition = MatchRoundPosition(2, Wind.EAST, 3),
                        continuesCombo = true,
                    ),
                ),
                policy(context).decide(context),
            )
        }
    }

    /** 子家和牌過莊時本場數歸零。 */
    @Test
    fun `non-dealer win resets the combo`() {
        val context = context(RiichiGameLength.East, 2, Wind.EAST, MatchRoundPhase.REGULAR, listOf(25_000, 25_000, 25_000, 25_000))

        assertEquals(
            MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(
                    nextPosition = MatchRoundPosition(2, Wind.EAST, 3),
                    continuesCombo = false,
                ),
            ),
            policy(context).decide(context),
        )
    }

    /** 原定最後局流局過莊進入延長局時，本場數仍繼續累加。 */
    @Test
    fun `final regular round draw into extra round continues the combo`() {
        val context = context(
            RiichiGameLength.East,
            4,
            Wind.EAST,
            MatchRoundPhase.REGULAR,
            listOf(29_900, 25_100, 25_000, 20_000),
            classification = RoundCompletionClassification.EXHAUSTIVE_DRAW,
        )

        assertEquals(
            MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(
                    nextPosition = MatchRoundPosition(4, Wind.SOUTH, 1, MatchRoundPhase.EXTRA),
                    continuesCombo = true,
                ),
            ),
            policy(context).decide(context),
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
        beneficiaryIndices: List<Int> = if (directive == RoundTransitionDirective.REPEAT_DEALER) listOf(dealerIndex) else emptyList(),
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
        return progressionContext(state, directive, rankedIndices, classification, beneficiaryIndices)
    }

    /** 由桌況建立最小完整的本局摘要與權威排名。 */
    private fun progressionContext(
        state: TableState,
        directive: RoundTransitionDirective,
        rankedIndices: List<Int>,
        classification: RoundCompletionClassification,
        beneficiaryIndices: List<Int>,
    ): MatchProgressionContext = MatchProgressionContext(
        tableState = state,
        completion = RoundCompletionSummary(
            outcomeId = "test:round_completed",
            classification = classification,
            beneficiaryPlayerIds = beneficiaryIndices.mapTo(mutableSetOf()) { state.players[it].id },
            transitionDirective = directive,
            settledScoresByPlayerId = state.players.associate { it.id to it.score },
        ),
        rankedPlayerIds = rankedIndices.map { state.players[it].id },
    )
}
