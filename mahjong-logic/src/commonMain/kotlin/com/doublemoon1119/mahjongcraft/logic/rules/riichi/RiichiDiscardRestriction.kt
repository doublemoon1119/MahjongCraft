package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.SwapCallRestriction
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import kotlin.uuid.Uuid

/**
 * 日本麻將（四人與三人）本次一般捨牌可以打出的牌。
 *
 * - 立直後只能打出剛摸入的牌。
 * - 食替（見 [SwapCallRestriction]）：吃或碰後不能打出的牌。吃碰後會無牌可打的吃碰本身不成立，
 *   由 [leavesDiscard] 在列出合法動作時排除。
 */
internal object RiichiDiscardRestriction {
    /**
     * 計算可以打出的牌。
     *
     * @param player 即將捨牌的玩家。
     * @param inRiichi 玩家是否已立直。
     * @return 可以打出的牌。不限制時為 null。
     */
    fun discardableTileIds(player: MahjongPlayer, inRiichi: Boolean): Set<Uuid>? {
        if (inRiichi) return player.hand.lastDrawn?.id?.let(::setOf)
        val forbidden = SwapCallRestriction.forbiddenAfterLastClaim(player).takeIf { it.isNotEmpty() } ?: return null
        val allowed = player.hand.standingTiles.filter { it.tile !in forbidden }.mapTo(mutableSetOf()) { it.id }
        return allowed.takeIf { it.isNotEmpty() }
    }

    /**
     * 判斷一次吃或碰之後，手上是否還有食替限制以外可以打出的牌。
     *
     * @param standingTiles 吃碰前的立牌。
     * @param usedTiles 吃碰時從立牌拿出的牌。
     * @param calledTile 吃或碰進的牌。
     * @param isPon 是否為碰。false 表示吃。
     * @return 還有可以打出的牌時為 true。
     */
    fun leavesDiscard(
        standingTiles: List<IdentifiedTile>,
        usedTiles: List<IdentifiedTile>,
        calledTile: Tile,
        isPon: Boolean,
    ): Boolean {
        val forbidden = SwapCallRestriction.forbiddenTiles(
            calledTile = calledTile,
            meldTiles = usedTiles.map { it.tile } + calledTile,
            isPon = isPon,
        )
        val usedIds = usedTiles.mapTo(mutableSetOf()) { it.id }
        return standingTiles.any { it.id !in usedIds && it.tile !in forbidden }
    }
}
