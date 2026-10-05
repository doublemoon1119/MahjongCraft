package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.MahjongAchievementCriterion
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementStatisticIds
import net.minecraft.advancement.criterion.Criteria
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.stat.StatFormatter
import net.minecraft.stat.Stats
import net.minecraft.util.Identifier

/** 麻將進度條件與通用統計的註冊入口。 */
object ModAchievements {
    /** 「麻將成果」進度條件；由 [register] 初始化。 */
    lateinit var mahjongAchievementCriterion: MahjongAchievementCriterion
        private set

    /**
     * 已註冊的統計，以統計 ID 字串索引。
     *
     * 原版自訂統計 registry 以物件本身比對值，累加與讀取統計必須使用註冊時的同一個 [Identifier]。
     */
    private val statistics = mutableMapOf<String, Identifier>()

    /** 註冊進度條件與全部通用統計；必須在 registry 凍結前呼叫。 */
    fun register() {
        mahjongAchievementCriterion = Criteria.register(MahjongAchievementCriterion())
        AchievementStatisticIds.ALL.forEach { statisticId ->
            val id = Identifier(statisticId)
            Registry.register(Registries.CUSTOM_STAT, id, id)
            Stats.CUSTOM.getOrCreateStat(id, StatFormatter.DEFAULT)
            statistics[statisticId] = id
        }
    }

    /**
     * 取得 [statisticId] 註冊時使用的 [Identifier]。
     *
     * @param statisticId 已註冊的統計 ID。
     * @return 註冊時的 [Identifier]。
     */
    fun statistic(statisticId: String): Identifier = statistics.getValue(statisticId)
}
