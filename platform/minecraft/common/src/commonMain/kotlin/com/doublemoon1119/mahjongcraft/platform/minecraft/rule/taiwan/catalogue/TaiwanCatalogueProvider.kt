package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
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
            entries = TaiwanTaiType.entries.map { type -> type.toCatalogueEntry(config) } + drawEntries(config),
        )
    }
}

/** 將計台項目轉成圖鑑條目。 */
private fun TaiwanTaiType.toCatalogueEntry(config: TaiwanRuleConfig): RuleCatalogueEntry {
    val id = TaiwanWinSettlementIds.tai(this)
    val slug = id.substringAfterLast('/')
    val flowerEntry = this == TaiwanTaiType.SeatFlower ||
        this == TaiwanTaiType.FlowerSet ||
        this == TaiwanTaiType.EightFlowers ||
        this == TaiwanTaiType.SevenFlowers
    return RuleCatalogueEntry(
        id = id,
        categoryId = taiCatalogueCategory.id,
        nameTranslationKey = TaiwanTaiTranslationKeys.keyFor(this),
        descriptionTranslationKey = TaiwanCatalogueKeys.PREFIX + "entry.$slug.description",
        labels = listOf(RuleCatalogueLabel(TaiwanCatalogueKeys.PREFIX + "label.tai_${taiCatalogueCategory.tai}")),
        unavailableReasonTranslationKey = TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE.takeIf { flowerEntry && !config.useFlowerTiles },
        examples = taiwanCatalogueExamples(this),
    )
}

/** 每個計台項目明確對應的主要分類；新增計台項目時必須一併決定圖鑑位置。 */
private val TaiwanTaiType.taiCatalogueCategory: TaiwanCatalogueCategory
    get() = when (this) {
        TaiwanTaiType.ConcealedHand,
        TaiwanTaiType.SelfDraw,
        TaiwanTaiType.FullyConcealedHand,
        TaiwanTaiType.SeatWind,
        TaiwanTaiType.RoundWind,
        TaiwanTaiType.Dragon,
        TaiwanTaiType.SeatFlower,
        TaiwanTaiType.FlowerSet,
        TaiwanTaiType.SingleWait,
        TaiwanTaiType.RobbingKong,
        TaiwanTaiType.ReplacementWin,
        TaiwanTaiType.LastDraw,
        TaiwanTaiType.DeclaredReady,
        -> TaiwanCatalogueCategory.TAI_1

        TaiwanTaiType.AllSequences,
        TaiwanTaiType.ThreeConcealedTriplets,
        TaiwanTaiType.AllExposed,
        -> TaiwanCatalogueCategory.TAI_2

        TaiwanTaiType.AllTriplets,
        TaiwanTaiType.LittleDragons,
        TaiwanTaiType.HalfFlush,
        -> TaiwanCatalogueCategory.TAI_4

        TaiwanTaiType.FourConcealedTriplets -> TaiwanCatalogueCategory.TAI_5

        TaiwanTaiType.FiveConcealedTriplets,
        TaiwanTaiType.BigDragons,
        TaiwanTaiType.FullFlush,
        TaiwanTaiType.LittleWinds,
        TaiwanTaiType.EarthlyReady,
        TaiwanTaiType.EightFlowers,
        TaiwanTaiType.SevenFlowers,
        -> TaiwanCatalogueCategory.TAI_8

        TaiwanTaiType.BigWinds,
        TaiwanTaiType.AllHonors,
        TaiwanTaiType.HeavenlyReady,
        TaiwanTaiType.HumanWin,
        TaiwanTaiType.EarthlyWin,
        -> TaiwanCatalogueCategory.TAI_16

        TaiwanTaiType.HeavenlyWin -> TaiwanCatalogueCategory.TAI_24
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
