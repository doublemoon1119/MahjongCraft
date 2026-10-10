package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 歷史玩家桌況。
 *
 * @property initialSeatIndex 初始座位。
 * @property handTiles 立牌。
 * @property melds 副露。
 * @property lastDrawn 最近摸牌。
 * @property discards 捨牌。
 * @property score 分數。
 * @property seatWind 保存的座風。
 * @property playerRuleState 規則公開資訊。
 * @property setAsideTiles 移出手牌、公開擺在桌上的牌（例如三人日麻拔出的北），依移出順序排列；沒有時為空清單。
 */
data class HistoryReplayPlayerState(
    val initialSeatIndex: Int,
    val handTiles: List<HistoryTileReference>,
    val melds: List<HistoryReplayMeld>,
    val lastDrawn: HistoryTileReference?,
    val discards: List<HistoryReplayDiscard>,
    val score: Int,
    val seatWind: Wind,
    val playerRuleState: HistoryReplayRuleInformation?,
    val setAsideTiles: List<HistoryTileReference>,
)
