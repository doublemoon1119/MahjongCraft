package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.TaiwanTaiTranslationKeys

/** 台灣麻將預設台表、特殊花胡與流局說明的圖鑑來源。 */
class TaiwanCatalogueProvider : RuleCatalogueProvider {
    override val ruleModuleId: String = BuiltInRuleModuleIds.TAIWAN

    /** 建立一般說明使用的台灣麻將預設設定。 */
    override fun defaultRuleConfig(): MahjongRuleConfig = TaiwanRuleConfig()

    /**
     * 依台灣麻將設定建立圖鑑。
     *
     * @param config 房間或一般說明使用的規則設定。
     * @return 台灣麻將圖鑑；設定型別不符時為 null。
     */
    override fun catalogue(config: MahjongRuleConfig): RuleCatalogue? {
        if (config !is TaiwanRuleConfig) return null
        return RuleCatalogue(
            categories = TaiwanCatalogueCategory.entries.map { RuleCatalogueCategory(it.id, it.nameKey) },
            entries = TaiwanCatalogueTai.entries.map { definition -> definition.toCatalogueEntry(config) } + taiwanCatalogueSpecialEntries(config),
        )
    }
}

/** 將計台項目對照轉成圖鑑條目。 */
private fun TaiwanCatalogueTai.toCatalogueEntry(config: TaiwanRuleConfig): RuleCatalogueEntry = RuleCatalogueEntry(
    id = id,
    categoryId = category.id,
    nameTranslationKey = TaiwanTaiTranslationKeys.keyFor(type),
    descriptionTranslationKey = descriptionKey,
    labels = listOf(RuleCatalogueLabel(valueKey)),
    unavailableReasonTranslationKey = when {
        type in FLOWER_TAI && !config.useFlowerTiles -> TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE
        type == TaiwanTaiType.DeclaredReady && !config.generalReadyDeclaration -> TaiwanCatalogueKeys.GENERAL_READY_DECLARATION_UNAVAILABLE
        else -> null
    },
    examples = taiwanCatalogueExamples(type),
)

/** 只有使用花牌時才會成立的計台項目。 */
private val FLOWER_TAI: Set<TaiwanTaiType> = setOf(
    TaiwanTaiType.SeatFlower,
    TaiwanTaiType.FlowerSet,
    TaiwanTaiType.EightFlowers,
    TaiwanTaiType.SevenFlowers,
)

/** 建立一般流局與途中流局條目；停用的流局保留條目並標示原因。 */
private fun taiwanCatalogueSpecialEntries(config: TaiwanRuleConfig): List<RuleCatalogueEntry> = TaiwanCatalogueSpecial.entries.map { definition ->
    RuleCatalogueEntry(
        id = definition.id,
        categoryId = TaiwanCatalogueCategory.Draw.id,
        nameTranslationKey = definition.nameKey,
        descriptionTranslationKey = definition.descriptionKey,
        unavailableReasonTranslationKey = when (definition) {
            TaiwanCatalogueSpecial.NoSupplementalTile -> TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE.takeIf { !config.useFlowerTiles }
            TaiwanCatalogueSpecial.FourWinds -> TaiwanCatalogueKeys.FOUR_WINDS_UNAVAILABLE.takeIf { !config.fourWindsAbortiveDraw }
            TaiwanCatalogueSpecial.Normal, TaiwanCatalogueSpecial.FourKans -> null
        },
    )
}
