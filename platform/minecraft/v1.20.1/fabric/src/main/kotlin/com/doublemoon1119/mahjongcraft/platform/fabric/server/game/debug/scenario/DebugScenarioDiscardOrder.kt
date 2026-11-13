package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import kotlin.uuid.Uuid

/**
 * 情境直接排好牌河時的打牌順序：依座位順序輪流，先排每家第一張、再排每家第二張，依此類推；[last] 是剛打出的牌時排在最後。
 *
 * @param players 依座位順序排列的玩家，牌河已排好。
 * @param last 最後打出的牌；沒有指定時為 null。
 * @return 每張捨牌剛好出現一次的打牌順序。
 */
internal fun scenarioDiscardOrder(players: List<MahjongPlayer>, last: Uuid? = null): List<Uuid> {
    val piles = players.map { player -> player.discardPile.entries.map { it.tile.id } }
    val interleaved = (0 until (piles.maxOfOrNull { it.size } ?: 0)).flatMap { index -> piles.mapNotNull { it.getOrNull(index) } }
    return if (last == null) interleaved else interleaved.filterNot { it == last } + last
}
