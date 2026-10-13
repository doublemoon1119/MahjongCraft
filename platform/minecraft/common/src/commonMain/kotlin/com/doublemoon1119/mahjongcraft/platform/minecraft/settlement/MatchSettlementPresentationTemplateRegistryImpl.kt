package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/** [MatchSettlementPresentationTemplateRegistry] 的記憶體實作。 */
@Single(binds = [MatchSettlementPresentationTemplateRegistry::class])
class MatchSettlementPresentationTemplateRegistryImpl : MatchSettlementPresentationTemplateRegistry {
    /** 依完整模板 key 保存的模板。 */
    private val templates = linkedMapOf<String, MatchSettlementPresentationTemplate>()

    /** 規則模組 ID 對應的終局模板 key。 */
    private val ruleTemplateKeys = linkedMapOf<String, String>()

    override val registrationKeys: Set<String> get() = templates.keys + ruleTemplateKeys.keys.map { "rule:$it" }

    /** registry 是否已停止接受註冊。 */
    private var frozen = false

    override fun register(template: MatchSettlementPresentationTemplate) {
        check(!frozen) { "Match settlement template registry is frozen" }
        require(templates.putIfAbsent(template.key, template.snapshot()) == null) { "Duplicate match settlement template: ${template.key}" }
    }

    override fun register(definition: MatchSettlementRuleTemplateBindingDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val templateKey = definition.templateKey
        check(!frozen) { "Match settlement template registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        NamespacedId.requireValid(templateKey) { "Match settlement template key must be namespaced: $templateKey" }
        require(ruleTemplateKeys.putIfAbsent(ruleModuleId, templateKey) == null) { "Duplicate match settlement rule template: $ruleModuleId" }
    }

    override fun find(key: String): MatchSettlementPresentationTemplate? = templates[key]?.snapshot()

    override fun findForRule(ruleModuleId: String): MatchSettlementPresentationTemplate? = (ruleTemplateKeys[ruleModuleId]?.let(templates::get) ?: templates[BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY])?.snapshot()

    override fun freeze() {
        frozen = true
    }
}
