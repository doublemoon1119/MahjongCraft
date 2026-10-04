package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 保存結算順序的局內立牌及獨立和牌張參照。
 *
 * @property standingTiles 不含和牌張的有序立牌參照。
 * @property winningTile 獨立和牌張；特殊結算沒有和牌張時為 null。
 */
data class HistoryReplayWinningHand(
    val standingTiles: List<HistoryTileReference>,
    val winningTile: HistoryTileReference?,
)
