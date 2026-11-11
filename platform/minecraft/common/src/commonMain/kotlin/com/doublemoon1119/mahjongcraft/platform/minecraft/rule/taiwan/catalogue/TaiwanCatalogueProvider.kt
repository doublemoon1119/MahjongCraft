package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider

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
            entries = TaiwanTaiType.entries.map { type -> type.toCatalogueEntry(config) } + drawEntries(config),
        )
    }
}

/** 將計台項目轉成圖鑑條目。 */
private fun TaiwanTaiType.toCatalogueEntry(config: TaiwanRuleConfig): RuleCatalogueEntry {
    val slug = id.substringAfterLast('/')
    val flowerEntry = this == TaiwanTaiType.SEAT_FLOWER ||
        this == TaiwanTaiType.FLOWER_SET ||
        this == TaiwanTaiType.EIGHT_FLOWERS ||
        this == TaiwanTaiType.SEVEN_FLOWERS
    return RuleCatalogueEntry(
        id = id,
        categoryId = taiCatalogueCategory.id,
        nameTranslationKey = "mahjongcraft.taiwan.tai.$slug",
        descriptionTranslationKey = TaiwanCatalogueKeys.PREFIX + "entry.$slug.description",
        labels = listOf(RuleCatalogueLabel(TaiwanCatalogueKeys.PREFIX + "label.tai_$tai")),
        unavailableReasonTranslationKey = TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE.takeIf { flowerEntry && !config.useFlowerTiles },
        examples = taiwanCatalogueExamples(this),
    )
}

/** 每個計台項目明確對應的主要分類；新增計台項目時必須一併決定圖鑑位置。 */
private val TaiwanTaiType.taiCatalogueCategory: TaiwanCatalogueCategory
    get() = when (this) {
        TaiwanTaiType.CONCEALED_HAND,
        TaiwanTaiType.SELF_DRAW,
        TaiwanTaiType.FULLY_CONCEALED_HAND,
        TaiwanTaiType.SEAT_WIND,
        TaiwanTaiType.ROUND_WIND,
        TaiwanTaiType.DRAGON,
        TaiwanTaiType.SEAT_FLOWER,
        TaiwanTaiType.FLOWER_SET,
        TaiwanTaiType.SINGLE_WAIT,
        TaiwanTaiType.ROBBING_KONG,
        TaiwanTaiType.REPLACEMENT_WIN,
        TaiwanTaiType.LAST_DRAW,
        TaiwanTaiType.DECLARED_READY,
        -> TaiwanCatalogueCategory.TAI_1

        TaiwanTaiType.ALL_SEQUENCES,
        TaiwanTaiType.THREE_CONCEALED_TRIPLETS,
        TaiwanTaiType.ALL_EXPOSED,
        -> TaiwanCatalogueCategory.TAI_2

        TaiwanTaiType.ALL_TRIPLETS,
        TaiwanTaiType.LITTLE_DRAGONS,
        TaiwanTaiType.HALF_FLUSH,
        -> TaiwanCatalogueCategory.TAI_4

        TaiwanTaiType.FOUR_CONCEALED_TRIPLETS -> TaiwanCatalogueCategory.TAI_5

        TaiwanTaiType.FIVE_CONCEALED_TRIPLETS,
        TaiwanTaiType.BIG_DRAGONS,
        TaiwanTaiType.FULL_FLUSH,
        TaiwanTaiType.LITTLE_WINDS,
        TaiwanTaiType.EARTHLY_READY,
        TaiwanTaiType.EIGHT_FLOWERS,
        TaiwanTaiType.SEVEN_FLOWERS,
        -> TaiwanCatalogueCategory.TAI_8

        TaiwanTaiType.BIG_WINDS,
        TaiwanTaiType.ALL_HONORS,
        TaiwanTaiType.HEAVENLY_READY,
        TaiwanTaiType.HUMAN_WIN,
        TaiwanTaiType.EARTHLY_WIN,
        -> TaiwanCatalogueCategory.TAI_16

        TaiwanTaiType.HEAVENLY_WIN -> TaiwanCatalogueCategory.TAI_24
    }

/** 建立一般流局與途中流局條目。 */
private fun drawEntries(config: TaiwanRuleConfig): List<RuleCatalogueEntry> = listOf(
    drawEntry(TaiwanExhaustiveDrawReason.Normal.id, "normal"),
    drawEntry(TaiwanExhaustiveDrawReason.NoSupplementalTile.id, "no_supplemental_tile", TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE.takeIf { !config.useFlowerTiles }),
    drawEntry(TaiwanExhaustiveDrawReason.FourKans.id, "four_kans"),
    drawEntry(TaiwanExhaustiveDrawReason.FourWinds.id, "four_winds", TaiwanCatalogueKeys.FOUR_WINDS_UNAVAILABLE.takeIf { !config.fourWindsAbortiveDraw }),
)

/** 建立一筆流局圖鑑條目。 */
private fun drawEntry(id: String, slug: String, unavailableReason: String? = null): RuleCatalogueEntry = RuleCatalogueEntry(
    id = id,
    categoryId = TaiwanCatalogueCategory.DRAW.id,
    nameTranslationKey = TaiwanCatalogueKeys.PREFIX + "draw.$slug.name",
    descriptionTranslationKey = TaiwanCatalogueKeys.PREFIX + "draw.$slug.description",
    unavailableReasonTranslationKey = unavailableReason,
)
