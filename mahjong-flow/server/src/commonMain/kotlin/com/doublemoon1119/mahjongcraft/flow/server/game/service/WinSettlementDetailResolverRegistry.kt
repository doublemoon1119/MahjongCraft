package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 把權威胡牌結果轉成規則專屬胡牌詳情欄位的解析器；只輸出語意資料，顯示方式由平台決定。 */
interface WinSettlementDetailResolver {
    /** 解析一般胡牌（自摸／榮和）的詳情；[handValue] 一律是實際成立的胡牌結果。 */
    fun resolve(state: TableState, handValue: HandValueResult): List<WinSettlementDetailField>

    /**
     * 解析不含 [HandValueResult] 的特殊 win-equivalent outcome（例如流局滿貫）詳情；不認得該
     * [outcome] 時回傳 `null`，讓 registry 落回空欄位。
     */
    fun resolveSpecialOutcome(state: TableState, outcome: ResolvedRoundOutcome): List<WinSettlementDetailField>? = null
}

/** 以完整規則模組 ID 登記、bootstrap 後凍結的胡牌詳情解析器。 */
class WinSettlementDetailResolverRegistry {
    private val resolvers = linkedMapOf<String, WinSettlementDetailResolver>()

    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String> get() = resolvers.keys.toSet()
    var isFrozen: Boolean = false
        private set

    fun register(ruleModuleId: String, resolver: WinSettlementDetailResolver) {
        check(!isFrozen) { "Win settlement detail resolver registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(resolvers.putIfAbsent(ruleModuleId, resolver) == null) { "Duplicate win settlement detail resolver: $ruleModuleId" }
    }

    fun resolve(ruleModuleId: String, state: TableState, handValue: HandValueResult): List<WinSettlementDetailField> = resolvers[ruleModuleId]?.resolve(state, handValue)
        ?: emptyList()

    fun resolveSpecialOutcome(ruleModuleId: String, state: TableState, outcome: ResolvedRoundOutcome): List<WinSettlementDetailField> = resolvers[ruleModuleId]?.resolveSpecialOutcome(state, outcome)
        ?: emptyList()

    fun freeze() {
        isFrozen = true
    }
}
