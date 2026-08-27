package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/** 一次胡牌即時結算完成後，以規則特有邏輯判斷本局後續應採取的權威決策。 */
interface WinRoundContinuationResolver {
    /** Resolver 的完整 namespaced ID，同時作為穩定排序的最後決勝鍵。 */
    val id: String

    /** 此 resolver 所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 數值越小越先判定；同優先序再依 [id] 排序。 */
    val priority: Int

    /** 成立時回傳這次的權威決策，否則回傳 `null` 讓下一個 resolver 繼續判定。 */
    fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective?
}

/**
 * 可於 bootstrap 登記、完成後凍結的 win round continuation resolver registry。
 *
 * 未替某規則模組登記任何 resolver 時，[resolve] 固定回傳 [WinRoundDirective.EndRound]——因此既有
 * 日麻／台麻與未特別擴充的第三方規則，胡牌後立即結束本局的既有行為完全不變。
 */
class WinRoundContinuationResolverRegistry {
    /** 尚未凍結的 resolver 集合。 */
    private val resolvers = mutableListOf<WinRoundContinuationResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 登記 resolver；ID 不得重複。 */
    fun register(resolver: WinRoundContinuationResolver) {
        check(!frozen) { "Win round continuation resolver registry is frozen" }
        require(resolvers.none { it.id == resolver.id }) { "Win round continuation resolver already registered: ${resolver.id}" }
        resolvers += resolver
    }

    /** 凍結並固定後續判定順序。 */
    fun freeze() {
        if (frozen) return
        resolvers.sortWith(compareBy(WinRoundContinuationResolver::priority, WinRoundContinuationResolver::id))
        frozen = true
    }

    /** 依 [ruleModule] 解析本次胡牌後的權威決策；沒有任何 resolver 成立時固定回傳 [WinRoundDirective.EndRound]。 */
    fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective {
        check(frozen) { "Win round continuation resolver registry must be frozen before use" }
        return resolvers.asSequence()
            .filter { it.ruleModuleId == ruleModule.id }
            .mapNotNull { it.resolve(context, ruleModule) }
            .firstOrNull() ?: WinRoundDirective.EndRound
    }
}
