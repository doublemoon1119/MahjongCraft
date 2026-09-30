package com.doublemoon1119.mahjongcraft.ai.expectation

/** 可換牌的手牌估計後續風險時，假設之後打出的牌。 */
enum class FutureRiskTiles {
    /** 每一張都以手中所有牌的平均放銃損失計算。 */
    AVERAGE,

    /** 依序打出手中放銃損失最小的牌；需要打出的張數超過手牌時，超出的部分以平均損失計算。 */
    SAFEST,
}

/**
 * 期望值計算使用的估計參數；內建的三個等級都使用 [DEFAULT]，數值以 AI 對局模擬的統計結果校準。
 *
 * @property opponentDiscardDiscount 對手的一張捨牌相對於自己摸一張牌，成為和牌機會的比例；對手會避開危險牌，因此小於 1。
 * @property typicalWaitTiles 兩向聽以上的手牌推算到聽牌後，假設的聽牌張數。
 * @property typicalAdvanceTiles 兩向聽以上的手牌在第一步之後，每一步假設的有效牌張數；實際有效牌較少時取實際值。
 * @property roundEndRatePerOpponent 每一輪中，每位對手讓本局結束的機率，例如和牌。
 * @property highThreatReadyProbability 只防守高威脅對手時，視為高威脅的聽牌可能性下限。
 * @property attackTurnsPerShanten 可換牌的手牌每差一向聽，預估還要打出幾張牌才能和牌。
 * @property placementStepPoints 接近終局時，名次每升降一位相當的點數。
 * @property abortiveDrawWinProbability 自己估計的和牌機率低於此值時宣告途中流局。
 * @property futureRiskTiles 可換牌的手牌估計後續風險時，假設之後打出哪些牌。
 * @property lockedFutureRiskFactor 宣告後不能換牌的手牌，後續風險乘上的倍數。
 * @property futureRiskHighThreatOnly 後續風險是否只計聽牌可能性達 [highThreatReadyProbability] 的對手。
 */
data class ExpectationParameters(
    val opponentDiscardDiscount: Double = 0.5,
    val typicalWaitTiles: Int = 5,
    val typicalAdvanceTiles: Int = 16,
    val roundEndRatePerOpponent: Double = 0.03,
    val highThreatReadyProbability: Double = 0.5,
    val attackTurnsPerShanten: Int = 3,
    val placementStepPoints: Int = 8000,
    val abortiveDrawWinProbability: Double = 0.15,
    val futureRiskTiles: FutureRiskTiles = FutureRiskTiles.AVERAGE,
    val lockedFutureRiskFactor: Double = 1.0,
    val futureRiskHighThreatOnly: Boolean = false,
) {
    /** [ExpectationParameters] 的固定值。 */
    companion object {
        /** 內建等級使用的參數。 */
        val DEFAULT: ExpectationParameters = ExpectationParameters()
    }
}
