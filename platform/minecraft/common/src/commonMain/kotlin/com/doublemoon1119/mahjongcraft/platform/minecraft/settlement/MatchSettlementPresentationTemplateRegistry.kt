package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

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
        require(key.substringBefore(':', "").isNotBlank() && key.substringAfter(':', "").isNotBlank())
        require(titleTranslationKey.isNotBlank())
        require(rowRevealIntervalTicks in 1..100 && readingTicks in 20..1200)
        require(rowSoundId.isNamespaced() && championSoundId.isNamespaced())
    }

    /** 判斷 resource ID 是否包含非空 namespace 與 path。 */
    private fun String.isNamespaced(): Boolean = substringBefore(':', "").isNotBlank() && substringAfter(':', "").isNotBlank()
}

/** 終局面板模板的凍結式 registry。 */
interface MatchSettlementPresentationTemplateRegistry {
    /** 登記一個完整模板；重複 key 視為錯誤。 */
    fun register(template: MatchSettlementPresentationTemplate)

    /** 查詢指定 key 的模板。 */
    fun find(key: String): MatchSettlementPresentationTemplate?

    /** 凍結 registry，之後不可再登記。 */
    fun freeze()
}

/** [MatchSettlementPresentationTemplateRegistry] 的記憶體實作。 */
class MatchSettlementPresentationTemplateRegistryImpl : MatchSettlementPresentationTemplateRegistry {
    private val templates = linkedMapOf<String, MatchSettlementPresentationTemplate>()
    private var frozen = false

    override fun register(template: MatchSettlementPresentationTemplate) {
        check(!frozen) { "Match settlement template registry is frozen" }
        require(templates.putIfAbsent(template.key, template) == null) { "Duplicate match settlement template: ${template.key}" }
    }

    override fun find(key: String): MatchSettlementPresentationTemplate? = templates[key]

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
