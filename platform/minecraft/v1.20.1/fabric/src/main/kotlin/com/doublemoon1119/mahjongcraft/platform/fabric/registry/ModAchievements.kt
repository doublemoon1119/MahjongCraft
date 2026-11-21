package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.MahjongAchievementCriterion
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementStatisticIds
import net.minecraft.advancements.CriteriaTriggers
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.stats.StatFormatter
import net.minecraft.stats.Stats

/** 麻將進度條件與通用統計的註冊入口。 */
object ModAchievements {
    /** 「麻將成果」進度條件；由 [register] 初始化。 */
    lateinit var mahjongAchievementCriterion: MahjongAchievementCriterion
        private set

    /**
     * 已註冊的統計，以統計 ID 字串索引。
     *
     * 原版自訂統計 registry 以物件本身比對值，累加與讀取統計必須使用註冊時的同一個 [ResourceLocation]。
     */
    private val statistics = mutableMapOf<String, ResourceLocation>()

    /** 註冊進度條件與全部通用統計；必須在 registry 凍結前呼叫。 */
    fun register() {
        mahjongAchievementCriterion = CriteriaTriggers.register(MahjongAchievementCriterion())
        AchievementStatisticIds.ALL.forEach { statisticId ->
            val id = ResourceLocation(statisticId)
            Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id)
            Stats.CUSTOM.get(id, StatFormatter.DEFAULT)
            statistics[statisticId] = id
        }
    }

    /**
     * 取得 [statisticId] 註冊時使用的 [ResourceLocation]。
     *
     * @param statisticId 已註冊的統計 ID。
     * @return 註冊時的 [ResourceLocation]。
     */
    fun statistic(statisticId: String): ResourceLocation = statistics.getValue(statisticId)
}
