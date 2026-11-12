package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiYakuTranslationKeys

/**
 * 日麻目錄的固定分類，排列順序與列表呈現一致。
 *
 * @property slug 穩定分類尾段，不依 enum 名稱或 ordinal 推導。
 */
internal enum class RiichiCatalogueCategory(
    private val slug: String,
) {
    /** han_1 說明分類。 */
    HAN_1("han_1"),

    /** han_2 說明分類。 */
    HAN_2("han_2"),

    /** han_3 說明分類。 */
    HAN_3("han_3"),

    /** han_5 說明分類。 */
    HAN_5("han_5"),

    /** han_6 說明分類。 */
    HAN_6("han_6"),

    /** yakuman 說明分類。 */
    YAKUMAN("yakuman"),

    /** double_yakuman 說明分類。 */
    DOUBLE_YAKUMAN("double_yakuman"),

    /** 古役說明分類；古役條目同時列在對應翻數的分類。 */
    LOCAL("local"),

    /** bonus 說明分類。 */
    BONUS("bonus"),

    /** mangan 說明分類。 */
    MANGAN("mangan"),

    /** abortive_draw 說明分類。 */
    ABORTIVE_DRAW("abortive_draw"),
    ;

    /** namespaced 分類識別碼。 */
    val id: String get() = MahjongCraftMetadata.id("catalogue/riichi/category/$slug")

    /** 分類名稱翻譯鍵。 */
    val nameKey: String get() = RiichiCatalogueKeys.PREFIX + "category.$slug"
}

/** 日麻目錄的共用翻譯鍵，不影響結算名稱契約。 */
internal object RiichiCatalogueKeys {
    /** 日麻目錄翻譯鍵前綴。 */
    const val PREFIX: String = MahjongCraftMetadata.PROJECT_ID + ".rule_catalogue.riichi."

    /** han_1 呈現標籤。 */
    const val HAN_1: String = PREFIX + "label.han_1"

    /** han_2 呈現標籤。 */
    const val HAN_2: String = PREFIX + "label.han_2"

    /** han_3 呈現標籤。 */
    const val HAN_3: String = PREFIX + "label.han_3"

    /** han_5 呈現標籤。 */
    const val HAN_5: String = PREFIX + "label.han_5"

    /** han_6 呈現標籤。 */
    const val HAN_6: String = PREFIX + "label.han_6"

    /** yakuman 呈現標籤。 */
    const val YAKUMAN: String = PREFIX + "label.yakuman"

    /** double_yakuman 呈現標籤。 */
    const val DOUBLE_YAKUMAN: String = PREFIX + "label.double_yakuman"

    /** closed_only 呈現標籤。 */
    const val CLOSED_ONLY: String = PREFIX + "label.closed_only"

    /** open_han_1 呈現標籤。 */
    const val OPEN_HAN_1: String = PREFIX + "label.open_han_1"

    /** open_han_2 呈現標籤。 */
    const val OPEN_HAN_2: String = PREFIX + "label.open_han_2"

    /** open_han_5 呈現標籤。 */
    const val OPEN_HAN_5: String = PREFIX + "label.open_han_5"

    /** bonus_only 呈現標籤。 */
    const val BONUS_ONLY: String = PREFIX + "label.bonus_only"

    /** mangan 呈現標籤。 */
    const val MANGAN: String = PREFIX + "label.mangan"

    /** 古役呈現標籤。 */
    const val LOCAL_YAKU: String = PREFIX + "label.local_yaku"

    /** 「不算役」標籤的說明。 */
    const val BONUS_ONLY_DESCRIPTION: String = PREFIX + "label.bonus_only.description"

    /** 「門清限定」標籤的說明。 */
    const val CLOSED_ONLY_DESCRIPTION: String = PREFIX + "label.closed_only.description"

    /** 「古役」標籤的說明。 */
    const val LOCAL_YAKU_DESCRIPTION: String = PREFIX + "label.local_yaku.description"

    /** 副露翻數標籤共用的說明。 */
    const val OPEN_HAN_DESCRIPTION: String = PREFIX + "label.open_han.description"

    /** 三元牌役牌的總稱，不以紅中名稱代替。 */
    const val DRAGON_NAME: String = PREFIX + "name.dragon"

    /** 本配置沒有赤寶牌的提示。 */
    const val RED_DORA_UNAVAILABLE: String = PREFIX + "unavailable.red_dora"

    /** 本配置沒有啟用古役的提示。 */
    const val LOCAL_YAKU_UNAVAILABLE: String = PREFIX + "unavailable.local_yaku"

    /** 三人日麻的牌組沒有二～八萬，役種無法成立的提示。 */
    const val THREE_PLAYER_TILES_UNAVAILABLE: String = PREFIX + "unavailable.three_player_tiles"
}

/**
 * 內建役種與目錄的顯式對照；翻譯尾段亦作為穩定條目尾段。
 *
 * @property type 條目的代表役種，決定名稱、標籤、無法成立的原因與範例。
 * @property slug 固定條目與翻譯尾段。
 * @property category 所屬說明分類。
 * @property valueKey 門清價值或加算標籤。
 * @property conditionKey 副露價值或門清限制標籤；null 表示沒有額外標籤。
 * @property coveredTypes 條目說明的全部役種；規則相同的多個役種（例如白、發、中三種役牌）合併成一個條目。
 */
internal enum class RiichiCatalogueYaku(
    val type: RiichiYakuType,
    private val slug: String,
    val category: RiichiCatalogueCategory,
    val valueKey: String,
    val conditionKey: String? = null,
    val coveredTypes: Set<RiichiYakuType> = setOf(type),
) {
    /** Dora 的說明對照。 */
    Dora(RiichiYakuType.Dora, "dora", RiichiCatalogueCategory.BONUS, RiichiCatalogueKeys.BONUS_ONLY),

    /** UraDora 的說明對照。 */
    UraDora(RiichiYakuType.UraDora, "uradora", RiichiCatalogueCategory.BONUS, RiichiCatalogueKeys.BONUS_ONLY),

    /** AkaDora 的說明對照。 */
    AkaDora(RiichiYakuType.AkaDora, "red_five", RiichiCatalogueCategory.BONUS, RiichiCatalogueKeys.BONUS_ONLY),

    /** 三人日麻拔北寶牌的說明對照。 */
    NukiDora(RiichiYakuType.NukiDora, "nuki_dora", RiichiCatalogueCategory.BONUS, RiichiCatalogueKeys.BONUS_ONLY),

    /** Tanyao 的說明對照。 */
    Tanyao(RiichiYakuType.Tanyao, "tanyao", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Pinfu 的說明對照。 */
    Pinfu(RiichiYakuType.Pinfu, "pinfu", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Iipeikou 的說明對照。 */
    Iipeikou(RiichiYakuType.Iipeikou, "ipeiko", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Riichi 的說明對照。 */
    Riichi(RiichiYakuType.Riichi, "reach", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** DoubleRiichi 的說明對照。 */
    DoubleRiichi(RiichiYakuType.DoubleRiichi, "double_reach", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Ippatsu 的說明對照。 */
    Ippatsu(RiichiYakuType.Ippatsu, "ippatsu", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** RinshanKaihou 的說明對照。 */
    RinshanKaihou(RiichiYakuType.RinshanKaihou, "rinshankaihoh", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Haitei 的說明對照。 */
    Haitei(RiichiYakuType.Haitei, "haitei", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Houtei 的說明對照。 */
    Houtei(RiichiYakuType.Houtei, "houtei", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Chankan 的說明對照。 */
    Chankan(RiichiYakuType.Chankan, "chankan", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Menzentsumo 的說明對照。 */
    Menzentsumo(RiichiYakuType.Menzentsumo, "tsumo", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Toitoi 的說明對照。 */
    Toitoi(RiichiYakuType.Toitoi, "toitoiho", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Sanankou 的說明對照。 */
    Sanankou(RiichiYakuType.Sanankou, "sananko", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Sankantsu 的說明對照。 */
    Sankantsu(RiichiYakuType.Sankantsu, "sankantsu", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** SanshokuDokoku 的說明對照。 */
    SanshokuDokoku(RiichiYakuType.SanshokuDokoku, "sanshokudohko", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** SanshokuDoujun 的說明對照。 */
    SanshokuDoujun(RiichiYakuType.SanshokuDoujun, "sanshokudohjun", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Honchan 的說明對照。 */
    Honchan(RiichiYakuType.Honchan, "chanta", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Junchan 的說明對照。 */
    Junchan(RiichiYakuType.Junchan, "junchan", RiichiCatalogueCategory.HAN_3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** Honitsu 的說明對照。 */
    Honitsu(RiichiYakuType.Honitsu, "honitsu", RiichiCatalogueCategory.HAN_3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** Ryanpeikou 的說明對照。 */
    Ryanpeikou(RiichiYakuType.Ryanpeikou, "ryanpeiko", RiichiCatalogueCategory.HAN_3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Ittuitsu 的說明對照。 */
    Ittuitsu(RiichiYakuType.Ittuitsu, "ikkitsukan", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Honroutou 的說明對照。 */
    Honroutou(RiichiYakuType.Honroutou, "honrohtoh", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Chinitsu 的說明對照。 */
    Chinitsu(RiichiYakuType.Chinitsu, "chinitsu", RiichiCatalogueCategory.HAN_6, RiichiCatalogueKeys.HAN_6, RiichiCatalogueKeys.OPEN_HAN_5),

    /** Shousangen 的說明對照。 */
    Shousangen(RiichiYakuType.Shousangen, "shosangen", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Chiitoitsu 的說明對照。 */
    Chiitoitsu(RiichiYakuType.Chiitoitsu, "chitoitsu", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.CLOSED_ONLY),

    /** RoundWind 的說明對照。 */
    RoundWind(RiichiYakuType.RoundWind, "bakaze", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** SeatWind 的說明對照。 */
    SeatWind(RiichiYakuType.SeatWind, "jikaze", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** 白、發、中三種役牌合併的說明對照，以中為範例。 */
    Dragon(
        type = RiichiYakuType.RedDragon,
        slug = "dragon",
        category = RiichiCatalogueCategory.HAN_1,
        valueKey = RiichiCatalogueKeys.HAN_1,
        coveredTypes = setOf(RiichiYakuType.WhiteDragon, RiichiYakuType.GreenDragon, RiichiYakuType.RedDragon),
    ),

    /** KokushiMusou 的說明對照。 */
    KokushiMusou(RiichiYakuType.KokushiMusou, "kokushimuso", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** ChurenPoto 的說明對照。 */
    ChurenPoto(RiichiYakuType.ChurenPoto, "churenpohto", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Tsuuiisou 的說明對照。 */
    Tsuuiisou(RiichiYakuType.Tsuuiisou, "tsuiso", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Ryuuuiisou 的說明對照。 */
    Ryuuuiisou(RiichiYakuType.Ryuuuiisou, "ryuiso", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Suuankou 的說明對照。 */
    Suuankou(RiichiYakuType.Suuankou, "suanko", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Sukantsu 的說明對照。 */
    Sukantsu(RiichiYakuType.Sukantsu, "sukantsu", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Shousuushi 的說明對照。 */
    Shousuushi(RiichiYakuType.Shousuushi, "shosushi", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Daisangen 的說明對照。 */
    Daisangen(RiichiYakuType.Daisangen, "daisangen", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Chinroutou 的說明對照。 */
    Chinroutou(RiichiYakuType.Chinroutou, "chinroto", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Tenhou 的說明對照。 */
    Tenhou(RiichiYakuType.Tenhou, "tenho", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Chiihou 的說明對照。 */
    Chiihou(RiichiYakuType.Chiihou, "chiho", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** KokushiMusou13 的說明對照。 */
    KokushiMusou13(RiichiYakuType.KokushiMusou13, "kokushimuso_jusanmenmachi", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** ChurenPoto9 的說明對照。 */
    ChurenPoto9(RiichiYakuType.ChurenPoto9, "junsei_churenpohto", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** SuuankouTanki 的說明對照。 */
    SuuankouTanki(RiichiYakuType.SuuankouTanki, "suanko_tanki", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Daisuushii 的說明對照。 */
    Daisuushii(RiichiYakuType.Daisuushii, "daisushi", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN),

    /** 古役燕返的說明對照。 */
    TsubameGaeshi(RiichiYakuType.TsubameGaeshi, "tsubame_gaeshi", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** 古役槓振的說明對照。 */
    Kanburi(RiichiYakuType.Kanburi, "kanburi", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** 古役十二落抬的說明對照。 */
    ShiiaruRaotai(RiichiYakuType.ShiiaruRaotai, "shiiaru_raotai", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** 古役五門齊的說明對照。 */
    UumenChii(RiichiYakuType.UumenChii, "uumen_chii", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** 古役三連刻的說明對照。 */
    Sanrenkou(RiichiYakuType.Sanrenkou, "sanrenkou", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** 古役一色三同順的說明對照。 */
    IsshokuSanjun(RiichiYakuType.IsshokuSanjun, "isshoku_sanjun", RiichiCatalogueCategory.HAN_3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** 古役一筒摸月的說明對照。 */
    IipinMoyue(RiichiYakuType.IipinMoyue, "iipin_moyue", RiichiCatalogueCategory.HAN_5, RiichiCatalogueKeys.HAN_5),

    /** 古役九筒撈魚的說明對照。 */
    ChuupinRaoyui(RiichiYakuType.ChuupinRaoyui, "chuupin_raoyui", RiichiCatalogueCategory.HAN_5, RiichiCatalogueKeys.HAN_5),

    /** 古役人和的說明對照。 */
    Renhou(RiichiYakuType.Renhou, "renhou", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大車輪的說明對照。 */
    Daisharin(RiichiYakuType.Daisharin, "daisharin", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大竹林的說明對照。 */
    Daichikurin(RiichiYakuType.Daichikurin, "daichikurin", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大數鄰的說明對照。 */
    Daisuurin(RiichiYakuType.Daisuurin, "daisuurin", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役石上三年的說明對照。 */
    IshinoUeSannen(RiichiYakuType.IshinoUeSannen, "ishi_no_ue_ni_mo_sannen", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** 古役大七星的說明對照。 */
    Daichisei(RiichiYakuType.Daichisei, "daichisei", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),
    ;

    /** namespaced 役種說明識別碼。 */
    val id: String get() = MahjongCraftMetadata.id("catalogue/riichi/$slug")

    /** 役種說明翻譯鍵。 */
    val descriptionKey: String get() = RiichiCatalogueKeys.PREFIX + "entry.$slug.description"
}

/**
 * 非一般役種的特殊結算及途中流局說明識別碼。
 *
 * @property slug 固定條目尾段。
 */
internal enum class RiichiCatalogueSpecial(
    private val slug: String,
) {
    /** 流局滿貫。 */
    NAGASHI_MANGAN("nagashi_mangan"),

    /** 四風連打。 */
    SUUFON_RENDA("suufon_renda"),

    /** 四槓散了。 */
    SUUKAIKAN("suukaikan"),

    /** 九種九牌。 */
    KYUUSHU_KYUUHAI("kyuushu_kyuuhai"),

    /** 四家立直。 */
    SUUCHA_RIICHI("suucha_riichi"),
    ;

    /** namespaced 特殊說明識別碼。 */
    val id: String get() = MahjongCraftMetadata.id("catalogue/riichi/$slug")

    /** 特殊說明名稱翻譯鍵；流局滿貫沿用既有結算名稱。 */
    val nameKey: String get() = if (this == NAGASHI_MANGAN) RiichiYakuTranslationKeys.NAGASHI_MANGAN else RiichiCatalogueKeys.PREFIX + "name.$slug"

    /** 特殊說明完整敘述翻譯鍵。 */
    val descriptionKey: String get() = RiichiCatalogueKeys.PREFIX + "entry.$slug.description"
}
