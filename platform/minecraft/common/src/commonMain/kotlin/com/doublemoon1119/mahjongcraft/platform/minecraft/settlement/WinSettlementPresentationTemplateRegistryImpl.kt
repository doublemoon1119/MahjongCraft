package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 記憶體 registry 實作。 */
class WinSettlementPresentationTemplateRegistryImpl : WinSettlementPresentationTemplateRegistry {
    private val templates = linkedMapOf<String, WinSettlementPresentationTemplate>()
    private val providers = linkedMapOf<PresentationFieldId, WinSettlementPresentationFieldProvider>()
    private val ruleTemplateKeys = linkedMapOf<String, String>()
    private val detailTextFormatters = linkedMapOf<String, WinSettlementDetailTextFormatter>()
    override var isFrozen: Boolean = false
        private set
    override val templateKeys: Set<String> get() = templates.keys.toSet()
    override val registrationKeys: Set<String>
        get() = templates.keys.mapTo(mutableSetOf()) { "template:$it" } +
            providers.keys.map { "field:$it" } +
            ruleTemplateKeys.keys.map { "rule:$it" } +
            detailTextFormatters.keys.map { "detail:$it" }

    override fun registerTemplate(template: WinSettlementPresentationTemplate) {
        check(!isFrozen) { "Win settlement template registry is frozen" }
        require(templates.putIfAbsent(template.key, template) == null) { "Duplicate win settlement template: ${template.key}" }
    }

    override fun registerFieldProvider(fieldId: PresentationFieldId, provider: WinSettlementPresentationFieldProvider) {
        check(!isFrozen) { "Win settlement template registry is frozen" }
        require(providers.putIfAbsent(fieldId, provider) == null) { "Duplicate win settlement field: $fieldId" }
    }

    override fun bindRuleTemplate(ruleModuleId: String, templateKey: String) {
        check(!isFrozen) { "Win settlement template registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        NamespacedId.requireValid(templateKey) { "Win settlement template key must be namespaced: $templateKey" }
        require(ruleTemplateKeys.putIfAbsent(ruleModuleId, templateKey) == null) { "Duplicate win settlement rule template: $ruleModuleId" }
    }

    override fun registerDetailTextFormatter(fieldId: String, formatter: WinSettlementDetailTextFormatter) {
        check(!isFrozen) { "Win settlement template registry is frozen" }
        NamespacedId.requireValid(fieldId) { "Win settlement detail field ID must be namespaced: $fieldId" }
        require(detailTextFormatters.putIfAbsent(fieldId, formatter) == null) { "Duplicate win settlement detail formatter: $fieldId" }
    }

    override fun findTemplate(key: String): WinSettlementPresentationTemplate? = templates[key]
    override fun findTemplateForRule(ruleModuleId: String): WinSettlementPresentationTemplate? = ruleTemplateKeys[ruleModuleId]?.let(templates::get) ?: templates[BuiltInWinSettlementTemplateKeys.GENERIC]
    override fun findDetailTextFormatter(fieldId: String): WinSettlementDetailTextFormatter? = detailTextFormatters[fieldId]
    override fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider? = providers[fieldId]
    override fun freeze() {
        isFrozen = true
    }
}
