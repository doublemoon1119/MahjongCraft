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
 * @property winValueDetail 和牌打點看得多仔細，介於 0 到 1：1 使用規則算出的點數，0 所有和牌都以目前手牌的基準打點計算，
 *   中間值依比例混合兩者，例如 0.5 時宣告（例如日麻的立直）多出的打點只算一半。
 * @property defenseScope 放銃損失列入計算的對手範圍。
 * @property considersFutureRisk 是否計入繼續進攻時後續捨牌的放銃風險。
 * @property considersPlacement 是否在接近終局時把點數得失換算為名次得失。
 * @property readingDepth 對手模型推測對手手牌的深度。
 */
data class InformationLevel(
    val countsVisibleTiles: Boolean,
    val winValueDetail: Double,
    val defenseScope: DefenseScope,
    val considersFutureRisk: Boolean,
    val considersPlacement: Boolean,
    val readingDepth: ReadingDepth,
) {
    init {
        require(winValueDetail in 0.0..1.0) { "Win value detail must be between 0 and 1" }
    }

    /** 內建的三個等級。 */
    companion object {
        /** 初級：只看自己的手牌與向聽，不數場上的牌、不區分打點、不防守。 */
        val BEGINNER: InformationLevel = InformationLevel(
            countsVisibleTiles = false,
            winValueDetail = 0.0,
            defenseScope = DefenseScope.NONE,
            considersFutureRisk = false,
            considersPlacement = false,
            readingDepth = ReadingDepth.BASIC,
        )

        /**
         * 中級：數場上的牌、對高威脅的對手防守，打點只粗略區分。
         *
         * 打點只計入規則點數與基準打點差距的三成，因此偏好快速和牌；只有宣告後打點明顯較高，或不宣告就不能和牌時，
         * 才會宣告（例如日麻的立直）。
         */
        val INTERMEDIATE: InformationLevel = InformationLevel(
            countsVisibleTiles = true,
            winValueDetail = 0.3,
            defenseScope = DefenseScope.HIGH_THREAT_ONLY,
            considersFutureRisk = false,
            considersPlacement = false,
            readingDepth = ReadingDepth.BASIC,
        )

        /**
         * 高級：使用規則算出的完整打點，對所有對手各自估計並防守、考慮名次，並以進階深度讀牌。
         *
         * 不計後續風險：後續風險讓繼續進攻的選項看起來過於危險，使手牌過早放棄。
         */
        val ADVANCED: InformationLevel = InformationLevel(
            countsVisibleTiles = true,
            winValueDetail = 1.0,
            defenseScope = DefenseScope.ALL,
            considersFutureRisk = false,
            considersPlacement = true,
            readingDepth = ReadingDepth.ADVANCED,
        )
    }
}
