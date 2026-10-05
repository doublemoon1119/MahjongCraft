package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 宣告式胡牌結算模板、欄位 provider、規則模板綁定與詳情格式化器的凍結式註冊中心。 */
interface WinSettlementPresentationTemplateRegistry {
    val isFrozen: Boolean
    val templateKeys: Set<String>

    /** 目前已登記模板、欄位 provider、規則綁定與詳情格式化器的穩定 key 快照。 */
    val registrationKeys: Set<String>
    fun registerTemplate(template: WinSettlementPresentationTemplate)
    fun registerFieldProvider(fieldId: PresentationFieldId, provider: WinSettlementPresentationFieldProvider)

    /**
     * 指定規則模組使用的模板。
     *
     * @param ruleModuleId 規則模組 ID。
     * @param templateKey 已登記或之後會登記的模板 key。
     */
    fun bindRuleTemplate(ruleModuleId: String, templateKey: String)

    /**
     * 登記胡牌詳情欄位的格式化器。
     *
     * @param fieldId 規則定義的胡牌詳情欄位 ID。
     * @param formatter 把欄位語意值轉成顯示文字的格式化器。
     */
    fun registerDetailTextFormatter(fieldId: String, formatter: WinSettlementDetailTextFormatter)
    fun findTemplate(key: String): WinSettlementPresentationTemplate?

    /**
     * 取得規則模組使用的模板；沒有綁定或綁定的模板不存在時使用通用模板。
     *
     * @param ruleModuleId 規則模組 ID。
     * @return 模板；連通用模板都不存在時為 null。
     */
    fun findTemplateForRule(ruleModuleId: String): WinSettlementPresentationTemplate?
    fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider?

    /**
     * 取得胡牌詳情欄位的格式化器。
     *
     * @param fieldId 胡牌詳情欄位 ID。
     * @return 已登記的格式化器；沒有登記時為 null。
     */
    fun findDetailTextFormatter(fieldId: String): WinSettlementDetailTextFormatter?
    fun freeze()
}

/** 欄位 provider 只能讀取不可變快照，不能接觸或修改權威桌況。 */
fun interface WinSettlementPresentationFieldProvider {
    fun provide(snapshot: WinSettlementPresentationFieldSnapshot): PresentationValue?
}

/** 提供給欄位 provider 的規則中立、已序列化快照。 */
data class WinSettlementPresentationFieldSnapshot(
    val outcomeId: String,
    val isTsumo: Boolean,
    val winnerId: String,
    val winnerDisplayName: String,
    val winnerIsAi: Boolean,
    val responsiblePlayerId: String?,
    val responsiblePlayerDisplayName: String?,
    val responsiblePlayerIsAi: Boolean?,
    val totalScore: Int,
    val tileAssetKeys: List<String>,
    val tileAssetGroups: List<List<String>>,
    val winningTileAssetKey: String?,
    val extensionFields: List<ExtensionPresentationField> = emptyList(),
    val initialFadeTicks: Int = 16,
    val entryStaggerTicks: Int = 8,
    val scoreRevealTicks: Int = 18,
    val scoreRevealDelayTicks: Int = 0,
) {
    /** 依完整 ID 取得強型別 extension 欄位；重複 ID 由建構端拒絕。 */
    fun extensionField(id: PresentationFieldId): PresentationValue? = extensionFields.firstOrNull { it.id == id }?.value

    init {
        require(extensionFields.map(ExtensionPresentationField::id).distinct().size == extensionFields.size)
    }
}

/** 不使用字串 Map 的單一 extension 顯示欄位。 */
data class ExtensionPresentationField(val id: PresentationFieldId, val value: PresentationValue)

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
    override fun findTemplateForRule(ruleModuleId: String): WinSettlementPresentationTemplate? = ruleTemplateKeys[ruleModuleId]?.let(templates::get)
        ?: templates[BuiltInWinSettlementTemplateKeys.GENERIC]
    override fun findDetailTextFormatter(fieldId: String): WinSettlementDetailTextFormatter? = detailTextFormatters[fieldId]
    override fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider? = providers[fieldId]
    override fun freeze() {
        isFrozen = true
    }
}
