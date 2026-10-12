package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** [GameAchievementResolverRegistry] 的記憶體實作；不捕捉判定的程式錯誤。 */
@Single(binds = [GameAchievementResolverRegistry::class])
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
