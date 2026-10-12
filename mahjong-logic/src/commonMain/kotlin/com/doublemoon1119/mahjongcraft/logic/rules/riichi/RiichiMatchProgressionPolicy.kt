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

/**
 * 日麻（四人或三人）的固定賽程、南入／西入、驟死、和了止め與擊飛 policy；每圈局數等於玩家人數。
 *
 * 原定最後局與延長局一律莊家連莊優先：莊家連莊時，即使其他玩家（例如包含莊家的雙響中的子家）已達返點也繼續連莊，
 * 只有莊家本人和牌或聽牌、居首且達返點才終局；莊家沒有連莊時，有人達返點即終局，延長局打完最後一局也終局。
 *
 * @property config 賽程長度、返點與擊飛門檻等立直規則設定。
 */
class RiichiMatchProgressionPolicy(
    private val config: RiichiFamilyRuleConfig,
) : MatchProgressionPolicy {
    override fun decide(context: MatchProgressionContext): MatchProgressionDecision {
        val state = context.tableState
        require(state.playerCount in MIN_SUPPORTED_PLAYER_COUNT..MAX_SUPPORTED_PLAYER_COUNT) {
            "Built-in riichi match progression requires two to four players"
        }
        val roundsPerWind = state.playerCount

        val bustThreshold = config.scoreConfig.bustThreshold
        if (bustThreshold != null && state.players.any { it.score < bustThreshold }) {
            return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.PLAYER_BUSTED)
        }

        val schedule = scheduleFor(config.gameLength, roundsPerWind)
        val current = state.roundPosition
        require(current.sequenceIndex in 0..schedule.extraLastIndex) { "Round position is outside the riichi schedule: $current" }

        if (config.gameLength == RiichiGameLength.OneGame) {
            return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED)
        }

        val topPlayer = state.players.first { it.id == context.rankedPlayerIds.first() }
        val targetReached = topPlayer.score >= config.scoreConfig.minPointsToWin
        val directive = context.completion.transitionDirective
        val continuesCombo = context.completion.classification.continuesCombo()

        if (current.phase == MatchRoundPhase.REGULAR && current.sequenceIndex < schedule.regularLastIndex) {
            return continueByDirective(current, directive, schedule.regularLastIndex, roundsPerWind, continuesCombo)
        }

        // 原定最後局與延長局使用同一套判斷：莊家連莊優先，只有莊家本人以和牌或聽牌連莊、居首且達返點時才終局。
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
        if (current.sequenceIndex >= schedule.extraLastIndex) {
            return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.EXTRA_ROUND_LIMIT_REACHED)
        }
        return MatchProgressionDecision.ContinueMatch(
            MatchRoundTransition.AdvanceTo(
                nextPosition = position(current.sequenceIndex + 1, schedule.regularLastIndex, roundsPerWind),
                continuesCombo = continuesCombo,
            ),
        )
    }

    /**
     * 依莊家去留指示產生下一步賽程決策。
     *
     * @param current 目前局位。
     * @param directive 連莊或過莊指示。
     * @param regularLastIndex 原定賽程最後一局的零起算索引。
     * @param roundsPerWind 每圈局數。
     * @param continuesCombo 過莊後是否繼續累加本場數。
     * @return 連莊或前進到下一局的決策。
     */
    private fun continueByDirective(
        current: MatchRoundPosition,
        directive: RoundTransitionDirective,
        regularLastIndex: Int,
        roundsPerWind: Int,
        continuesCombo: Boolean,
    ): MatchProgressionDecision = when (directive) {
        RoundTransitionDirective.REPEAT_DEALER -> MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound)
        RoundTransitionDirective.ADVANCE_DEALER -> MatchProgressionDecision.ContinueMatch(
            MatchRoundTransition.AdvanceTo(
                nextPosition = position(current.sequenceIndex + 1, regularLastIndex, roundsPerWind),
                continuesCombo = continuesCombo,
            ),
        )
    }

    /** 流局後即使過莊，本場數仍繼續累加；和牌與等同和牌的結果在過莊時歸零。 */
    private fun RoundCompletionClassification.continuesCombo(): Boolean = when (this) {
        RoundCompletionClassification.EXHAUSTIVE_DRAW,
        RoundCompletionClassification.ABORTIVE_DRAW,
        -> true

        RoundCompletionClassification.WIN,
        RoundCompletionClassification.EXTENSION,
        -> false
    }

    /**
     * 依連續局序建立場風、圈內局數與賽程階段。
     *
     * @param sequenceIndex 零起算的連續局序。
     * @param regularLastIndex 原定賽程的最後局序。
     * @param roundsPerWind 每圈局數。
     * @return 對應的局位。
     */
    private fun position(
        sequenceIndex: Int,
        regularLastIndex: Int,
        roundsPerWind: Int,
    ): MatchRoundPosition {
        val winds = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST)
        return MatchRoundPosition(
            sequenceIndex = sequenceIndex,
            prevalentWind = winds[sequenceIndex / roundsPerWind],
            localRoundNumber = sequenceIndex % roundsPerWind + 1,
            phase = if (sequenceIndex > regularLastIndex) MatchRoundPhase.EXTRA else MatchRoundPhase.REGULAR,
        )
    }

    /**
     * 依賽程長度取得原定及延長上限。
     *
     * @param gameLength 對局賽程長度。
     * @param roundsPerWind 每圈局數。
     * @return 原定最後局與最多再一圈的延長最後局；單局制不延長。
     */
    private fun scheduleFor(gameLength: RiichiGameLength, roundsPerWind: Int): Schedule = when (gameLength) {
        RiichiGameLength.OneGame -> Schedule(regularLastIndex = 0, extraLastIndex = 0)
        RiichiGameLength.East -> Schedule(regularLastIndex = roundsPerWind - 1, extraLastIndex = roundsPerWind * 2 - 1)
        RiichiGameLength.TwoWinds -> Schedule(regularLastIndex = roundsPerWind * 2 - 1, extraLastIndex = roundsPerWind * 3 - 1)
    }

    /**
     * 立直賽程的局序上限。
     *
     * @property regularLastIndex 原定賽程最後一局的零起算局序。
     * @property extraLastIndex 延長賽程最後一局的零起算局序。
     */
    private data class Schedule(val regularLastIndex: Int, val extraLastIndex: Int)

    /** 內建立直賽程允許的人數範圍。 */
    private companion object {
        /** 允許的最少玩家數。 */
        const val MIN_SUPPORTED_PLAYER_COUNT: Int = 2

        /** 允許的最多玩家數。 */
        const val MAX_SUPPORTED_PLAYER_COUNT: Int = 4
    }
}
