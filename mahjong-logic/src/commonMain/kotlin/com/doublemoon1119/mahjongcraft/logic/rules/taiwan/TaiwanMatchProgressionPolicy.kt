package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.FixedLengthMatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundTransition
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective

/**
 * 台灣麻將打完選定圈數後結束整場，但末局莊家連莊時要打到莊家下莊才結束。
 *
 * @property totalRounds 選定圈數的原定局數。
 */
class TaiwanMatchProgressionPolicy(private val totalRounds: Int) : MatchProgressionPolicy {
    private val fixedLength = FixedLengthMatchProgressionPolicy(totalRounds)

    override fun decide(context: MatchProgressionContext): MatchProgressionDecision {
        val isLastRound = context.tableState.roundPosition.roundNumber >= totalRounds
        if (isLastRound && context.completion.transitionDirective == RoundTransitionDirective.RepeatDealer) {
            return MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound)
        }
        if (isLastRound) return MatchProgressionDecision.EndMatch(BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED)
        return fixedLength.decide(context)
    }
}
