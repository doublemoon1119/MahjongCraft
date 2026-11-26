package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 統計門檻：玩家的統計值至少要達到 [atLeast]。
 *
 * @property statisticId 統計 ID。
 * @property atLeast 最低值，至少為 1。
 */
data class StatisticThreshold(
    val statisticId: String,
    val atLeast: Int,
) {
    init {
        NamespacedId.requireValid(statisticId) { "Statistic ID must be namespaced: $statisticId" }
        require(atLeast >= 1) { "Statistic threshold must be at least 1" }
    }
}
