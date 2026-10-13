package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/** 記憶體 registry 實作。 */
@Single(binds = [WinSettlementPresentationTemplateRegistry::class])
class WinSettlementPresentationTemplateRegistryImpl : WinSettlementPresentationTemplateRegistry {
    /** 依完整模板 key 保存的結算模板。 */
    private val templates = linkedMapOf<String, WinSettlementPresentationTemplate>()

    /** 欄位 ID 對應的欄位值 provider。 */
    private val providers = linkedMapOf<PresentationFieldId, WinSettlementPresentationFieldProvider>()

    /** 規則模組 ID 對應的結算模板 key。 */
    private val ruleTemplateKeys = linkedMapOf<String, String>()

    /** 詳情欄位 ID 對應的文字格式器。 */
    private val detailTextFormatters = linkedMapOf<String, WinSettlementDetailTextFormatter>()

    override var isFrozen: Boolean = false
        private set

    override val templateKeys: Set<String> get() = templates.keys.toSet()

    override val registrationKeys: Set<String>
        get() = templates.keys.mapTo(mutableSetOf()) { "template:$it" } +
            providers.keys.map { "field:$it" } +
            ruleTemplateKeys.keys.map { "rule:$it" } +
            detailTextFormatters.keys.map { "detail:$it" }

    override fun register(template: WinSettlementPresentationTemplate) {
        check(!isFrozen) { "Win settlement template registry is frozen" }
        require(templates.putIfAbsent(template.key, template.snapshot()) == null) { "Duplicate win settlement template: ${template.key}" }
    }

    override fun register(definition: WinSettlementPresentationFieldProviderDefinition) {
        val fieldId = definition.fieldId
        val provider = definition.provider
        check(!isFrozen) { "Win settlement template registry is frozen" }
        require(providers.putIfAbsent(fieldId, provider) == null) { "Duplicate win settlement field: $fieldId" }
    }

    override fun register(definition: WinSettlementRuleTemplateBindingDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val templateKey = definition.templateKey
        check(!isFrozen) { "Win settlement template registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        NamespacedId.requireValid(templateKey) { "Win settlement template key must be namespaced: $templateKey" }
        require(ruleTemplateKeys.putIfAbsent(ruleModuleId, templateKey) == null) { "Duplicate win settlement rule template: $ruleModuleId" }
    }

    override fun register(definition: WinSettlementDetailTextFormatterDefinition) {
        val fieldId = definition.fieldId
        val formatter = definition.formatter
        check(!isFrozen) { "Win settlement template registry is frozen" }
        NamespacedId.requireValid(fieldId) { "Win settlement detail field ID must be namespaced: $fieldId" }
        require(detailTextFormatters.putIfAbsent(fieldId, formatter) == null) { "Duplicate win settlement detail formatter: $fieldId" }
    }

    override fun findTemplate(key: String): WinSettlementPresentationTemplate? = templates[key]?.snapshot()

    override fun findTemplateForRule(ruleModuleId: String): WinSettlementPresentationTemplate? = (ruleTemplateKeys[ruleModuleId]?.let(templates::get) ?: templates[BuiltInWinSettlementTemplateKeys.GENERIC])?.snapshot()

    override fun findDetailTextFormatter(fieldId: String): WinSettlementDetailTextFormatter? = detailTextFormatters[fieldId]

    override fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider? = providers[fieldId]

    override fun freeze() {
        isFrozen = true
    }
}
