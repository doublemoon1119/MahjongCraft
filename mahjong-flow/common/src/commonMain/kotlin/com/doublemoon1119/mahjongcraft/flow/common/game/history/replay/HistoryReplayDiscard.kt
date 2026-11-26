package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 歷史捨牌。
 *
 * @property tile 捨出牌。
 * @property isTaken 是否已被副露取走。
 * @property markers 規則提供的 namespaced 捨牌標記。
 */
data class HistoryReplayDiscard(
    val tile: HistoryTileReference,
    val isTaken: Boolean,
    val markers: Set<String>,
)
