package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 宣告式胡牌結算模板與欄位 provider 的凍結式註冊中心。 */
interface WinSettlementPresentationTemplateRegistry {
    val isFrozen: Boolean
    val templateKeys: Set<String>
    fun registerTemplate(template: WinSettlementPresentationTemplate)
    fun registerFieldProvider(fieldId: PresentationFieldId, provider: WinSettlementPresentationFieldProvider)
    fun findTemplate(key: String): WinSettlementPresentationTemplate?
    fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider?
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
    override var isFrozen: Boolean = false
        private set
    override val templateKeys: Set<String> get() = templates.keys.toSet()

    override fun registerTemplate(template: WinSettlementPresentationTemplate) {
        check(!isFrozen) { "Win settlement template registry is frozen" }
        require(templates.putIfAbsent(template.key, template) == null) { "Duplicate win settlement template: ${template.key}" }
    }

    override fun registerFieldProvider(fieldId: PresentationFieldId, provider: WinSettlementPresentationFieldProvider) {
        check(!isFrozen) { "Win settlement template registry is frozen" }
        require(providers.putIfAbsent(fieldId, provider) == null) { "Duplicate win settlement field: $fieldId" }
    }

    override fun findTemplate(key: String): WinSettlementPresentationTemplate? = templates[key]
    override fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider? = providers[fieldId]
    override fun freeze() {
        isFrozen = true
    }
}
