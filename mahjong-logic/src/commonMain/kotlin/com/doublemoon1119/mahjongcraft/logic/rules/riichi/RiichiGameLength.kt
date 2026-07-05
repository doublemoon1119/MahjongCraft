package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.config.GameLength

/**
 * 立直麻將的遊戲長度
 */
sealed interface RiichiGameLength : GameLength {
    /**
     * 一局
     */
    data object OneGame : RiichiGameLength {
        override val totalRounds: Int = 1
        override val roundsOffset: Int = 0
    }

    /**
     * 東風戰
     */
    data object East : RiichiGameLength {
        override val totalRounds: Int = 4
        override val roundsOffset: Int = 0
    }

    /**
     * 半莊 (東南風)
     */
    data object TwoWinds : RiichiGameLength {
        override val totalRounds: Int = 8
        override val roundsOffset: Int = 0
    }
}