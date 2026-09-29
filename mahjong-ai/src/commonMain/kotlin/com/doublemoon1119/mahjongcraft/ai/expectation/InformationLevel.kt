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

/**
 * AI 期望值計算可使用的資訊範圍。
 *
 * 三個等級使用同一套期望值計算，差別只在這裡開放的資訊；決策一律選擇期望值最高的選項，不使用隨機性。
 *
 * @property countsVisibleTiles 未見牌是否扣除場上所有可見牌；為 `false` 時只扣除自己的手牌。
 * @property valuesWins 是否依規則區分每一種和牌的打點；為 `false` 時所有和牌都以目前手牌的基準打點計算。
 * @property defenseScope 放銃損失列入計算的對手範圍。
 * @property considersFutureRisk 是否計入繼續進攻時後續捨牌的放銃風險。
 * @property considersPlacement 是否在接近終局時把點數得失換算為名次得失。
 * @property readingDepth 對手模型推測對手手牌的深度。
 */
data class InformationLevel(
    val countsVisibleTiles: Boolean,
    val valuesWins: Boolean,
    val defenseScope: DefenseScope,
    val considersFutureRisk: Boolean,
    val considersPlacement: Boolean,
    val readingDepth: ReadingDepth,
) {
    /** 內建的三個等級。 */
    companion object {
        /** 初級：只看自己的手牌與向聽，不數場上的牌、不區分打點、不防守。 */
        val BEGINNER: InformationLevel = InformationLevel(
            countsVisibleTiles = false,
            valuesWins = false,
            defenseScope = DefenseScope.NONE,
            considersFutureRisk = false,
            considersPlacement = false,
            readingDepth = ReadingDepth.BASIC,
        )

        /** 中級：數場上的牌、區分打點，並對高威脅的對手防守。 */
        val INTERMEDIATE: InformationLevel = InformationLevel(
            countsVisibleTiles = true,
            valuesWins = true,
            defenseScope = DefenseScope.HIGH_THREAT_ONLY,
            considersFutureRisk = false,
            considersPlacement = false,
            readingDepth = ReadingDepth.BASIC,
        )

        /**
         * 高級：在中級之上對所有對手各自估計並防守、考慮名次，並以進階深度讀牌。
         *
         * 不計後續風險：後續風險讓繼續進攻的選項看起來過於危險，使手牌過早放棄。
         */
        val ADVANCED: InformationLevel = InformationLevel(
            countsVisibleTiles = true,
            valuesWins = true,
            defenseScope = DefenseScope.ALL,
            considersFutureRisk = false,
            considersPlacement = true,
            readingDepth = ReadingDepth.ADVANCED,
        )
    }
}
