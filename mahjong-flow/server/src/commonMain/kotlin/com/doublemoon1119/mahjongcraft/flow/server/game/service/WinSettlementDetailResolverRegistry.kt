package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single

/** 以完整規則模組 ID 登記、bootstrap 後凍結的胡牌詳情解析器。 */
@Single
class WinSettlementDetailResolverRegistry {
    /** 以完整規則模組識別碼索引的詳情解析器。 */
    private val resolvers = linkedMapOf<String, WinSettlementDetailResolver>()

    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String> get() = resolvers.keys.toSet()

    /** 是否已禁止後續註冊。 */
    var isFrozen: Boolean = false
        private set

    /**
     * 登記指定規則模組的胡牌詳情解析器。
     *
     * @param definition 規則模組識別碼與胡牌詳情解析器的完整註冊定義。
     * @throws IllegalStateException registry 已凍結時拋出。
     * @throws IllegalArgumentException 識別碼格式無效或識別碼已被登記時拋出。
     */
    fun register(definition: WinSettlementDetailResolverDefinition) {
        check(!isFrozen) { "Win settlement detail resolver registry is frozen" }
        NamespacedId.requireValid(definition.ruleModuleId) { "Rule module ID must be namespaced: ${definition.ruleModuleId}" }
        require(resolvers.putIfAbsent(definition.ruleModuleId, definition.resolver) == null) { "Duplicate win settlement detail resolver: ${definition.ruleModuleId}" }
    }

    /**
     * 解析指定規則模組的一般胡牌詳情。
     *
     * @param ruleModuleId 欲查詢的規則模組識別碼。
     * @param state 胡牌成立時的權威桌況。
     * @param handValue 已完成計算的胡牌結果。
     * @return 規則解析器產生的詳情欄位；沒有對應解析器時為空清單。
     */
    fun resolve(ruleModuleId: String, state: TableState, handValue: HandValueResult): List<WinSettlementDetailField> = resolvers[ruleModuleId]?.resolve(state, handValue)
        ?: emptyList()

    /**
     * 解析指定規則模組的特殊胡牌等價結果詳情。
     *
     * @param ruleModuleId 欲查詢的規則模組識別碼。
     * @param state 特殊結果成立時的權威桌況。
     * @param outcome 欲解析的特殊回合結果。
     * @return 規則解析器產生的詳情欄位；沒有對應解析器或不支援此結果時為空清單。
     */
    fun resolveSpecialOutcome(ruleModuleId: String, state: TableState, outcome: ResolvedRoundOutcome): List<WinSettlementDetailField> = resolvers[ruleModuleId]?.resolveSpecialOutcome(state, outcome)
        ?: emptyList()

    /** 凍結 registry，禁止後續註冊新的解析器。 */
    fun freeze() {
        isFrozen = true
    }
}
