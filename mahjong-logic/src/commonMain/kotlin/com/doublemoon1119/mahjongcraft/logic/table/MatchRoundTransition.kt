package com.doublemoon1119.mahjongcraft.logic.table

/** 繼續整場對局時採用的局位變化。 */
sealed interface MatchRoundTransition {
    /** 保留目前局位並連莊。 */
    data object RepeatCurrentRound : MatchRoundTransition

    /**
     * 過莊並前進到明確的下一局位。
     *
     * @property nextPosition 下一局位。
     * @property continuesCombo 過莊後本場數是否繼續累加；為 `false` 時歸零。
     */
    data class AdvanceTo(
        val nextPosition: MatchRoundPosition,
        val continuesCombo: Boolean = false,
    ) : MatchRoundTransition
}
