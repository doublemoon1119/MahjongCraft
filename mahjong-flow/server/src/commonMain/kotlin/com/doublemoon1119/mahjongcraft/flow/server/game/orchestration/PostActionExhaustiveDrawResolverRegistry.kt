package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 可於 bootstrap 登記、完成後凍結的主動觸發途中流局 resolver registry。 */
class PostActionExhaustiveDrawResolverRegistry {
    /** 尚未凍結的 resolver 集合。 */
    private val resolvers = mutableListOf<PostActionExhaustiveDrawResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記 resolver ID 的快照。 */
    val registrationKeys: Set<String> get() = resolvers.mapTo(mutableSetOf()) { registrationKey(it.ruleModuleId, it.id) }

    /**
     * 登記 resolver；同一規則模組內 ID 不得重複，同一個 ID 可由不同規則模組各自登記。
     *
     * @param resolver 要登記的判定器。
     */
    fun register(resolver: PostActionExhaustiveDrawResolver) {
        check(!frozen) { "Post-action exhaustive draw resolver registry is frozen" }
        NamespacedId.requireValid(resolver.ruleModuleId) { "Rule module ID must be namespaced: ${resolver.ruleModuleId}" }
        NamespacedId.requireValid(resolver.id) { "Resolver ID must be namespaced: ${resolver.id}" }
        require(resolvers.none { it.id == resolver.id && it.ruleModuleId == resolver.ruleModuleId }) {
            "Post-action exhaustive draw resolver already registered: ${resolver.ruleModuleId}/${resolver.id}"
        }
        resolvers += resolver
    }

    /** 凍結註冊表並依優先序、ID 固定判定順序；重複呼叫不改變結果。 */
    fun freeze() {
        if (frozen) return
        resolvers.sortWith(compareByDescending<PostActionExhaustiveDrawResolver> { it.priority }.thenBy { registrationKey(it.ruleModuleId, it.id) })
        frozen = true
    }

    /**
     * 依穩定順序回傳第一個成立的流局原因；註冊表必須先凍結。
     *
     * @param context 已完成動作及反應後的權威情境。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 第一個成立的流局原因；沒有 resolver 成立時為 null。
     */
    fun resolve(context: CompletedGameActionContext, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        check(frozen) { "Post-action exhaustive draw resolver registry must be frozen before use" }
        return resolvers.asSequence()
            .filter { it.ruleModuleId == ruleModule.id }
            .mapNotNull { it.resolve(context, ruleModule) }
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

/** 將同一途中流局動作記錄至所有玩家的權威動作歷史。 */
internal fun TableState.recordExhaustiveDrawForAllPlayers(reason: ExhaustiveDrawReason): TableState = copy(
    players = players.map { player -> player.recordAction(GameAction.ExhaustiveDraw(reason)) },
)
