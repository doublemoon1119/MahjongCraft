package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 排名比較所需的最小玩家資訊，讓 [MahjongRuleModule] 的排名邏輯
 * （[MahjongRuleModule.compareForRoundRanking]／[MahjongRuleModule.compareForMatchRanking]）不用
 * 綁死在 [MahjongPlayer] 或 [MahjongPlayerSnapshot] 任一種具體型別上——兩者都已經有這三個欄位，只要
 * 加上這個標記介面就自動滿足，不需要額外實作任何成員。
 */
interface RankablePlayer {
    val score: Int
    val currentWind: Wind
    val initialSeat: Wind
}
