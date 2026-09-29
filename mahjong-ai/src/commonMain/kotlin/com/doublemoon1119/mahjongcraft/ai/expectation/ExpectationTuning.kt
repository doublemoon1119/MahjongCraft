package com.doublemoon1119.mahjongcraft.ai.expectation

/** 期望值計算使用的估計參數；以 AI 對局模擬的統計結果校準。 */
internal object ExpectationTuning {
    /** 對手的一張捨牌相對於自己摸一張牌，成為和牌機會的比例；對手會避開危險牌，因此小於 1。 */
    const val OPPONENT_DISCARD_DISCOUNT: Double = 0.5

    /** 兩向聽以上的手牌推算到聽牌後，假設的聽牌張數。 */
    const val TYPICAL_WAIT_TILES: Int = 5

    /** 兩向聽以上的手牌在第一步之後，每一步假設的有效牌張數；實際有效牌較少時取實際值。 */
    const val TYPICAL_ADVANCE_TILES: Int = 16

    /** 每一輪中，每位對手讓本局結束的機率，例如和牌。 */
    const val ROUND_END_RATE_PER_OPPONENT: Double = 0.03

    /** 只防守高威脅對手時，視為高威脅的聽牌可能性下限。 */
    const val HIGH_THREAT_READY_PROBABILITY: Double = 0.5

    /** 可換牌的手牌每差一向聽，預估還要打出幾張牌才能和牌。 */
    const val ATTACK_TURNS_PER_SHANTEN: Int = 3

    /** 接近終局時，名次每升降一位相當的點數。 */
    const val PLACEMENT_STEP_POINTS: Int = 8000

    /** 自己估計的和牌機率低於此值時宣告途中流局。 */
    const val ABORTIVE_DRAW_WIN_PROBABILITY: Double = 0.15

    /** 兩個期望值視為相同的差距上限。 */
    const val EXPECTED_VALUE_TIE_TOLERANCE: Double = 1e-9
}
