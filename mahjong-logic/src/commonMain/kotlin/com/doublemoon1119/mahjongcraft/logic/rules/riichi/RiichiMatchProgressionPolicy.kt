package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundTransition
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/** 四人日麻的固定賽程、南入／西入、驟死、和了止め與擊飛 policy。 */
class RiichiMatchProgressionPolicy(
    private val config: RiichiRuleConfig,
) : MatchProgressionPolicy {
    override fun decide(context: MatchProgressionContext): MatchProgressionDecision {
        val state = context.tableState
        require(state.playerCount in MIN_SUPPORTED_PLAYER_COUNT..RIICHI_PLAYER_COUNT) {
            "Built-in riichi match progression requires two to four players"
        }

        val bustThreshold = config.scoreConfig.bustThreshold
        if (bustThreshold != null && state.players.any { it.score < bustThreshold }) {
            return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.PLAYER_BUSTED)
        }

        val schedule = scheduleFor(config.gameLength)
        val current = state.roundPosition
        require(current.sequenceIndex in 0..schedule.extraLastIndex) { "Round position is outside the riichi schedule: $current" }

        if (config.gameLength == RiichiGameLength.OneGame) {
            return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED)
        }

        val topPlayer = state.players.first { it.id == context.rankedPlayerIds.first() }
        val targetReached = topPlayer.score >= config.scoreConfig.minPointsToWin
        val directive = context.completion.transitionDirective

        if (current.phase == MatchRoundPhase.EXTRA) {
            if (targetReached) return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED)
            if (current.sequenceIndex >= schedule.extraLastIndex) {
                return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.EXTRA_ROUND_LIMIT_REACHED)
            }
            return continueByDirective(current, directive, schedule.regularLastIndex)
        }

        if (current.sequenceIndex < schedule.regularLastIndex) {
            return continueByDirective(current, directive, schedule.regularLastIndex)
        }

        require(current.sequenceIndex == schedule.regularLastIndex) { "Regular riichi round exceeded its schedule: $current" }
        if (directive == RoundTransitionDirective.REPEAT_DEALER) {
            val dealerQualifiedForTopFinish = when (context.completion.classification) {
                RoundCompletionClassification.WIN,
                RoundCompletionClassification.EXHAUSTIVE_DRAW,
                -> state.dealerPlayerId in context.completion.beneficiaryPlayerIds

                RoundCompletionClassification.ABORTIVE_DRAW,
                RoundCompletionClassification.EXTENSION,
                -> false
            }
            return if (dealerQualifiedForTopFinish && state.dealerPlayerId == topPlayer.id && targetReached) {
                MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.DEALER_TOP_FINISH)
            } else {
                MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound)
            }
        }
        if (targetReached) return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED)
        return MatchProgressionDecision.ContinueMatch(
            MatchRoundTransition.AdvanceTo(position(schedule.regularLastIndex + 1, schedule.regularLastIndex)),
        )
    }

    /** 依莊家 directive 產生連莊或下一局決策。 */
    private fun continueByDirective(
        current: MatchRoundPosition,
        directive: RoundTransitionDirective,
        regularLastIndex: Int,
    ): MatchProgressionDecision = when (directive) {
        RoundTransitionDirective.REPEAT_DEALER -> MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound)
        RoundTransitionDirective.ADVANCE_DEALER -> MatchProgressionDecision.ContinueMatch(
            MatchRoundTransition.AdvanceTo(position(current.sequenceIndex + 1, regularLastIndex)),
        )
    }

    /** 由明確四人日麻 sequence index 建立局位。 */
    private fun position(sequenceIndex: Int, regularLastIndex: Int): MatchRoundPosition {
        val winds = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST)
        return MatchRoundPosition(
            sequenceIndex = sequenceIndex,
            prevalentWind = winds[sequenceIndex / RIICHI_PLAYER_COUNT],
            localRoundNumber = sequenceIndex % RIICHI_PLAYER_COUNT + 1,
            phase = if (sequenceIndex > regularLastIndex) MatchRoundPhase.EXTRA else MatchRoundPhase.REGULAR,
        )
    }

    /** 依房間長度取得原定最後局與延長最後局。 */
    private fun scheduleFor(gameLength: RiichiGameLength): Schedule = when (gameLength) {
        RiichiGameLength.OneGame -> Schedule(regularLastIndex = 0, extraLastIndex = 0)
        RiichiGameLength.East -> Schedule(regularLastIndex = 3, extraLastIndex = 7)
        RiichiGameLength.TwoWinds -> Schedule(regularLastIndex = 7, extraLastIndex = 11)
    }

    /** 日麻賽程的原定與延長局位上限。 */
    private data class Schedule(val regularLastIndex: Int, val extraLastIndex: Int)

    private companion object {
        /** 測試與未來規則擴充共用模型所允許的最少玩家數；正式內建日麻房間仍限制四人。 */
        const val MIN_SUPPORTED_PLAYER_COUNT: Int = 2

        /** 目前內建日麻固定玩家數。 */
        const val RIICHI_PLAYER_COUNT: Int = 4
    }
}
