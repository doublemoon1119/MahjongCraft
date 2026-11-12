package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 台灣麻將圖鑑依台數劃分的主要分類。
 *
 * @property id namespaced 分類識別碼。
 * @property nameKey 分類名稱的翻譯鍵。
 */
internal enum class TaiwanCatalogueCategory(
    val id: String,
    val nameKey: String,
) {
    /** 一台項目。 */
    Tai1(MahjongCraftMetadata.id("catalogue/taiwan/category/tai_1"), "mahjongcraft.taiwan.rule_catalogue.category.tai_1"),

    /** 兩台項目。 */
    Tai2(MahjongCraftMetadata.id("catalogue/taiwan/category/tai_2"), "mahjongcraft.taiwan.rule_catalogue.category.tai_2"),

    /** 四台項目。 */
    Tai4(MahjongCraftMetadata.id("catalogue/taiwan/category/tai_4"), "mahjongcraft.taiwan.rule_catalogue.category.tai_4"),

    /** 五台項目。 */
    Tai5(MahjongCraftMetadata.id("catalogue/taiwan/category/tai_5"), "mahjongcraft.taiwan.rule_catalogue.category.tai_5"),

    /** 八台項目。 */
    Tai8(MahjongCraftMetadata.id("catalogue/taiwan/category/tai_8"), "mahjongcraft.taiwan.rule_catalogue.category.tai_8"),

    /** 十六台項目。 */
    Tai16(MahjongCraftMetadata.id("catalogue/taiwan/category/tai_16"), "mahjongcraft.taiwan.rule_catalogue.category.tai_16"),

    /** 二十四台項目。 */
    Tai24(MahjongCraftMetadata.id("catalogue/taiwan/category/tai_24"), "mahjongcraft.taiwan.rule_catalogue.category.tai_24"),

    /** 一般與途中流局項目。 */
    Draw(MahjongCraftMetadata.id("catalogue/taiwan/category/draw"), "mahjongcraft.taiwan.rule_catalogue.category.draw"),
}

/** 台灣麻將圖鑑的共用翻譯鍵，不影響結算名稱契約。 */
internal object TaiwanCatalogueKeys {
    /** 1 台呈現標籤。 */
    const val TAI_1: String = "mahjongcraft.taiwan.rule_catalogue.label.tai_1"

    /** 2 台呈現標籤。 */
    const val TAI_2: String = "mahjongcraft.taiwan.rule_catalogue.label.tai_2"

    /** 4 台呈現標籤。 */
    const val TAI_4: String = "mahjongcraft.taiwan.rule_catalogue.label.tai_4"

    /** 5 台呈現標籤。 */
    const val TAI_5: String = "mahjongcraft.taiwan.rule_catalogue.label.tai_5"

    /** 8 台呈現標籤。 */
    const val TAI_8: String = "mahjongcraft.taiwan.rule_catalogue.label.tai_8"

    /** 16 台呈現標籤。 */
    const val TAI_16: String = "mahjongcraft.taiwan.rule_catalogue.label.tai_16"

    /** 24 台呈現標籤。 */
    const val TAI_24: String = "mahjongcraft.taiwan.rule_catalogue.label.tai_24"

    /** 花牌停用時顯示的原因。 */
    const val FLOWERS_UNAVAILABLE: String = "mahjongcraft.taiwan.rule_catalogue.unavailable.flowers"

    /** 四風連打停用時顯示的原因。 */
    const val FOUR_WINDS_UNAVAILABLE: String = "mahjongcraft.taiwan.rule_catalogue.unavailable.four_winds"
}

/**
 * 內建計台項目與圖鑑的顯式對照。
 *
 * @property type 條目對應的計台項目。
 * @property id namespaced 條目識別碼。
 * @property descriptionKey 條目說明的翻譯鍵。
 * @property category 條目的主要分類。
 * @property valueKey 台數標籤。
 */
internal enum class TaiwanCatalogueTai(
    val type: TaiwanTaiType,
    val id: String,
    val descriptionKey: String,
    val category: TaiwanCatalogueCategory,
    val valueKey: String,
) {
    /** 門清的說明對照。 */
    ConcealedHand(TaiwanTaiType.ConcealedHand, MahjongCraftMetadata.id("catalogue/taiwan/concealed_hand"), "mahjongcraft.taiwan.rule_catalogue.entry.concealed_hand.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 自摸的說明對照。 */
    SelfDraw(TaiwanTaiType.SelfDraw, MahjongCraftMetadata.id("catalogue/taiwan/self_draw"), "mahjongcraft.taiwan.rule_catalogue.entry.self_draw.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 門清自摸的說明對照。 */
    FullyConcealedHand(TaiwanTaiType.FullyConcealedHand, MahjongCraftMetadata.id("catalogue/taiwan/fully_concealed_hand"), "mahjongcraft.taiwan.rule_catalogue.entry.fully_concealed_hand.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 門風的說明對照。 */
    SeatWind(TaiwanTaiType.SeatWind, MahjongCraftMetadata.id("catalogue/taiwan/seat_wind"), "mahjongcraft.taiwan.rule_catalogue.entry.seat_wind.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 圈風的說明對照。 */
    RoundWind(TaiwanTaiType.RoundWind, MahjongCraftMetadata.id("catalogue/taiwan/round_wind"), "mahjongcraft.taiwan.rule_catalogue.entry.round_wind.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 三元牌的說明對照。 */
    Dragon(TaiwanTaiType.Dragon, MahjongCraftMetadata.id("catalogue/taiwan/dragon"), "mahjongcraft.taiwan.rule_catalogue.entry.dragon.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 正花的說明對照。 */
    SeatFlower(TaiwanTaiType.SeatFlower, MahjongCraftMetadata.id("catalogue/taiwan/seat_flower"), "mahjongcraft.taiwan.rule_catalogue.entry.seat_flower.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 花槓的說明對照。 */
    FlowerSet(TaiwanTaiType.FlowerSet, MahjongCraftMetadata.id("catalogue/taiwan/flower_set"), "mahjongcraft.taiwan.rule_catalogue.entry.flower_set.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 獨聽的說明對照。 */
    SingleWait(TaiwanTaiType.SingleWait, MahjongCraftMetadata.id("catalogue/taiwan/single_wait"), "mahjongcraft.taiwan.rule_catalogue.entry.single_wait.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 搶槓的說明對照。 */
    RobbingKong(TaiwanTaiType.RobbingKong, MahjongCraftMetadata.id("catalogue/taiwan/robbing_kong"), "mahjongcraft.taiwan.rule_catalogue.entry.robbing_kong.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 槓上開花的說明對照。 */
    ReplacementWin(TaiwanTaiType.ReplacementWin, MahjongCraftMetadata.id("catalogue/taiwan/replacement_win"), "mahjongcraft.taiwan.rule_catalogue.entry.replacement_win.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 海底撈月的說明對照。 */
    LastDraw(TaiwanTaiType.LastDraw, MahjongCraftMetadata.id("catalogue/taiwan/last_draw"), "mahjongcraft.taiwan.rule_catalogue.entry.last_draw.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 平胡的說明對照。 */
    AllSequences(TaiwanTaiType.AllSequences, MahjongCraftMetadata.id("catalogue/taiwan/all_sequences"), "mahjongcraft.taiwan.rule_catalogue.entry.all_sequences.description", TaiwanCatalogueCategory.Tai2, TaiwanCatalogueKeys.TAI_2),

    /** 三暗刻的說明對照。 */
    ThreeConcealedTriplets(TaiwanTaiType.ThreeConcealedTriplets, MahjongCraftMetadata.id("catalogue/taiwan/three_concealed_triplets"), "mahjongcraft.taiwan.rule_catalogue.entry.three_concealed_triplets.description", TaiwanCatalogueCategory.Tai2, TaiwanCatalogueKeys.TAI_2),

    /** 四暗刻的說明對照。 */
    FourConcealedTriplets(TaiwanTaiType.FourConcealedTriplets, MahjongCraftMetadata.id("catalogue/taiwan/four_concealed_triplets"), "mahjongcraft.taiwan.rule_catalogue.entry.four_concealed_triplets.description", TaiwanCatalogueCategory.Tai5, TaiwanCatalogueKeys.TAI_5),

    /** 五暗刻的說明對照。 */
    FiveConcealedTriplets(TaiwanTaiType.FiveConcealedTriplets, MahjongCraftMetadata.id("catalogue/taiwan/five_concealed_triplets"), "mahjongcraft.taiwan.rule_catalogue.entry.five_concealed_triplets.description", TaiwanCatalogueCategory.Tai8, TaiwanCatalogueKeys.TAI_8),

    /** 全求人的說明對照。 */
    AllExposed(TaiwanTaiType.AllExposed, MahjongCraftMetadata.id("catalogue/taiwan/all_exposed"), "mahjongcraft.taiwan.rule_catalogue.entry.all_exposed.description", TaiwanCatalogueCategory.Tai2, TaiwanCatalogueKeys.TAI_2),

    /** 碰碰胡的說明對照。 */
    AllTriplets(TaiwanTaiType.AllTriplets, MahjongCraftMetadata.id("catalogue/taiwan/all_triplets"), "mahjongcraft.taiwan.rule_catalogue.entry.all_triplets.description", TaiwanCatalogueCategory.Tai4, TaiwanCatalogueKeys.TAI_4),

    /** 小三元的說明對照。 */
    LittleDragons(TaiwanTaiType.LittleDragons, MahjongCraftMetadata.id("catalogue/taiwan/little_dragons"), "mahjongcraft.taiwan.rule_catalogue.entry.little_dragons.description", TaiwanCatalogueCategory.Tai4, TaiwanCatalogueKeys.TAI_4),

    /** 大三元的說明對照。 */
    BigDragons(TaiwanTaiType.BigDragons, MahjongCraftMetadata.id("catalogue/taiwan/big_dragons"), "mahjongcraft.taiwan.rule_catalogue.entry.big_dragons.description", TaiwanCatalogueCategory.Tai8, TaiwanCatalogueKeys.TAI_8),

    /** 混一色的說明對照。 */
    HalfFlush(TaiwanTaiType.HalfFlush, MahjongCraftMetadata.id("catalogue/taiwan/half_flush"), "mahjongcraft.taiwan.rule_catalogue.entry.half_flush.description", TaiwanCatalogueCategory.Tai4, TaiwanCatalogueKeys.TAI_4),

    /** 清一色的說明對照。 */
    FullFlush(TaiwanTaiType.FullFlush, MahjongCraftMetadata.id("catalogue/taiwan/full_flush"), "mahjongcraft.taiwan.rule_catalogue.entry.full_flush.description", TaiwanCatalogueCategory.Tai8, TaiwanCatalogueKeys.TAI_8),

    /** 小四喜的說明對照。 */
    LittleWinds(TaiwanTaiType.LittleWinds, MahjongCraftMetadata.id("catalogue/taiwan/little_winds"), "mahjongcraft.taiwan.rule_catalogue.entry.little_winds.description", TaiwanCatalogueCategory.Tai8, TaiwanCatalogueKeys.TAI_8),

    /** 大四喜的說明對照。 */
    BigWinds(TaiwanTaiType.BigWinds, MahjongCraftMetadata.id("catalogue/taiwan/big_winds"), "mahjongcraft.taiwan.rule_catalogue.entry.big_winds.description", TaiwanCatalogueCategory.Tai16, TaiwanCatalogueKeys.TAI_16),

    /** 字一色的說明對照。 */
    AllHonors(TaiwanTaiType.AllHonors, MahjongCraftMetadata.id("catalogue/taiwan/all_honors"), "mahjongcraft.taiwan.rule_catalogue.entry.all_honors.description", TaiwanCatalogueCategory.Tai16, TaiwanCatalogueKeys.TAI_16),

    /** 報聽的說明對照。 */
    DeclaredReady(TaiwanTaiType.DeclaredReady, MahjongCraftMetadata.id("catalogue/taiwan/declared_ready"), "mahjongcraft.taiwan.rule_catalogue.entry.declared_ready.description", TaiwanCatalogueCategory.Tai1, TaiwanCatalogueKeys.TAI_1),

    /** 天聽的說明對照。 */
    HeavenlyReady(TaiwanTaiType.HeavenlyReady, MahjongCraftMetadata.id("catalogue/taiwan/heavenly_ready"), "mahjongcraft.taiwan.rule_catalogue.entry.heavenly_ready.description", TaiwanCatalogueCategory.Tai16, TaiwanCatalogueKeys.TAI_16),

    /** 地聽的說明對照。 */
    EarthlyReady(TaiwanTaiType.EarthlyReady, MahjongCraftMetadata.id("catalogue/taiwan/earthly_ready"), "mahjongcraft.taiwan.rule_catalogue.entry.earthly_ready.description", TaiwanCatalogueCategory.Tai8, TaiwanCatalogueKeys.TAI_8),

    /** 人胡的說明對照。 */
    HumanWin(TaiwanTaiType.HumanWin, MahjongCraftMetadata.id("catalogue/taiwan/human_win"), "mahjongcraft.taiwan.rule_catalogue.entry.human_win.description", TaiwanCatalogueCategory.Tai16, TaiwanCatalogueKeys.TAI_16),

    /** 地胡的說明對照。 */
    EarthlyWin(TaiwanTaiType.EarthlyWin, MahjongCraftMetadata.id("catalogue/taiwan/earthly_win"), "mahjongcraft.taiwan.rule_catalogue.entry.earthly_win.description", TaiwanCatalogueCategory.Tai16, TaiwanCatalogueKeys.TAI_16),

    /** 天胡的說明對照。 */
    HeavenlyWin(TaiwanTaiType.HeavenlyWin, MahjongCraftMetadata.id("catalogue/taiwan/heavenly_win"), "mahjongcraft.taiwan.rule_catalogue.entry.heavenly_win.description", TaiwanCatalogueCategory.Tai24, TaiwanCatalogueKeys.TAI_24),

    /** 八仙過海的說明對照。 */
    EightFlowers(TaiwanTaiType.EightFlowers, MahjongCraftMetadata.id("catalogue/taiwan/eight_flowers"), "mahjongcraft.taiwan.rule_catalogue.entry.eight_flowers.description", TaiwanCatalogueCategory.Tai8, TaiwanCatalogueKeys.TAI_8),

    /** 七搶一的說明對照。 */
    SevenFlowers(TaiwanTaiType.SevenFlowers, MahjongCraftMetadata.id("catalogue/taiwan/seven_flowers"), "mahjongcraft.taiwan.rule_catalogue.entry.seven_flowers.description", TaiwanCatalogueCategory.Tai8, TaiwanCatalogueKeys.TAI_8),
}

/**
 * 一般與途中流局的說明識別碼。
 *
 * @property id namespaced 條目識別碼。
 * @property nameKey 條目名稱的翻譯鍵。
 * @property descriptionKey 條目說明的翻譯鍵。
 */
internal enum class TaiwanCatalogueSpecial(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
) {
    /** 一般流局。 */
    Normal(MahjongCraftMetadata.id("catalogue/taiwan/normal"), "mahjongcraft.taiwan.rule_catalogue.draw.normal.name", "mahjongcraft.taiwan.rule_catalogue.draw.normal.description"),

    /** 無牌可補花或補槓的流局。 */
    NoSupplementalTile(MahjongCraftMetadata.id("catalogue/taiwan/no_supplemental_tile"), "mahjongcraft.taiwan.rule_catalogue.draw.no_supplemental_tile.name", "mahjongcraft.taiwan.rule_catalogue.draw.no_supplemental_tile.description"),

    /** 四槓流局。 */
    FourKans(MahjongCraftMetadata.id("catalogue/taiwan/four_kans"), "mahjongcraft.taiwan.rule_catalogue.draw.four_kans.name", "mahjongcraft.taiwan.rule_catalogue.draw.four_kans.description"),

    /** 四風連打。 */
    FourWinds(MahjongCraftMetadata.id("catalogue/taiwan/four_winds"), "mahjongcraft.taiwan.rule_catalogue.draw.four_winds.name", "mahjongcraft.taiwan.rule_catalogue.draw.four_winds.description"),
}
