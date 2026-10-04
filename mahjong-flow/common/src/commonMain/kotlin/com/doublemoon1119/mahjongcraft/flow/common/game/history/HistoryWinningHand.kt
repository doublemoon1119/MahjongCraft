package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/**
 * 結算時依規則牌序整理的立牌及獨立和牌張，不重複保存牌種或副露。
 *
 * @property standingTileIds 不含和牌張的有序立牌實體識別碼。
 * @property winningTileId 權威和牌張；特殊結算沒有和牌張時為 null。
 */
data class HistoryWinningHand(
    val standingTileIds: List<Uuid>,
    val winningTileId: Uuid?,
) {
    init {
        require(standingTileIds.distinct().size == standingTileIds.size) { "Winning hand contains duplicate standing tiles" }
        require(winningTileId !in standingTileIds) { "Winning tile must be separate from standing tiles" }
    }
}
