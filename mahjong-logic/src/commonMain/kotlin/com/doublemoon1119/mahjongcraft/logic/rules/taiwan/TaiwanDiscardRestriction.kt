package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.judgment.SwapCallRestriction
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import kotlin.uuid.Uuid

/**
 * 台灣麻將本次一般捨牌可以打出的牌。
 *
 * - 宣告聽牌後只能打出剛摸入的牌。
 * - 食替（見 [SwapCallRestriction]）：吃或碰後不能打出的牌。限制後無牌可打時不限制。
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
        val forbidden = SwapCallRestriction.forbiddenAfterLastClaim(player).takeIf { it.isNotEmpty() } ?: return null
        val allowed = hand.standingTiles.filter { it.tile !in forbidden }.mapTo(mutableSetOf()) { it.id }
        return allowed.takeIf { it.isNotEmpty() }
    }
}
