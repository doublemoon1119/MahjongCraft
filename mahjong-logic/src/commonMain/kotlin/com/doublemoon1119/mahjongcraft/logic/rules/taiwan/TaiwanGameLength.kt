package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.config.GameLength

/**
 * 台灣麻將的遊戲長度
 */
sealed interface TaiwanGameLength : GameLength {
    /**
     * 一局
     */
    data object OneGame : TaiwanGameLength {
        override val totalRounds: Int = 1
        override val roundsOffset: Int = 0
    }

    /**
     * 東風戰
     */
    data object East : TaiwanGameLength {
        override val totalRounds: Int = 4
        override val roundsOffset: Int = 0
    }

    /**
     * 東南風
     */
    data object TwoWinds : TaiwanGameLength {
        override val totalRounds: Int = 8
        override val roundsOffset: Int = 0
    }

    /**
     * 東南西北風
     */
    data object FourWinds : TaiwanGameLength {
        override val totalRounds: Int = 16
        override val roundsOffset: Int = 0
    }
}
