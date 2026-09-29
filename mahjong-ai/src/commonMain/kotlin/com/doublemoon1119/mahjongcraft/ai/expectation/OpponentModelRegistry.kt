package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 依規則模組 ID 管理對手模型的可凍結登記表。
 *
 * 沒有登記的規則使用 [NeutralOpponentModel]。內建與第三方規則以同一個 [register] 登記，不具特權。
 */
class OpponentModelRegistry {
    /** 依規則模組 ID 索引的對手模型建立方式。 */
    private val factoriesByRuleModuleId = linkedMapOf<String, (MahjongRuleModule<*>, ReadingDepth) -> OpponentModel>()

    /** 是否已禁止後續登記。 */
    private var frozen = false

    /** 目前已登記的規則模組 ID，依登記順序排列。 */
    val registrationKeys: Set<String> get() = factoriesByRuleModuleId.keys.toSet()

    /**
     * 登記 [ruleModuleId] 使用的對手模型；[factory] 每次決策時以該局的規則模組與決策者的讀牌深度呼叫一次。
     *
     * 凍結後或重複登記同一個規則時拋出例外。
     */
    fun register(ruleModuleId: String, factory: (MahjongRuleModule<*>, ReadingDepth) -> OpponentModel) {
        check(!frozen) { "Opponent model registry is frozen" }
        require(ruleModuleId !in factoriesByRuleModuleId) { "Opponent model already registered for $ruleModuleId" }
        factoriesByRuleModuleId[ruleModuleId] = factory
    }

    /** 禁止後續登記。 */
    fun freeze() {
        frozen = true
    }

    /** 建立 [module] 以 [depth] 讀牌的對手模型；沒有登記時為 [NeutralOpponentModel]。 */
    fun create(module: MahjongRuleModule<*>, depth: ReadingDepth): OpponentModel = factoriesByRuleModuleId[module.id]?.invoke(module, depth) ?: NeutralOpponentModel(depth)
}
