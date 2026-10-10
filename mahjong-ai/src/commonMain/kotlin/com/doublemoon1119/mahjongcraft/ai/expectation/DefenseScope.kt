package com.doublemoon1119.mahjongcraft.ai.expectation

/** 放銃損失列入計算的對手範圍。 */
enum class DefenseScope {
    /** 不計放銃損失。 */
    NONE,

    /** 只計聽牌可能性達 [ExpectationParameters.highThreatReadyProbability] 的對手，打點使用規則的基準打點。 */
    HIGH_THREAT_ONLY,

    /** 計入所有對手，打點使用規則對各對手的估計。 */
    ALL,
}
