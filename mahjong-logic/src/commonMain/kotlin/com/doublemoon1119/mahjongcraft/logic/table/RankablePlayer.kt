package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 排名比較所需的最小玩家資訊，讓 [MahjongRuleModule] 的排名邏輯
 * （[MahjongRuleModule.compareForRoundRanking]／[MahjongRuleModule.compareForMatchRanking]）不用
 * 綁死在 [MahjongPlayer] 或玩家快照任一種具體型別上——兩者都已經有這三個欄位，只要
 * 加上這個標記介面就自動滿足，不需要額外實作任何成員。
 */
interface RankablePlayer {
    /** 玩家目前的分數。 */
    val score: Int

    /** 本局由規則指派的自風。 */
    val seatWind: Wind

    /** 整場固定的起家座位順位。 */
    val initialSeatIndex: Int
}
