package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 判斷目前是否仍處於「場上沒有任何人鳴牌、且每個人都還沒打出第二張牌」的第一巡。
 *
 * 用於判斷九種九牌（見 [RiichiLegalActionValidator]）與雙立直（見開局後的立直宣告 use case）
 * 是否成立——兩者都要求場上尚未出現任何鳴牌，且輪到 [player] 打牌時，[player] 本人
 * 還沒打過牌、其他人也都最多只打過一張牌。
 *
 * @param player 欲判斷的玩家（通常是即將打牌的當前玩家）。
 * @return 是否仍在第一巡且無人鳴牌。
 */
fun TableState.isFirstGoAround(player: MahjongPlayer): Boolean {
    return players.all { it.hand.exposedMelds.isEmpty() } &&
        players.all { it.discardPile.entries.size <= 1 } &&
        player.discardPile.entries.isEmpty()
}
