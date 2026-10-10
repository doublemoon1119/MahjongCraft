package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import kotlin.uuid.Uuid

/** 供內建與第三方規則登記成果判定的凍結式 registry。 */
interface GameAchievementResolverRegistry {
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

    /**
     * 只以 [ruleModuleId] 登記的判定處理 [facts]；沒有登記的規則不產生規則專屬成果。
     *
     * @param ruleModuleId 這場對局的規則模組 ID。
     * @param facts 本次交易對單一桌子提交的事實。
     * @return 以玩家 UUID 索引的成果 ID。
     */
    fun resolve(ruleModuleId: String, facts: CommittedGameFacts): Map<Uuid, Set<String>>

    /** 凍結判定集合，不影響已登記判定的使用。 */
    fun freeze()
}
