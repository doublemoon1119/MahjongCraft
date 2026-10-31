package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver

/** 擴充登記進度解析器時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface GameAchievementResolverRegistrar {
    /** 目前已登記規則 ID 的快照，供註冊統計使用。 */
    val registrationKeys: Set<String>

    /** 是否禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記判定；拒絕重複規則 ID 與凍結後的變更。
     *
     * @param resolver 規則專屬成果判定。
     */
    fun register(resolver: GameAchievementResolver)
}
