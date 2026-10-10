package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 終局名次範圍；上下界均包含。
 *
 * @property minimum 最小名次。
 * @property maximum 最大名次。
 */
data class HistoryRankRange(
    val minimum: Int? = null,
    val maximum: Int? = null,
)
