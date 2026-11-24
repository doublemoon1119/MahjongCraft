package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementStatisticIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.achievement.MahjongAchievementCriterion
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.registerCriterion
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.stats.StatFormatter
import net.minecraft.stats.Stats

/** 麻將進度條件與通用統計的註冊入口。 */
object ModAchievements {
    /** 「麻將成果」進度條件；[register] 交出的登記完成後初始化。 */
    lateinit var mahjongAchievementCriterion: MahjongAchievementCriterion
        private set

    /**
     * 已註冊的統計，以統計 ID 字串索引。
     *
     * 原版自訂統計 registry 以物件本身比對值，累加與讀取統計必須使用註冊時的同一個 [ResourceLocation]。
     */
    private val statistics = mutableMapOf<String, ResourceLocation>()

    /**
     * 登記進度條件與全部通用統計。
     *
     * 統計項目在統計 ID 寫入登記表之後才建立，統計畫面因此會列出玩家還沒獲得過的統計；統計項目在登記完成後
     * 由載入器安排的時機加入，不與其他 mod 的初始化同時寫入原版共用的統計表。
     *
     * @param registrar 寫入登記表的方式。
     */
    fun register(registrar: ModRegistrar) {
        AchievementStatisticIds.ALL.forEach { statisticId ->
            val id = resourceId(statisticId)
            registrar.register(registry = Registries.CUSTOM_STAT, id = id) { id }
            statistics[statisticId] = id
        }
        registerCriterion(
            registrar = registrar,
            id = MahjongAchievementCriterion.ID,
            factory = ::MahjongAchievementCriterion,
            onRegistered = { criterion -> mahjongAchievementCriterion = criterion },
        )
        registrar.afterRegistration {
            statistics.values.forEach { id -> Stats.CUSTOM.get(id, StatFormatter.DEFAULT) }
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
