package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 規則 extension 將權威胡牌結果轉為模板鍵與強型別顯示欄位的結果。 */
data class WinSettlementResolvedDetails(
    val templateKey: String,
    val fields: List<WinSettlementDetailField>,
)

/** 不接觸 Minecraft renderer 的規則專屬胡牌詳情解析器。 */
fun interface WinSettlementDetailResolver {
    fun resolve(state: TableState, handValue: HandValueResult): WinSettlementResolvedDetails
}

/** 以完整規則模組 ID 登記、bootstrap 後凍結的胡牌詳情解析器。 */
class WinSettlementDetailResolverRegistry {
    private val resolvers = linkedMapOf<String, WinSettlementDetailResolver>()
    var isFrozen: Boolean = false
        private set

    fun register(ruleModuleId: String, resolver: WinSettlementDetailResolver) {
        check(!isFrozen) { "Win settlement detail resolver registry is frozen" }
        require(':' in ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(resolvers.putIfAbsent(ruleModuleId, resolver) == null) { "Duplicate win settlement detail resolver: $ruleModuleId" }
    }

    fun resolve(ruleModuleId: String, state: TableState, handValue: HandValueResult): WinSettlementResolvedDetails = resolvers[ruleModuleId]?.resolve(state, handValue)
        ?: WinSettlementResolvedDetails(WinSettlementPresentationRequestFactory.GENERIC_TEMPLATE_KEY, emptyList())

    fun freeze() {
        isFrozen = true
    }
}

/** 建立包含 bundled 日麻 resolver、但尚未凍結的 registry。 */
fun createBuiltInWinSettlementDetailResolverRegistry(): WinSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply {
    register(BuiltInRuleModuleIds.RIICHI) { state, handValue ->
        WinSettlementResolvedDetails(
            WinSettlementPresentationRequestFactory.RIICHI_TEMPLATE_KEY,
            WinSettlementPresentationRequestFactory.riichiDetails(state, handValue),
        )
    }
}
