package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 規則專屬成果的判定；只讀取已提交的事實與交易前後的對局，不得重新計算胡牌或分數。
 *
 * 例如日麻可以從自己產生的胡牌詳情判定役滿。
 */
interface GameAchievementResolver {
    /** 對應的 namespaced 規則模組識別碼。 */
    val ruleModuleId: String

    /**
     * 判定本次交易中各玩家達成的規則專屬成果。
     *
     * @param facts 本次交易對單一桌子提交的事實。
     * @return 以玩家 UUID 索引的完整 namespaced 成果 ID；沒有成果的玩家不出現。
     */
    fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>>
}

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

/** [GameAchievementResolverRegistry] 的記憶體實作；不捕捉判定的程式錯誤。 */
class GameAchievementResolverRegistryImpl : GameAchievementResolverRegistry {
    /** 依註冊順序保存判定。 */
    private val resolvers = linkedMapOf<String, GameAchievementResolver>()

    /** 註冊規則的獨立快照。 */
    override val registrationKeys: Set<String> get() = resolvers.keys.toSet()

    /** 是否已完成註冊。 */
    override var isFrozen: Boolean = false
        private set

    /** 驗證規則識別碼並登記，重複註冊不覆蓋原判定。 */
    override fun register(resolver: GameAchievementResolver) {
        check(!isFrozen) { "Game achievement resolver registry is frozen" }
        NamespacedId.requireValid(resolver.ruleModuleId) { "Invalid achievement rule ID: ${resolver.ruleModuleId}" }
        require(resolver.ruleModuleId !in resolvers) { "Duplicate game achievement resolver: ${resolver.ruleModuleId}" }
        resolvers[resolver.ruleModuleId] = resolver
    }

    /** 只交給指定規則的判定。 */
    override fun resolve(ruleModuleId: String, facts: CommittedGameFacts): Map<Uuid, Set<String>> = resolvers[ruleModuleId]?.resolve(facts).orEmpty()

    /** 結束註冊階段。 */
    override fun freeze() {
        isFrozen = true
    }
}
