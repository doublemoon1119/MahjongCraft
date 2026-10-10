package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 可於 bootstrap 登記、完成後凍結的 post-reaction outcome resolver registry。 */
class PostReactionRoundOutcomeResolverRegistry {
    /** 尚未凍結的 resolver 集合。 */
    private val resolvers = mutableListOf<PostReactionRoundOutcomeResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記 resolver ID 的快照。 */
    val registrationKeys: Set<String> get() = resolvers.mapTo(mutableSetOf()) { it.id }

    /** 登記 resolver；同一規則模組內 ID 不得重複，同一個 ID 可由不同規則模組各自登記。 */
    fun register(resolver: PostReactionRoundOutcomeResolver) {
        check(!frozen) { "Post-reaction round outcome resolver registry is frozen" }
        require(resolvers.none { it.id == resolver.id && it.ruleModuleId == resolver.ruleModuleId }) {
            "Round outcome resolver already registered: ${resolver.ruleModuleId}/${resolver.id}"
        }
        resolvers += resolver
    }

    /** 凍結並固定後續判定順序。 */
    fun freeze() {
        if (frozen) return
        resolvers.sortWith(compareBy(PostReactionRoundOutcomeResolver::priority, PostReactionRoundOutcomeResolver::id))
        frozen = true
    }

    /** 依穩定順序回傳第一個成立的 outcome。 */
    fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome? {
        check(frozen) { "Post-reaction round outcome resolver registry must be frozen before use" }
        return resolvers.asSequence()
            .filter { it.ruleModuleId == ruleModule.id }
            .mapNotNull { it.resolve(tableState, ruleModule) }
            .firstOrNull()
    }
}
