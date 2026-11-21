package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModAchievements
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementGrantExecutor
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import net.minecraft.stats.Stats

/**
 * [AchievementGrantGateway] 的 Minecraft 實作：先累加統計，再觸發進度條件，
 * 讓依統計門檻判定的進度讀到已累加的值。
 *
 * @property serverHolder 取得目前伺服器與在線玩家。
 */
class FabricAchievementGrantGateway(
    private val serverHolder: FabricServerHolder,
) : AchievementGrantGateway {
    override fun grant(achievements: PlayerAchievements): Boolean {
        val player = serverHolder.findPlayer(achievements.playerId) ?: return false
        AchievementGrantExecutor.grant(
            achievements = achievements,
            incrementStatistic = { statisticId -> player.awardStat(ModAchievements.statistic(statisticId)) },
            triggerCriterion = {
                ModAchievements.mahjongAchievementCriterion.trigger(player, achievements) { statisticId ->
                    player.stats.getValue(Stats.CUSTOM.get(ModAchievements.statistic(statisticId)))
                }
            },
        )
        return true
    }
}
