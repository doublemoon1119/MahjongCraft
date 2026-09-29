package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 依規則模組 ID 管理對手模型的可凍結登記表。
 *
 * 沒有登記的規則使用 [NeutralOpponentModel]。內建與第三方規則以同一個 [register] 登記，不具特權。
 * 以進階深度建立時，登記的模型外面一律再套用所有規則共用的讀牌（[ReadingOpponentModel]），
 * 規則自己的模型只需要負責規則專屬的估計。
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

    /**
     * 建立 [module] 以 [depth] 讀牌的對手模型；沒有登記時為 [NeutralOpponentModel]。
     *
     * 進階深度時外面再套用共用的讀牌。沒有登記的規則，讀牌倍率限制在 [NEUTRAL_READING_FACTOR_RANGE]：
     * 不知道規則時無法確認推測的準確度，因此只做溫和的調整；規則確定不能榮和的牌不受這個限制。
     */
    fun create(module: MahjongRuleModule<*>, depth: ReadingDepth): OpponentModel {
        val registered = factoriesByRuleModuleId[module.id]?.invoke(module, depth)
        val model = registered ?: NeutralOpponentModel(depth)
        if (depth == ReadingDepth.BASIC) return model
        val interpretation = module.createTileInterpretationPolicy()
        val rules = module.createPositionRules()
        return ReadingOpponentModel(
            delegate = model,
            rules = rules,
            interpretation = interpretation,
            reading = OpponentReading(interpretation = interpretation, rules = rules),
            factorRange = if (registered == null) NEUTRAL_READING_FACTOR_RANGE else UNLIMITED_READING_FACTOR_RANGE,
        )
    }

    /** [OpponentModelRegistry] 的常數。 */
    internal companion object {
        /** 沒有登記專屬模型的規則，讀牌倍率允許的範圍。 */
        val NEUTRAL_READING_FACTOR_RANGE: ClosedFloatingPointRange<Double> = 0.5..1.5

        /** 有登記專屬模型的規則，讀牌倍率不另外限制。 */
        val UNLIMITED_READING_FACTOR_RANGE: ClosedFloatingPointRange<Double> = 0.0..Double.MAX_VALUE
    }
}
