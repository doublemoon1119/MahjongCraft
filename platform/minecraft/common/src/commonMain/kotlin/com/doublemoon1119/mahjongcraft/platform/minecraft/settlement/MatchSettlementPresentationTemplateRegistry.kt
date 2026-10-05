package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/** 內建通用終局結算模板 key；規則沒有綁定模板時使用。 */
const val BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY: String = "${MinecraftModMetadata.MOD_ID}:generic_match_settlement"

/** 終局名次的宣告式揭曉方向。 */
enum class MatchSettlementRevealOrder {
    /** 從末位依序揭曉至第一名。 */
    LAST_TO_FIRST,

    /** 從第一名依序揭曉至末位。 */
    FIRST_TO_LAST,
}

/**
 * 第三方可調整的受控終局面板模板。
 *
 * @property key 完整 namespaced template key。
 * @property titleTranslationKey 面板標題翻譯 key。
 * @property backgroundArgb 背景 ARGB。
 * @property titleRgb 標題 RGB。
 * @property rankRgb 一般名次 RGB。
 * @property championRgb 第一名 RGB。
 * @property scoreRgb 最終點數 RGB。
 * @property revealOrder 逐名揭曉順序。
 * @property rowRevealIntervalTicks 兩列揭曉起點的間隔。
 * @property readingTicks 全部揭曉後的閱讀時間。
 * @property rowSoundId 一般名次揭曉聲音 resource ID。
 * @property championSoundId 第一名揭曉聲音 resource ID。
 */
data class MatchSettlementPresentationTemplate(
    val key: String,
    val titleTranslationKey: String,
    val backgroundArgb: Int = 0xB8000000.toInt(),
    val titleRgb: Int = 0xFFD37A,
    val rankRgb: Int = 0xFFD86A,
    val championRgb: Int = 0xFFD65A,
    val scoreRgb: Int = 0xFFF3C4,
    val revealOrder: MatchSettlementRevealOrder = MatchSettlementRevealOrder.LAST_TO_FIRST,
    val rowRevealIntervalTicks: Int = 12,
    val readingTicks: Int = 100,
    val rowSoundId: String = "minecraft:entity.experience_orb.pickup",
    val championSoundId: String = "minecraft:ui.toast.challenge_complete",
) {
    init {
        NamespacedId.requireValid(key) { "Match settlement template key must be namespaced: $key" }
        require(titleTranslationKey.isNotBlank())
        require(rowRevealIntervalTicks in 1..100 && readingTicks in 20..1200)
        MinecraftResourceIds.requireValid(rowSoundId) { "Invalid match settlement row sound ID: $rowSoundId" }
        MinecraftResourceIds.requireValid(championSoundId) { "Invalid match settlement champion sound ID: $championSoundId" }
    }
}

/** 終局面板模板的凍結式 registry。 */
interface MatchSettlementPresentationTemplateRegistry {
    /** 目前已登記模板 key 的快照。 */
    val registrationKeys: Set<String>

    /** 登記一個完整模板；重複 key 視為錯誤。 */
    fun register(template: MatchSettlementPresentationTemplate)

    /**
     * 指定規則模組使用的模板。
     *
     * @param ruleModuleId 規則模組 ID。
     * @param templateKey 已登記或之後會登記的模板 key。
     */
    fun bindRuleTemplate(ruleModuleId: String, templateKey: String)

    /** 查詢指定 key 的模板。 */
    fun find(key: String): MatchSettlementPresentationTemplate?

    /**
     * 取得規則模組使用的模板；沒有綁定或綁定的模板不存在時使用 [BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY]。
     *
     * @param ruleModuleId 規則模組 ID。
     * @return 模板；連內建模板都不存在時為 null。
     */
    fun findForRule(ruleModuleId: String): MatchSettlementPresentationTemplate?

    /** 凍結 registry，之後不可再登記。 */
    fun freeze()
}

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

    override fun findForRule(ruleModuleId: String): MatchSettlementPresentationTemplate? = ruleTemplateKeys[ruleModuleId]?.let(templates::get)
        ?: templates[BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY]

    override fun freeze() {
        frozen = true
    }
}

/** 登記 MahjongCraft 的通用終局模板。 */
fun MatchSettlementPresentationTemplateRegistry.registerBuiltInMatchSettlementTemplate() {
    register(
        MatchSettlementPresentationTemplate(
            key = BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY,
            titleTranslationKey = MinecraftMessageKeys.MATCH_SETTLEMENT_TITLE,
        ),
    )
}
