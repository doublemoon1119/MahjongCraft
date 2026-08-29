package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** MahjongCraft 內建整場終止原因的完整識別碼。 */
object BuiltInMatchEndReasonIds {
    /** 已完成規則原定的固定局數。 */
    val SCHEDULE_COMPLETED: String = MahjongCraftMetadata.id("schedule_completed")

    /** 第一名已達規則要求的終局點數。 */
    val TARGET_SCORE_REACHED: String = MahjongCraftMetadata.id("target_score_reached")

    /** 延長賽已到規則允許的最後局位。 */
    val EXTRA_ROUND_LIMIT_REACHED: String = MahjongCraftMetadata.id("extra_round_limit_reached")

    /** 最後莊家已居第一並符合終局點數。 */
    val DEALER_TOP_FINISH: String = MahjongCraftMetadata.id("dealer_top_finish")

    /** 至少一位玩家已達擊飛門檻。 */
    val PLAYER_BUSTED: String = MahjongCraftMetadata.id("player_busted")
}

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
