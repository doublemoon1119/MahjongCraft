package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 可於 bootstrap 登記、完成後凍結的 win round continuation resolver registry。
 *
 * 未替某規則模組登記任何 resolver 時，[resolve] 固定回傳 [WinRoundDirective.EndRound]：沒有登記
 * resolver 的規則，胡牌後一律立即結束本局。
 */
class WinRoundContinuationResolverRegistry {
    /** 尚未凍結的 resolver 集合。 */
    private val resolvers = mutableListOf<WinRoundContinuationResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記 resolver 的完整身分快照，格式為 `ruleModuleId/resolverId`。 */
    val registrationKeys: Set<String> get() = resolvers.mapTo(mutableSetOf()) { registrationKey(it.ruleModuleId, it.id) }

    /**
     * 登記 resolver；同一規則模組內的 resolver ID 不得重複，不同規則模組可使用相同 ID。
     * 註冊表凍結後不可登記。
     *
     * @param resolver 要登記的胡牌後續判定器。
     */
    fun register(resolver: WinRoundContinuationResolver) {
        check(!frozen) { "Win round continuation resolver registry is frozen" }
        NamespacedId.requireValid(resolver.ruleModuleId) { "Rule module ID must be namespaced: ${resolver.ruleModuleId}" }
        NamespacedId.requireValid(resolver.id) { "Resolver ID must be namespaced: ${resolver.id}" }
        require(resolvers.none { it.id == resolver.id && it.ruleModuleId == resolver.ruleModuleId }) {
            "Win round continuation resolver already registered: ${resolver.ruleModuleId}/${resolver.id}"
        }
        resolvers += resolver
    }

    /** 凍結註冊表並依優先序、ID 固定判定順序；重複呼叫不改變結果。 */
    fun freeze() {
        if (frozen) return
        resolvers.sortWith(
            compareByDescending<WinRoundContinuationResolver> { it.priority }
                .thenBy { registrationKey(it.ruleModuleId, it.id) },
        )
        frozen = true
    }

    /**
     * 依 [ruleModule] 解析本次胡牌後的權威決策；沒有任何 resolver 成立時固定回傳 [WinRoundDirective.EndRound]。
     *
     * @param context 胡牌即時結算後的流程情境。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 第一個成立的後續決策；沒有 resolver 成立時為 [WinRoundDirective.EndRound]。
     */
    fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective {
        check(frozen) { "Win round continuation resolver registry must be frozen before use" }
        return resolvers.asSequence()
            .filter { it.ruleModuleId == ruleModule.id }
            .mapNotNull { it.resolve(context, ruleModule) }
            .firstOrNull() ?: WinRoundDirective.EndRound
    }

    /**
     * 建立跨規則模組穩定識別碼，供快照、診斷與同優先序排序使用。
     *
     * @param ruleModuleId resolver 所屬規則模組的 namespaced ID。
     * @param id resolver 的 namespaced ID。
     * @return 將規則模組與 resolver ID 組合後的穩定註冊鍵。
     */
    private fun registrationKey(ruleModuleId: String, id: String): String = "$ruleModuleId/$id"
}
