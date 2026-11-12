package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiYakuTranslationKeys

/**
 * 日麻目錄的固定分類，排列順序與列表呈現一致。
 *
 * @property id namespaced 分類識別碼，逐列寫明，不依 enum 名稱或 ordinal 推導。
 */
internal enum class RiichiCatalogueCategory(
    val id: String,
    val nameKey: String,
) {
    /** han_1 說明分類。 */
    Han1(MahjongCraftMetadata.id("catalogue/riichi/category/han_1"), "mahjongcraft.riichi.rule_catalogue.category.han_1"),

    /** han_2 說明分類。 */
    Han2(MahjongCraftMetadata.id("catalogue/riichi/category/han_2"), "mahjongcraft.riichi.rule_catalogue.category.han_2"),

    /** han_3 說明分類。 */
    Han3(MahjongCraftMetadata.id("catalogue/riichi/category/han_3"), "mahjongcraft.riichi.rule_catalogue.category.han_3"),

    /** han_5 說明分類。 */
    Han5(MahjongCraftMetadata.id("catalogue/riichi/category/han_5"), "mahjongcraft.riichi.rule_catalogue.category.han_5"),

    /** han_6 說明分類。 */
    Han6(MahjongCraftMetadata.id("catalogue/riichi/category/han_6"), "mahjongcraft.riichi.rule_catalogue.category.han_6"),

    /** yakuman 說明分類。 */
    Yakuman(MahjongCraftMetadata.id("catalogue/riichi/category/yakuman"), "mahjongcraft.riichi.rule_catalogue.category.yakuman"),

    /** double_yakuman 說明分類。 */
    DoubleYakuman(MahjongCraftMetadata.id("catalogue/riichi/category/double_yakuman"), "mahjongcraft.riichi.rule_catalogue.category.double_yakuman"),

    /** 古役說明分類；古役條目同時列在對應翻數的分類。 */
    Local(MahjongCraftMetadata.id("catalogue/riichi/category/local"), "mahjongcraft.riichi.rule_catalogue.category.local"),

    /** bonus 說明分類。 */
    Bonus(MahjongCraftMetadata.id("catalogue/riichi/category/bonus"), "mahjongcraft.riichi.rule_catalogue.category.bonus"),

    /** mangan 說明分類。 */
    Mangan(MahjongCraftMetadata.id("catalogue/riichi/category/mangan"), "mahjongcraft.riichi.rule_catalogue.category.mangan"),

    /** abortive_draw 說明分類。 */
    AbortiveDraw(MahjongCraftMetadata.id("catalogue/riichi/category/abortive_draw"), "mahjongcraft.riichi.rule_catalogue.category.abortive_draw"),
}

/** 日麻目錄的共用翻譯鍵，不影響結算名稱契約。 */
internal object RiichiCatalogueKeys {
    /** han_1 呈現標籤。 */
    const val HAN_1: String = "mahjongcraft.riichi.rule_catalogue.label.han_1"

    /** han_2 呈現標籤。 */
    const val HAN_2: String = "mahjongcraft.riichi.rule_catalogue.label.han_2"

    /** han_3 呈現標籤。 */
    const val HAN_3: String = "mahjongcraft.riichi.rule_catalogue.label.han_3"

    /** han_5 呈現標籤。 */
    const val HAN_5: String = "mahjongcraft.riichi.rule_catalogue.label.han_5"

    /** han_6 呈現標籤。 */
    const val HAN_6: String = "mahjongcraft.riichi.rule_catalogue.label.han_6"

    /** yakuman 呈現標籤。 */
    const val YAKUMAN: String = "mahjongcraft.riichi.rule_catalogue.label.yakuman"

    /** double_yakuman 呈現標籤。 */
    const val DOUBLE_YAKUMAN: String = "mahjongcraft.riichi.rule_catalogue.label.double_yakuman"

    /** closed_only 呈現標籤。 */
    const val CLOSED_ONLY: String = "mahjongcraft.riichi.rule_catalogue.label.closed_only"

    /** open_han_1 呈現標籤。 */
    const val OPEN_HAN_1: String = "mahjongcraft.riichi.rule_catalogue.label.open_han_1"

    /** open_han_2 呈現標籤。 */
    const val OPEN_HAN_2: String = "mahjongcraft.riichi.rule_catalogue.label.open_han_2"

    /** open_han_5 呈現標籤。 */
    const val OPEN_HAN_5: String = "mahjongcraft.riichi.rule_catalogue.label.open_han_5"

    /** bonus_only 呈現標籤。 */
    const val BONUS_ONLY: String = "mahjongcraft.riichi.rule_catalogue.label.bonus_only"

    /** mangan 呈現標籤。 */
    const val MANGAN: String = "mahjongcraft.riichi.rule_catalogue.label.mangan"

    /** 古役呈現標籤。 */
    const val LOCAL_YAKU: String = "mahjongcraft.riichi.rule_catalogue.label.local_yaku"

    /** 「不算役」標籤的說明。 */
    const val BONUS_ONLY_DESCRIPTION: String = "mahjongcraft.riichi.rule_catalogue.label.bonus_only.description"

    /** 「門清限定」標籤的說明。 */
    const val CLOSED_ONLY_DESCRIPTION: String = "mahjongcraft.riichi.rule_catalogue.label.closed_only.description"

    /** 「古役」標籤的說明。 */
    const val LOCAL_YAKU_DESCRIPTION: String = "mahjongcraft.riichi.rule_catalogue.label.local_yaku.description"

    /** 副露翻數標籤共用的說明。 */
    const val OPEN_HAN_DESCRIPTION: String = "mahjongcraft.riichi.rule_catalogue.label.open_han.description"

    /** 三元牌役牌的總稱，不以紅中名稱代替。 */
    const val DRAGON_NAME: String = "mahjongcraft.riichi.rule_catalogue.name.dragon"

    /** 本配置沒有赤寶牌的提示。 */
    const val RED_DORA_UNAVAILABLE: String = "mahjongcraft.riichi.rule_catalogue.unavailable.red_dora"

    /** 本配置沒有啟用古役的提示。 */
    const val LOCAL_YAKU_UNAVAILABLE: String = "mahjongcraft.riichi.rule_catalogue.unavailable.local_yaku"

    /** 三人日麻的牌組沒有二～八萬，役種無法成立的提示。 */
    const val THREE_PLAYER_TILES_UNAVAILABLE: String = "mahjongcraft.riichi.rule_catalogue.unavailable.three_player_tiles"
}

/**
 * 內建役種與目錄的顯式對照。
 *
 * @property type 條目的代表役種，決定名稱、標籤、無法成立的原因與範例。
 * @property id namespaced 條目識別碼。
 * @property descriptionKey 條目說明的翻譯鍵。
 * @property category 所屬說明分類。
 * @property valueKey 門清價值或加算標籤。
 * @property conditionKey 副露價值或門清限制標籤；null 表示沒有額外標籤。
 * @property coveredTypes 條目說明的全部役種；規則相同的多個役種（例如白、發、中三種役牌）合併成一個條目。
 */
internal enum class RiichiCatalogueYaku(
    val type: RiichiYakuType,
    val id: String,
    val descriptionKey: String,
    val category: RiichiCatalogueCategory,
    val valueKey: String,
    val conditionKey: String? = null,
    val coveredTypes: Set<RiichiYakuType> = setOf(type),
) {
    /** Dora 的說明對照。 */
    Dora(RiichiYakuType.Dora, MahjongCraftMetadata.id("catalogue/riichi/dora"), "mahjongcraft.riichi.rule_catalogue.entry.dora.description", RiichiCatalogueCategory.Bonus, RiichiCatalogueKeys.BONUS_ONLY),

    /** UraDora 的說明對照。 */
    UraDora(RiichiYakuType.UraDora, MahjongCraftMetadata.id("catalogue/riichi/uradora"), "mahjongcraft.riichi.rule_catalogue.entry.uradora.description", RiichiCatalogueCategory.Bonus, RiichiCatalogueKeys.BONUS_ONLY),

    /** AkaDora 的說明對照。 */
    AkaDora(RiichiYakuType.AkaDora, MahjongCraftMetadata.id("catalogue/riichi/red_five"), "mahjongcraft.riichi.rule_catalogue.entry.red_five.description", RiichiCatalogueCategory.Bonus, RiichiCatalogueKeys.BONUS_ONLY),

    /** 三人日麻拔北寶牌的說明對照。 */
    NukiDora(RiichiYakuType.NukiDora, MahjongCraftMetadata.id("catalogue/riichi/nuki_dora"), "mahjongcraft.riichi.rule_catalogue.entry.nuki_dora.description", RiichiCatalogueCategory.Bonus, RiichiCatalogueKeys.BONUS_ONLY),

    /** Tanyao 的說明對照。 */
    Tanyao(RiichiYakuType.Tanyao, MahjongCraftMetadata.id("catalogue/riichi/tanyao"), "mahjongcraft.riichi.rule_catalogue.entry.tanyao.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** Pinfu 的說明對照。 */
    Pinfu(RiichiYakuType.Pinfu, MahjongCraftMetadata.id("catalogue/riichi/pinfu"), "mahjongcraft.riichi.rule_catalogue.entry.pinfu.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Iipeikou 的說明對照。 */
    Iipeikou(RiichiYakuType.Iipeikou, MahjongCraftMetadata.id("catalogue/riichi/ipeiko"), "mahjongcraft.riichi.rule_catalogue.entry.ipeiko.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Riichi 的說明對照。 */
    Riichi(RiichiYakuType.Riichi, MahjongCraftMetadata.id("catalogue/riichi/reach"), "mahjongcraft.riichi.rule_catalogue.entry.reach.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** DoubleRiichi 的說明對照。 */
    DoubleRiichi(RiichiYakuType.DoubleRiichi, MahjongCraftMetadata.id("catalogue/riichi/double_reach"), "mahjongcraft.riichi.rule_catalogue.entry.double_reach.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Ippatsu 的說明對照。 */
    Ippatsu(RiichiYakuType.Ippatsu, MahjongCraftMetadata.id("catalogue/riichi/ippatsu"), "mahjongcraft.riichi.rule_catalogue.entry.ippatsu.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** RinshanKaihou 的說明對照。 */
    RinshanKaihou(RiichiYakuType.RinshanKaihou, MahjongCraftMetadata.id("catalogue/riichi/rinshankaihoh"), "mahjongcraft.riichi.rule_catalogue.entry.rinshankaihoh.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** Haitei 的說明對照。 */
    Haitei(RiichiYakuType.Haitei, MahjongCraftMetadata.id("catalogue/riichi/haitei"), "mahjongcraft.riichi.rule_catalogue.entry.haitei.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** Houtei 的說明對照。 */
    Houtei(RiichiYakuType.Houtei, MahjongCraftMetadata.id("catalogue/riichi/houtei"), "mahjongcraft.riichi.rule_catalogue.entry.houtei.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** Chankan 的說明對照。 */
    Chankan(RiichiYakuType.Chankan, MahjongCraftMetadata.id("catalogue/riichi/chankan"), "mahjongcraft.riichi.rule_catalogue.entry.chankan.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** Menzentsumo 的說明對照。 */
    Menzentsumo(RiichiYakuType.Menzentsumo, MahjongCraftMetadata.id("catalogue/riichi/tsumo"), "mahjongcraft.riichi.rule_catalogue.entry.tsumo.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Toitoi 的說明對照。 */
    Toitoi(RiichiYakuType.Toitoi, MahjongCraftMetadata.id("catalogue/riichi/toitoiho"), "mahjongcraft.riichi.rule_catalogue.entry.toitoiho.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** Sanankou 的說明對照。 */
    Sanankou(RiichiYakuType.Sanankou, MahjongCraftMetadata.id("catalogue/riichi/sananko"), "mahjongcraft.riichi.rule_catalogue.entry.sananko.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** Sankantsu 的說明對照。 */
    Sankantsu(RiichiYakuType.Sankantsu, MahjongCraftMetadata.id("catalogue/riichi/sankantsu"), "mahjongcraft.riichi.rule_catalogue.entry.sankantsu.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** SanshokuDokoku 的說明對照。 */
    SanshokuDokoku(RiichiYakuType.SanshokuDokoku, MahjongCraftMetadata.id("catalogue/riichi/sanshokudohko"), "mahjongcraft.riichi.rule_catalogue.entry.sanshokudohko.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** SanshokuDoujun 的說明對照。 */
    SanshokuDoujun(RiichiYakuType.SanshokuDoujun, MahjongCraftMetadata.id("catalogue/riichi/sanshokudohjun"), "mahjongcraft.riichi.rule_catalogue.entry.sanshokudohjun.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Honchan 的說明對照。 */
    Honchan(RiichiYakuType.Honchan, MahjongCraftMetadata.id("catalogue/riichi/chanta"), "mahjongcraft.riichi.rule_catalogue.entry.chanta.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Junchan 的說明對照。 */
    Junchan(RiichiYakuType.Junchan, MahjongCraftMetadata.id("catalogue/riichi/junchan"), "mahjongcraft.riichi.rule_catalogue.entry.junchan.description", RiichiCatalogueCategory.Han3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** Honitsu 的說明對照。 */
    Honitsu(RiichiYakuType.Honitsu, MahjongCraftMetadata.id("catalogue/riichi/honitsu"), "mahjongcraft.riichi.rule_catalogue.entry.honitsu.description", RiichiCatalogueCategory.Han3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** Ryanpeikou 的說明對照。 */
    Ryanpeikou(RiichiYakuType.Ryanpeikou, MahjongCraftMetadata.id("catalogue/riichi/ryanpeiko"), "mahjongcraft.riichi.rule_catalogue.entry.ryanpeiko.description", RiichiCatalogueCategory.Han3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Ittuitsu 的說明對照。 */
    Ittuitsu(RiichiYakuType.Ittuitsu, MahjongCraftMetadata.id("catalogue/riichi/ikkitsukan"), "mahjongcraft.riichi.rule_catalogue.entry.ikkitsukan.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Honroutou 的說明對照。 */
    Honroutou(RiichiYakuType.Honroutou, MahjongCraftMetadata.id("catalogue/riichi/honrohtoh"), "mahjongcraft.riichi.rule_catalogue.entry.honrohtoh.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** Chinitsu 的說明對照。 */
    Chinitsu(RiichiYakuType.Chinitsu, MahjongCraftMetadata.id("catalogue/riichi/chinitsu"), "mahjongcraft.riichi.rule_catalogue.entry.chinitsu.description", RiichiCatalogueCategory.Han6, RiichiCatalogueKeys.HAN_6, RiichiCatalogueKeys.OPEN_HAN_5),

    /** Shousangen 的說明對照。 */
    Shousangen(RiichiYakuType.Shousangen, MahjongCraftMetadata.id("catalogue/riichi/shosangen"), "mahjongcraft.riichi.rule_catalogue.entry.shosangen.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** Chiitoitsu 的說明對照。 */
    Chiitoitsu(RiichiYakuType.Chiitoitsu, MahjongCraftMetadata.id("catalogue/riichi/chitoitsu"), "mahjongcraft.riichi.rule_catalogue.entry.chitoitsu.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.CLOSED_ONLY),

    /** RoundWind 的說明對照。 */
    RoundWind(RiichiYakuType.RoundWind, MahjongCraftMetadata.id("catalogue/riichi/bakaze"), "mahjongcraft.riichi.rule_catalogue.entry.bakaze.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** SeatWind 的說明對照。 */
    SeatWind(RiichiYakuType.SeatWind, MahjongCraftMetadata.id("catalogue/riichi/jikaze"), "mahjongcraft.riichi.rule_catalogue.entry.jikaze.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** 白、發、中三種役牌合併的說明對照，以中為範例。 */
    Dragon(
        type = RiichiYakuType.RedDragon,
        id = MahjongCraftMetadata.id("catalogue/riichi/dragon"),
        descriptionKey = "mahjongcraft.riichi.rule_catalogue.entry.dragon.description",
        category = RiichiCatalogueCategory.Han1,
        valueKey = RiichiCatalogueKeys.HAN_1,
        coveredTypes = setOf(RiichiYakuType.WhiteDragon, RiichiYakuType.GreenDragon, RiichiYakuType.RedDragon),
    ),

    /** KokushiMusou 的說明對照。 */
    KokushiMusou(RiichiYakuType.KokushiMusou, MahjongCraftMetadata.id("catalogue/riichi/kokushimuso"), "mahjongcraft.riichi.rule_catalogue.entry.kokushimuso.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** ChurenPoto 的說明對照。 */
    ChurenPoto(RiichiYakuType.ChurenPoto, MahjongCraftMetadata.id("catalogue/riichi/churenpohto"), "mahjongcraft.riichi.rule_catalogue.entry.churenpohto.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Tsuuiisou 的說明對照。 */
    Tsuuiisou(RiichiYakuType.Tsuuiisou, MahjongCraftMetadata.id("catalogue/riichi/tsuiso"), "mahjongcraft.riichi.rule_catalogue.entry.tsuiso.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN),

    /** Ryuuuiisou 的說明對照。 */
    Ryuuuiisou(RiichiYakuType.Ryuuuiisou, MahjongCraftMetadata.id("catalogue/riichi/ryuiso"), "mahjongcraft.riichi.rule_catalogue.entry.ryuiso.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN),

    /** Suuankou 的說明對照。 */
    Suuankou(RiichiYakuType.Suuankou, MahjongCraftMetadata.id("catalogue/riichi/suanko"), "mahjongcraft.riichi.rule_catalogue.entry.suanko.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Sukantsu 的說明對照。 */
    Sukantsu(RiichiYakuType.Sukantsu, MahjongCraftMetadata.id("catalogue/riichi/sukantsu"), "mahjongcraft.riichi.rule_catalogue.entry.sukantsu.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN),

    /** Shousuushi 的說明對照。 */
    Shousuushi(RiichiYakuType.Shousuushi, MahjongCraftMetadata.id("catalogue/riichi/shosushi"), "mahjongcraft.riichi.rule_catalogue.entry.shosushi.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN),

    /** Daisangen 的說明對照。 */
    Daisangen(RiichiYakuType.Daisangen, MahjongCraftMetadata.id("catalogue/riichi/daisangen"), "mahjongcraft.riichi.rule_catalogue.entry.daisangen.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN),

    /** Chinroutou 的說明對照。 */
    Chinroutou(RiichiYakuType.Chinroutou, MahjongCraftMetadata.id("catalogue/riichi/chinroto"), "mahjongcraft.riichi.rule_catalogue.entry.chinroto.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN),

    /** Tenhou 的說明對照。 */
    Tenhou(RiichiYakuType.Tenhou, MahjongCraftMetadata.id("catalogue/riichi/tenho"), "mahjongcraft.riichi.rule_catalogue.entry.tenho.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Chiihou 的說明對照。 */
    Chiihou(RiichiYakuType.Chiihou, MahjongCraftMetadata.id("catalogue/riichi/chiho"), "mahjongcraft.riichi.rule_catalogue.entry.chiho.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** KokushiMusou13 的說明對照。 */
    KokushiMusou13(RiichiYakuType.KokushiMusou13, MahjongCraftMetadata.id("catalogue/riichi/kokushimuso_jusanmenmachi"), "mahjongcraft.riichi.rule_catalogue.entry.kokushimuso_jusanmenmachi.description", RiichiCatalogueCategory.DoubleYakuman, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** ChurenPoto9 的說明對照。 */
    ChurenPoto9(RiichiYakuType.ChurenPoto9, MahjongCraftMetadata.id("catalogue/riichi/junsei_churenpohto"), "mahjongcraft.riichi.rule_catalogue.entry.junsei_churenpohto.description", RiichiCatalogueCategory.DoubleYakuman, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** SuuankouTanki 的說明對照。 */
    SuuankouTanki(RiichiYakuType.SuuankouTanki, MahjongCraftMetadata.id("catalogue/riichi/suanko_tanki"), "mahjongcraft.riichi.rule_catalogue.entry.suanko_tanki.description", RiichiCatalogueCategory.DoubleYakuman, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Daisuushii 的說明對照。 */
    Daisuushii(RiichiYakuType.Daisuushii, MahjongCraftMetadata.id("catalogue/riichi/daisushi"), "mahjongcraft.riichi.rule_catalogue.entry.daisushi.description", RiichiCatalogueCategory.DoubleYakuman, RiichiCatalogueKeys.DOUBLE_YAKUMAN),

    /** 古役燕返的說明對照。 */
    TsubameGaeshi(RiichiYakuType.TsubameGaeshi, MahjongCraftMetadata.id("catalogue/riichi/tsubame_gaeshi"), "mahjongcraft.riichi.rule_catalogue.entry.tsubame_gaeshi.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** 古役槓振的說明對照。 */
    Kanburi(RiichiYakuType.Kanburi, MahjongCraftMetadata.id("catalogue/riichi/kanburi"), "mahjongcraft.riichi.rule_catalogue.entry.kanburi.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** 古役十二落抬的說明對照。 */
    ShiiaruRaotai(RiichiYakuType.ShiiaruRaotai, MahjongCraftMetadata.id("catalogue/riichi/shiiaru_raotai"), "mahjongcraft.riichi.rule_catalogue.entry.shiiaru_raotai.description", RiichiCatalogueCategory.Han1, RiichiCatalogueKeys.HAN_1),

    /** 古役五門齊的說明對照。 */
    UumenChii(RiichiYakuType.UumenChii, MahjongCraftMetadata.id("catalogue/riichi/uumen_chii"), "mahjongcraft.riichi.rule_catalogue.entry.uumen_chii.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** 古役三連刻的說明對照。 */
    Sanrenkou(RiichiYakuType.Sanrenkou, MahjongCraftMetadata.id("catalogue/riichi/sanrenkou"), "mahjongcraft.riichi.rule_catalogue.entry.sanrenkou.description", RiichiCatalogueCategory.Han2, RiichiCatalogueKeys.HAN_2),

    /** 古役一色三同順的說明對照。 */
    IsshokuSanjun(RiichiYakuType.IsshokuSanjun, MahjongCraftMetadata.id("catalogue/riichi/isshoku_sanjun"), "mahjongcraft.riichi.rule_catalogue.entry.isshoku_sanjun.description", RiichiCatalogueCategory.Han3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** 古役一筒摸月的說明對照。 */
    IipinMoyue(RiichiYakuType.IipinMoyue, MahjongCraftMetadata.id("catalogue/riichi/iipin_moyue"), "mahjongcraft.riichi.rule_catalogue.entry.iipin_moyue.description", RiichiCatalogueCategory.Han5, RiichiCatalogueKeys.HAN_5),

    /** 古役九筒撈魚的說明對照。 */
    ChuupinRaoyui(RiichiYakuType.ChuupinRaoyui, MahjongCraftMetadata.id("catalogue/riichi/chuupin_raoyui"), "mahjongcraft.riichi.rule_catalogue.entry.chuupin_raoyui.description", RiichiCatalogueCategory.Han5, RiichiCatalogueKeys.HAN_5),

    /** 古役人和的說明對照。 */
    Renhou(RiichiYakuType.Renhou, MahjongCraftMetadata.id("catalogue/riichi/renhou"), "mahjongcraft.riichi.rule_catalogue.entry.renhou.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大車輪的說明對照。 */
    Daisharin(RiichiYakuType.Daisharin, MahjongCraftMetadata.id("catalogue/riichi/daisharin"), "mahjongcraft.riichi.rule_catalogue.entry.daisharin.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大竹林的說明對照。 */
    Daichikurin(RiichiYakuType.Daichikurin, MahjongCraftMetadata.id("catalogue/riichi/daichikurin"), "mahjongcraft.riichi.rule_catalogue.entry.daichikurin.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大數鄰的說明對照。 */
    Daisuurin(RiichiYakuType.Daisuurin, MahjongCraftMetadata.id("catalogue/riichi/daisuurin"), "mahjongcraft.riichi.rule_catalogue.entry.daisuurin.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役石上三年的說明對照。 */
    IshinoUeSannen(RiichiYakuType.IshinoUeSannen, MahjongCraftMetadata.id("catalogue/riichi/ishi_no_ue_ni_mo_sannen"), "mahjongcraft.riichi.rule_catalogue.entry.ishi_no_ue_ni_mo_sannen.description", RiichiCatalogueCategory.Yakuman, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大七星的說明對照。 */
    Daichisei(RiichiYakuType.Daichisei, MahjongCraftMetadata.id("catalogue/riichi/daichisei"), "mahjongcraft.riichi.rule_catalogue.entry.daichisei.description", RiichiCatalogueCategory.DoubleYakuman, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),
}

/**
 * 非一般役種的特殊結算及途中流局說明識別碼。
 *
 * @property id namespaced 條目識別碼。
 * @property nameKey 條目名稱的翻譯鍵；流局滿貫沿用結算名稱。
 * @property descriptionKey 條目說明的翻譯鍵。
 */
internal enum class RiichiCatalogueSpecial(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
) {
    /** 流局滿貫。 */
    NagashiMangan(MahjongCraftMetadata.id("catalogue/riichi/nagashi_mangan"), RiichiYakuTranslationKeys.NAGASHI_MANGAN, "mahjongcraft.riichi.rule_catalogue.entry.nagashi_mangan.description"),

    /** 四風連打。 */
    SuufonRenda(MahjongCraftMetadata.id("catalogue/riichi/suufon_renda"), "mahjongcraft.riichi.rule_catalogue.name.suufon_renda", "mahjongcraft.riichi.rule_catalogue.entry.suufon_renda.description"),

    /** 四槓散了。 */
    Suukaikan(MahjongCraftMetadata.id("catalogue/riichi/suukaikan"), "mahjongcraft.riichi.rule_catalogue.name.suukaikan", "mahjongcraft.riichi.rule_catalogue.entry.suukaikan.description"),

    /** 九種九牌。 */
    KyuushuKyuuhai(MahjongCraftMetadata.id("catalogue/riichi/kyuushu_kyuuhai"), "mahjongcraft.riichi.rule_catalogue.name.kyuushu_kyuuhai", "mahjongcraft.riichi.rule_catalogue.entry.kyuushu_kyuuhai.description"),

    /** 四家立直。 */
    SuuchaRiichi(MahjongCraftMetadata.id("catalogue/riichi/suucha_riichi"), "mahjongcraft.riichi.rule_catalogue.name.suucha_riichi", "mahjongcraft.riichi.rule_catalogue.entry.suucha_riichi.description"),
}
