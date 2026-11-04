package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import kotlin.uuid.Uuid

/**
 * 台灣麻將本次一般捨牌可以打出的牌。
 *
 * - 宣告聽牌後只能打出剛摸入的牌。
 * - 食替：吃牌後不能打出與吃進牌相同、或順子另一頭的牌；碰牌後不能打出同一種牌。限制後無牌可打時不限制。
 */
internal object TaiwanDiscardRestriction {
    /**
     * 計算可以打出的牌。
     *
     * @param player 即將捨牌的玩家。
     * @return 可以打出的牌；不限制時為 null。
     */
    fun discardableTileIds(player: MahjongPlayer): Set<Uuid>? {
        val hand = player.hand
        if (player.components[TaiwanPlayerComponents.READY_DECLARATION] != null) {
            return hand.lastDrawn?.id?.let(::setOf)
        }
        val forbidden = forbiddenAfterClaim(player).takeIf { it.isNotEmpty() } ?: return null
        val allowed = hand.standingTiles.filter { it.tile !in forbidden }.mapTo(mutableSetOf()) { it.id }
        return allowed.takeIf { it.isNotEmpty() }
    }

    /**
     * 剛吃或碰之後不能打出的牌面。
     *
     * @param player 即將捨牌的玩家。
     * @return 不能打出的牌面；上一個動作不是吃或碰時為空集合。
     */
    private fun forbiddenAfterClaim(player: MahjongPlayer): Set<Tile> {
        val action = player.actionHistory.lastOrNull()
        val calledTileId = when (action) {
            is GameAction.Chi -> action.tileId
            is GameAction.Pon -> action.tileId
            else -> return emptySet()
        }
        val meld = player.hand.melds.lastOrNull { meld -> meld.tiles.any { it.id == calledTileId } } ?: return emptySet()
        val called = meld.tiles.first { it.id == calledTileId }.tile
        if (action is GameAction.Pon) return setOf(called)
        val numeric = called as? Tile.Numeric ?: return setOf(called)
        val values = meld.tiles.map { (it.tile as Tile.Numeric).value }
        val otherEnd = when (numeric.value) {
            values.min() -> numeric.value + 3
            values.max() -> numeric.value - 3
            else -> null
        }
        return setOfNotNull(called, otherEnd?.takeIf { it in 1..9 }?.let { Tile.Numeric(numeric.suit, it) })
    }
}
