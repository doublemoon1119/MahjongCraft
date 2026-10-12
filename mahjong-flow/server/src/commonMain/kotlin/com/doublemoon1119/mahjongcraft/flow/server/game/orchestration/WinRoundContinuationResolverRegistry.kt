package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
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

    /** 目前已登記 resolver ID 的快照。 */
    val registrationKeys: Set<String> get() = resolvers.mapTo(mutableSetOf()) { it.id }

    /**
     * 登記 resolver；ID 不得重複，註冊表凍結後不可登記。
     *
     * @param resolver 要登記的胡牌後續判定器。
     */
    fun register(resolver: WinRoundContinuationResolver) {
        check(!frozen) { "Win round continuation resolver registry is frozen" }
        require(resolvers.none { it.id == resolver.id }) { "Win round continuation resolver already registered: ${resolver.id}" }
        resolvers += resolver
    }

    /** 凍結註冊表並依優先序、ID 固定判定順序；重複呼叫不改變結果。 */
    fun freeze() {
        if (frozen) return
        resolvers.sortWith(compareBy(WinRoundContinuationResolver::priority, WinRoundContinuationResolver::id))
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
}
