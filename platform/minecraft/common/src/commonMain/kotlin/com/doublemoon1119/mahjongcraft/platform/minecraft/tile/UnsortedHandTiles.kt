package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import kotlin.uuid.Uuid

/**
 * 判斷哪些立牌是直接補進手牌、還沒理過的牌；共享布局把它們依補進的順序接在立牌最右邊。
 *
 * 從牌牆、保留區或自己的摸牌位直接進入立牌的牌還沒理過，例如逐輪補牌時補到的牌。玩家在同一步打出牌或把手上的牌移到副露
 * 時，表示玩家正在整理手牌，進入立牌的牌直接理進去，先前還沒理過的牌也一起理好。玩家整理手牌時，該玩家的牌都理進去。
 */
object UnsortedHandTiles {
    /** 直接補進立牌的來源區域。 */
    private val DIRECT_SOURCES = setOf(TileRegion.WALL, TileRegion.RESERVED)

    /**
     * 算出這一步之後還沒理過的牌。
     *
     * @param previous 這一步開始時的擺放；呈現端還沒有任何擺放時為 null，所有牌都視為已理好。
     * @param state 這一步之後的桌況。
     * @param sortedPlayerIds 這一步整理手牌的玩家。
     * @return 還沒理過的牌，每位玩家依補進的順序排列。
     */
    fun after(previous: Map<Uuid, ArrangedTile>?, state: TableState, sortedPlayerIds: Set<Uuid>): List<Uuid> {
        if (previous == null) return emptyList()
        val discardedOrMelded = state.players.flatMapTo(mutableSetOf()) { player ->
            player.discardPile.entries.map { it.tile.id } + player.hand.melds.flatMap { meld -> meld.tiles.map { it.id } }
        }
        return state.players.flatMap { player ->
            val acted = previous.values.any { before ->
                before.row.ownerId == player.id && before.row.region in setOf(TileRegion.HAND, TileRegion.DRAWN) && before.tileId in discardedOrMelded
            }
            if (player.id in sortedPlayerIds || acted) return@flatMap emptyList()
            val standingIds = player.hand.tiles.map { it.id }
            val kept = previous.values
                .filter { it.unsorted && it.row.ownerId == player.id && it.tileId in standingIds }
                .sortedBy { it.orderInRow }
                .map { it.tileId }
            val joined = standingIds.filter { id ->
                id !in kept && previous[id]?.row?.let { row -> row.region in DIRECT_SOURCES || (row.region == TileRegion.DRAWN && row.ownerId == player.id) } == true
            }
            kept + joined
        }
    }
}
