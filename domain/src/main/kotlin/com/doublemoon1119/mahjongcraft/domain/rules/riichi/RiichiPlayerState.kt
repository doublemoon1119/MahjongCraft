package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 日本麻將特有的玩家狀態。
 *
 * 用於記錄玩家在遊戲過程中的立直相關狀態。
 *
 * @property riichiTile 玩家立直時打出的牌，若未立直則為 null。
 * @property doubleRiichiTile 玩家雙立直時打出的牌，若未雙立直則為 null。
 */
data class RiichiPlayerState(
    var riichiTile: IdentifiedTile? = null,
    var doubleRiichiTile: IdentifiedTile? = null
) : PlayerRuleState {

    /**
     * 玩家是否已宣告 立直/雙立直。
     */
    val isRiichi: Boolean get() = riichiTile != null || doubleRiichiTile != null

    /**
     * 玩家是否已宣告雙立直。
     */
    val isDoubleRiichi: Boolean get() = doubleRiichiTile != null

    /**
     * 取得玩家當前振聽的牌列表。
     *
     * 振聽牌來源：
     * 1. 自己打過的牌（discardPile，包含被鳴走的牌）
     * 2. 當前巡迴放過的榮和（passedTilesInRound）
     *
     * @param discardPile 玩家的牌河。
     * @param passedTilesInRound 當前巡迴中放過的牌（用於同巡振聽）。
     * @return 玩家當前振聽的牌集合。
     */
    fun getFuritenTiles(
        discardPile: DiscardPile<*>,
        passedTilesInRound: Set<Tile>
    ): Set<Tile> {
        val discardedTiles = discardPile.entries
            .map { it.tile.tile.withoutRed }
            .toSet()

        return discardedTiles + passedTilesInRound
    }
}
