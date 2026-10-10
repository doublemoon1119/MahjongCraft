package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 進度資料中「麻將成果」條件的內容；與 Minecraft 版本無關。
 *
 * @property achievementIds 必須在同一次觸發中全部成立的成果 ID，至少一個。
 * @property ruleModuleId 限定的規則模組 ID；null 表示不限規則。
 * @property statistic 另外要求的統計門檻；null 表示不看統計。
 */
data class AchievementCondition(
    val achievementIds: Set<String>,
    val ruleModuleId: String? = null,
    val statistic: StatisticThreshold? = null,
) {
    init {
        require(achievementIds.isNotEmpty()) { "Achievement condition must require at least one achievement" }
        achievementIds.forEach { id -> NamespacedId.requireValid(id) { "Achievement ID must be namespaced: $id" } }
        ruleModuleId?.let { id -> NamespacedId.requireValid(id) { "Rule module ID must be namespaced: $id" } }
    }

    /**
     * [achievements] 是否滿足此條件。
     *
     * @param achievements 同一次交易中一位玩家達成的成果。
     * @param statisticValue 取得玩家目前統計值；應已包含這次交易累加的部分。
     * @return 是否成立。
     */
    fun matches(
        achievements: PlayerAchievements,
        statisticValue: (String) -> Int,
    ): Boolean = achievements.achievementIds.containsAll(achievementIds) &&
        (ruleModuleId == null || ruleModuleId == achievements.ruleModuleId) &&
        (statistic == null || statisticValue(statistic.statisticId) >= statistic.atLeast)
}
