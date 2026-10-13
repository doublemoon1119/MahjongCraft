package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single

/** 可於 bootstrap 登記、完成後凍結的 post-reaction outcome resolver registry。 */
@Single
class PostReactionRoundOutcomeResolverRegistry {
    /** 尚未凍結的 resolver 集合。 */
    private val resolvers = mutableListOf<PostReactionRoundOutcomeResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記 resolver ID 的快照。 */
    val registrationKeys: Set<String> get() = resolvers.mapTo(mutableSetOf()) { registrationKey(it.ruleModuleId, it.id) }

    /**
     * 登記 resolver；同一規則模組內 ID 不得重複，同一個 ID 可由不同規則模組各自登記。
     *
     * @param resolver 要登記的判定器。
     */
    fun register(resolver: PostReactionRoundOutcomeResolver) {
        check(!frozen) { "Post-reaction round outcome resolver registry is frozen" }
        NamespacedId.requireValid(resolver.ruleModuleId) { "Rule module ID must be namespaced: ${resolver.ruleModuleId}" }
        NamespacedId.requireValid(resolver.id) { "Resolver ID must be namespaced: ${resolver.id}" }
        require(resolvers.none { it.id == resolver.id && it.ruleModuleId == resolver.ruleModuleId }) {
            "Round outcome resolver already registered: ${resolver.ruleModuleId}/${resolver.id}"
        }
        resolvers += resolver
    }

    /** 凍結註冊表並依優先序、ID 固定判定順序；重複呼叫不改變結果。 */
    fun freeze() {
        if (frozen) return
        resolvers.sortWith(compareByDescending<PostReactionRoundOutcomeResolver> { it.priority }.thenBy { registrationKey(it.ruleModuleId, it.id) })
        frozen = true
    }

    /**
     * 依穩定順序回傳第一個成立的回合結果；註冊表必須先凍結。
     *
     * @param tableState 完成反應後的權威桌況。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 第一個成立的特殊回合結果；沒有 resolver 成立時為 null。
     */
    fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome? {
        check(frozen) { "Post-reaction round outcome resolver registry must be frozen before use" }
        return resolvers.asSequence()
            .filter { it.ruleModuleId == ruleModule.id }
            .mapNotNull { it.resolve(tableState, ruleModule) }
            .firstOrNull()
    }

    /**
     * 建立跨規則模組穩定識別碼，供快照與同優先序排序使用。
     *
     * @param ruleModuleId resolver 所屬規則模組的 namespaced ID。
     * @param id resolver 的 namespaced ID。
     * @return 將規則模組與 resolver ID 組合後的穩定註冊鍵。
     */
    private fun registrationKey(ruleModuleId: String, id: String): String = "$ruleModuleId/$id"
}
