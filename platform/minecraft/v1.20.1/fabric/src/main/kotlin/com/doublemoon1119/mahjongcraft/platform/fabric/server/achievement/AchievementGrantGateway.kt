package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements

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
