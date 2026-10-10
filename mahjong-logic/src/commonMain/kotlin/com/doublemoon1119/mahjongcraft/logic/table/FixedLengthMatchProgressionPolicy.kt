package com.doublemoon1119.mahjongcraft.logic.table

/**
 * 不使用延長賽的固定局數 progression policy。
 *
 * @property totalRounds 原定局數。
 */
class FixedLengthMatchProgressionPolicy(
    private val totalRounds: Int,
) : MatchProgressionPolicy {
    init {
        require(totalRounds > 0) { "Fixed match length must be positive" }
    }

    override fun decide(context: MatchProgressionContext): MatchProgressionDecision {
        val state = context.tableState
        val directive = context.completion.transitionDirective
        if (state.roundPosition.roundNumber >= totalRounds) {
            return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED)
        }
        return when (directive) {
            RoundTransitionDirective.REPEAT_DEALER -> MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound)
            RoundTransitionDirective.ADVANCE_DEALER -> MatchProgressionDecision.ContinueMatch(
                MatchRoundTransition.AdvanceTo(nextPosition(state.roundPosition, state.playerCount)),
            )
        }
    }

    /** 依既有固定局數慣例產生下一個局位。 */
    private fun nextPosition(current: MatchRoundPosition, playerCount: Int): MatchRoundPosition {
        val nextIndex = current.sequenceIndex + 1
        val winds = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH)
        return MatchRoundPosition(
            sequenceIndex = nextIndex,
            prevalentWind = winds[(nextIndex / playerCount).coerceAtMost(winds.lastIndex)],
            localRoundNumber = nextIndex % playerCount + 1,
        )
    }
}
