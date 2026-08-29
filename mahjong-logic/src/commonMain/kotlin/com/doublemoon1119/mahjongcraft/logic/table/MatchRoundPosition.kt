package com.doublemoon1119.mahjongcraft.logic.table

/** 一個局位在原定賽程或延長賽中的階段。 */
enum class MatchRoundPhase {
    /** 規則原定賽程。 */
    REGULAR,

    /** 原定賽程結束後的延長賽。 */
    EXTRA,
}

/**
 * 整場對局中的權威局位。
 *
 * @property sequenceIndex 從零開始、跨場風遞增的穩定賽程序號。
 * @property prevalentWind 此局場風。
 * @property localRoundNumber 此場風內從一開始的局數。
 * @property phase 此局屬於原定賽程或延長賽。
 */
data class MatchRoundPosition(
    val sequenceIndex: Int,
    val prevalentWind: Wind,
    val localRoundNumber: Int,
    val phase: MatchRoundPhase = MatchRoundPhase.REGULAR,
) {
    init {
        require(sequenceIndex >= 0) { "Match round sequenceIndex must not be negative" }
        require(localRoundNumber > 0) { "Match round localRoundNumber must be positive" }
    }

    /** 供既有事件與呈現使用、從一開始的絕對局數。 */
    val roundNumber: Int get() = sequenceIndex + 1

    companion object {
        /** 一般麻將對局的預設初始局位。 */
        fun initial(): MatchRoundPosition = MatchRoundPosition(0, Wind.EAST, 1)
    }
}
