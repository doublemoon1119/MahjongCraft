package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

/**
 * 執行一位玩家同一次交易的成果授予順序。
 *
 * 先累加成果對應的統計，再觸發進度條件，確保條件讀到已包含本次交易的統計值。
 * 統計 ID 由成果集合去重，讓同一筆交易不會重複累加同一項統計。
 */
object AchievementGrantExecutor {
    /**
     * 先累加統計，再觸發一次進度條件。
     *
     * @param achievements 同一次交易中一位玩家達成的成果。
     * @param incrementStatistic 累加一項統計的操作。
     * @param triggerCriterion 在所有統計成功累加後觸發進度條件的操作。
     */
    fun grant(
        achievements: PlayerAchievements,
        incrementStatistic: (String) -> Unit,
        triggerCriterion: () -> Unit,
    ) {
        AchievementStatisticIds.statisticsFor(achievements.achievementIds).forEach(incrementStatistic)
        triggerCriterion()
    }
}
