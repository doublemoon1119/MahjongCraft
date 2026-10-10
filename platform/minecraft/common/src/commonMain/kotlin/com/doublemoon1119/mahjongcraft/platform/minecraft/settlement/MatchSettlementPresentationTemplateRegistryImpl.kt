package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [MatchSettlementPresentationTemplateRegistry] 的記憶體實作。 */
class MatchSettlementPresentationTemplateRegistryImpl : MatchSettlementPresentationTemplateRegistry {
    private val templates = linkedMapOf<String, MatchSettlementPresentationTemplate>()
    private val ruleTemplateKeys = linkedMapOf<String, String>()

    override val registrationKeys: Set<String> get() = templates.keys + ruleTemplateKeys.keys.map { "rule:$it" }
    private var frozen = false

    override fun register(template: MatchSettlementPresentationTemplate) {
        check(!frozen) { "Match settlement template registry is frozen" }
        require(templates.putIfAbsent(template.key, template) == null) { "Duplicate match settlement template: ${template.key}" }
    }

    override fun bindRuleTemplate(ruleModuleId: String, templateKey: String) {
        check(!frozen) { "Match settlement template registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        NamespacedId.requireValid(templateKey) { "Match settlement template key must be namespaced: $templateKey" }
        require(ruleTemplateKeys.putIfAbsent(ruleModuleId, templateKey) == null) { "Duplicate match settlement rule template: $ruleModuleId" }
    }

    override fun find(key: String): MatchSettlementPresentationTemplate? = templates[key]

    override fun findForRule(ruleModuleId: String): MatchSettlementPresentationTemplate? = ruleTemplateKeys[ruleModuleId]?.let(templates::get) ?: templates[BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY]

    override fun freeze() {
        frozen = true
    }
}
