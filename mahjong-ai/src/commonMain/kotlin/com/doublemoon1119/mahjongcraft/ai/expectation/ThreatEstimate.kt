package com.doublemoon1119.mahjongcraft.ai.expectation

/**
 * 從公開資訊估計的對手威脅。
 *
 * @property readyProbability 對手已經聽牌的可能性，介於 0 到 1。
 * @property expectedWinValue 對手和牌時預估可得的點數；放銃給他時的損失以此估計。
 */
data class ThreatEstimate(
    val readyProbability: Double,
    val expectedWinValue: Int,
) {
    init {
        require(readyProbability in 0.0..1.0) { "Ready probability must be between 0 and 1" }
        require(expectedWinValue >= 0) { "Expected win value must not be negative" }
    }
}
