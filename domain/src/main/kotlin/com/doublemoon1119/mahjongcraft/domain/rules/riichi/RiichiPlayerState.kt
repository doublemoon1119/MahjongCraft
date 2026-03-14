package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.PlayerRuleState

/**
 * 日本麻將特有的玩家狀態。
 *
 * 用於記錄玩家在遊戲過程中的立直相關狀態。
 *
 * @property isRiichiDeclared 玩家是否已宣告立直。
 */
data class RiichiPlayerState(
    var isRiichiDeclared: Boolean = false
) : PlayerRuleState {
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
            .map { it.tile.tile.stripRed() }
            .toSet()

        return discardedTiles + passedTilesInRound
    }

    /**
     * 取得牌的基礎類型（忽略赤寶牌屬性）。
     */
    private fun Tile.stripRed(): Tile = when (this) {
        is Tile.Numeric -> Tile.Numeric(suit, value, isRed = false)
        else -> this
    }
}
