package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 最後捨牌的所有反應結束後，以純邏輯判定一種特殊 round outcome。 */
interface PostReactionRoundOutcomeResolver {
    /** Resolver 的完整 namespaced ID，同時作為穩定排序的最後決勝鍵。 */
    val id: String

    /** 此 resolver 所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 數值越小越先判定；同優先序再依 [id] 排序。 */
    val priority: Int

    /** 成立時回傳完整權威結果，否則回傳 `null` 讓下一個 resolver 繼續判定。 */
    fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome?
}

/** 可於 bootstrap 登記、完成後凍結的 post-reaction outcome resolver registry。 */
class PostReactionRoundOutcomeResolverRegistry {
    /** 尚未凍結的 resolver 集合。 */
    private val resolvers = mutableListOf<PostReactionRoundOutcomeResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 登記 resolver；ID 不得重複。 */
    fun register(resolver: PostReactionRoundOutcomeResolver) {
        check(!frozen) { "Post-reaction round outcome resolver registry is frozen" }
        require(resolvers.none { it.id == resolver.id }) { "Round outcome resolver already registered: ${resolver.id}" }
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
