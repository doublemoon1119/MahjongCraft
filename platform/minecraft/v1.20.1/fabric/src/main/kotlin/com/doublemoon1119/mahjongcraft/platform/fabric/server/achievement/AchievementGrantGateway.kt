package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModAchievements
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementStatisticIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import net.minecraft.stat.Stats
import org.koin.core.annotation.Single

/** 把一組成果交給 Minecraft 的統計與進度系統。 */
interface AchievementGrantGateway {
    /**
     * 為在線玩家累加統計並觸發進度條件；玩家不在線時不做任何事。
     *
     * 必須在伺服器主執行緒呼叫。
     *
     * @param achievements 同一次交易中一位玩家達成的成果。
     * @return 玩家是否在線並已處理。
     */
    fun grant(achievements: PlayerAchievements): Boolean
}

/**
 * [AchievementGrantGateway] 的 Minecraft 實作：先累加統計，再觸發進度條件，
 * 讓依統計門檻判定的進度讀到已累加的值。
 *
 * @property serverHolder 取得目前伺服器與在線玩家。
 */
@Single(binds = [AchievementGrantGateway::class])
class FabricAchievementGrantGateway(
    private val serverHolder: FabricServerHolder,
) : AchievementGrantGateway {
    override fun grant(achievements: PlayerAchievements): Boolean {
        val player = serverHolder.findPlayer(achievements.playerId) ?: return false
        AchievementStatisticIds.statisticsFor(achievements.achievementIds).forEach { statisticId ->
            player.incrementStat(ModAchievements.statistic(statisticId))
        }
        ModAchievements.mahjongAchievementCriterion.trigger(player, achievements) { statisticId ->
            player.statHandler.getStat(Stats.CUSTOM.getOrCreateStat(ModAchievements.statistic(statisticId)))
        }
        return true
    }
}
