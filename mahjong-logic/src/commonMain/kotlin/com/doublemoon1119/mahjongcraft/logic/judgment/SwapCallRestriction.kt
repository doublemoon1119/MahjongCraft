package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer

/**
 * 食替：吃或碰之後，緊接著的那次捨牌不能打出的牌。
 *
 * - 碰：不能打出與碰進的牌同一種的牌。
 * - 吃：不能打出與吃進的牌同一種的牌；吃進的牌在順子一端時，也不能打出順子另一頭外側的那種牌，
 *   例如用四萬、五萬吃三萬後不能打出六萬。吃進的牌在順子中間時只限制同一種牌。
 *
 * 牌的種類以 [Tile] 比較，外觀不同的同種牌（例如赤五與一般的五）視為同一種。限制後無牌可打時如何處理由規則決定。
 */
internal object SwapCallRestriction {
    /**
     * 一次吃或碰之後不能打出的牌。
     *
     * @param calledTile 吃或碰進的牌。
     * @param meldTiles 組成的面子中所有的牌，含 [calledTile]。
     * @param isPon 是否為碰；false 表示吃。
     * @return 不能打出的牌。
     */
    fun forbiddenTiles(calledTile: Tile, meldTiles: List<Tile>, isPon: Boolean): Set<Tile> {
        if (isPon) return setOf(calledTile)
        val numeric = calledTile as? Tile.Numeric ?: return setOf(calledTile)
        val values = meldTiles.mapNotNull { (it as? Tile.Numeric)?.value }
        val otherEnd = when (numeric.value) {
            values.min() -> numeric.value + SEQUENCE_LENGTH
            values.max() -> numeric.value - SEQUENCE_LENGTH
            else -> null
        }
        return setOfNotNull(calledTile, otherEnd?.takeIf { it in NUMERIC_VALUES }?.let { Tile.Numeric(numeric.suit, it) })
    }

    /**
     * 玩家剛吃或碰之後不能打出的牌。
     *
     * @param player 即將捨牌的玩家。
     * @return 不能打出的牌；上一個動作不是吃或碰時為空集合。
     */
    fun forbiddenAfterLastClaim(player: MahjongPlayer): Set<Tile> {
        val action = player.actionHistory.lastOrNull()
        val calledTileId = when (action) {
            is GameAction.Chi -> action.tileId
            is GameAction.Pon -> action.tileId
            else -> return emptySet()
        }
        val meld = player.hand.melds.lastOrNull { meld -> meld.tiles.any { it.id == calledTileId } } ?: return emptySet()
        return forbiddenTiles(
            calledTile = meld.tiles.first { it.id == calledTileId }.tile,
            meldTiles = meld.tiles.map { it.tile },
            isPon = action is GameAction.Pon,
        )
    }

    /** 順子一端到另一頭外側那種牌的數字差。 */
    private const val SEQUENCE_LENGTH = 3

    /** 數牌的數字範圍。 */
    private val NUMERIC_VALUES = 1..9
}
