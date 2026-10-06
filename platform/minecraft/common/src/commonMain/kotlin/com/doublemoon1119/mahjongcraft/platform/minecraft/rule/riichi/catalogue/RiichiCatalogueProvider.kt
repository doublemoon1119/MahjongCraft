package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiYakuTranslationKeys

/** 日麻說明目錄來源，只組成說明與範例，不執行和牌或計分判定。 */
class RiichiCatalogueProvider : RuleCatalogueProvider {
    /** 日麻規則模組識別碼。 */
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

    /** 建立一般說明用配置，不表示任一場對局的實際設定。 */
    override fun defaultRuleConfig(): MahjongRuleConfig = RiichiRuleConfig()

    /**
     * 依日麻配置調整說明條件；不支援其他規則配置。
     *
     * @param config 已知配置或一般說明配置。
     * @return 日麻目錄，或配置型別不匹配時的 null。
     */
    override fun catalogue(config: MahjongRuleConfig): RuleCatalogue? {
        if (config !is RiichiRuleConfig) return null
        return RuleCatalogue(
            categories = RiichiCatalogueCategory.entries.map { RuleCatalogueCategory(it.id, it.nameKey) },
            entries = RiichiCatalogueYaku.entries.map { definition ->
                RuleCatalogueEntry(
                    id = definition.id,
                    categoryId = definition.category.id,
                    nameTranslationKey = if (definition.type == YakuType.Dragon) RiichiCatalogueKeys.DRAGON_NAME else RiichiYakuTranslationKeys.keyFor(definition.type),
                    descriptionTranslationKey = definition.descriptionKey,
                    labelTranslationKeys = buildList {
                        add(definition.valueKey)
                        definition.conditionKey?.let(::add)
                        if (definition.type == YakuType.Tanyao && !config.allowOpenTanyao) add(RiichiCatalogueKeys.CLOSED_ONLY)
                    },
                    unavailableReasonTranslationKey = RiichiCatalogueKeys.RED_DORA_UNAVAILABLE.takeIf { definition.type == YakuType.AkaDora && config.redDoraCount == 0 },
                    examples = riichiCatalogueExamples(definition.type),
                )
            } + riichiCatalogueSpecialEntries(),
        )
    }
}

/** 建立不經一般手牌役種判定的特殊結算與途中流局說明。 */
private fun riichiCatalogueSpecialEntries(): List<RuleCatalogueEntry> = RiichiCatalogueSpecial.entries.map { definition ->
    val isMangan = definition == RiichiCatalogueSpecial.NAGASHI_MANGAN
    RuleCatalogueEntry(
        id = definition.id,
        categoryId = if (isMangan) RiichiCatalogueCategory.MANGAN.id else RiichiCatalogueCategory.ABORTIVE_DRAW.id,
        nameTranslationKey = definition.nameKey,
        descriptionTranslationKey = definition.descriptionKey,
        labelTranslationKeys = if (isMangan) listOf(RiichiCatalogueKeys.MANGAN) else emptyList(),
    )
}
