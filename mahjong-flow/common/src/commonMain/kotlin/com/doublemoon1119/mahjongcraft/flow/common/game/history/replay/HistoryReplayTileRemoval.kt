package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 一位玩家在一輪換牌中移出的牌。
 *
 * @property seat 移出牌的玩家座位。
 * @property tiles 移出的牌，依移出順序排列。
 */
data class HistoryReplayTileRemoval(
    val seat: Int,
    val tiles: List<HistoryTileReference>,
)
