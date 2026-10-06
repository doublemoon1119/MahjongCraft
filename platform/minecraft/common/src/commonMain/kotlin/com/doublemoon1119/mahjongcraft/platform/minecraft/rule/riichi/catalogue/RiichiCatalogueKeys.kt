package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
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

    /** han_6 說明分類。 */
    HAN_6("han_6"),

    /** yakuman 說明分類。 */
    YAKUMAN("yakuman"),

    /** double_yakuman 說明分類。 */
    DOUBLE_YAKUMAN("double_yakuman"),

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

    /** 「不算役」標籤的說明。 */
    const val BONUS_ONLY_DESCRIPTION: String = PREFIX + "label.bonus_only.description"

    /** 「門清限定」標籤的說明。 */
    const val CLOSED_ONLY_DESCRIPTION: String = PREFIX + "label.closed_only.description"

    /** 副露翻數標籤共用的說明。 */
    const val OPEN_HAN_DESCRIPTION: String = PREFIX + "label.open_han.description"

    /** 三元牌役牌的總稱，不以紅中名稱代替。 */
    const val DRAGON_NAME: String = PREFIX + "name.dragon"

    /** 本配置沒有赤寶牌的提示。 */
    const val RED_DORA_UNAVAILABLE: String = PREFIX + "unavailable.red_dora"
}

/**
 * 內建役種與目錄的顯式對照；翻譯尾段亦作為穩定條目尾段。
 *
 * @property type 對應的現行役種。
 * @property slug 固定條目與翻譯尾段。
 * @property category 所屬說明分類。
 * @property valueKey 門清價值或加算標籤。
 * @property conditionKey 副露價值或門清限制標籤；null 表示沒有額外標籤。
 */
internal enum class RiichiCatalogueYaku(
    val type: YakuType,
    private val slug: String,
    val category: RiichiCatalogueCategory,
    val valueKey: String,
    val conditionKey: String? = null,
) {
    /** Dora 的說明對照。 */
    Dora(YakuType.Dora, "dora", RiichiCatalogueCategory.BONUS, RiichiCatalogueKeys.BONUS_ONLY),

    /** UraDora 的說明對照。 */
    UraDora(YakuType.UraDora, "uradora", RiichiCatalogueCategory.BONUS, RiichiCatalogueKeys.BONUS_ONLY),

    /** AkaDora 的說明對照。 */
    AkaDora(YakuType.AkaDora, "red_five", RiichiCatalogueCategory.BONUS, RiichiCatalogueKeys.BONUS_ONLY),

    /** Tanyao 的說明對照。 */
    Tanyao(YakuType.Tanyao, "tanyao", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Pinfu 的說明對照。 */
    Pinfu(YakuType.Pinfu, "pinfu", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Iipeikou 的說明對照。 */
    Iipeikou(YakuType.Iipeikou, "ipeiko", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Riichi 的說明對照。 */
    Riichi(YakuType.Riichi, "reach", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** DoubleRiichi 的說明對照。 */
    DoubleRiichi(YakuType.DoubleRiichi, "double_reach", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Ippatsu 的說明對照。 */
    Ippatsu(YakuType.Ippatsu, "ippatsu", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** RinshanKaihou 的說明對照。 */
    RinshanKaihou(YakuType.RinshanKaihou, "rinshankaihoh", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Haitei 的說明對照。 */
    Haitei(YakuType.Haitei, "haitei", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Houtei 的說明對照。 */
    Houtei(YakuType.Houtei, "houtei", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Chankan 的說明對照。 */
    Chankan(YakuType.Chankan, "chankan", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Menzentsumo 的說明對照。 */
    Menzentsumo(YakuType.Menzentsumo, "tsumo", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Toitoi 的說明對照。 */
    Toitoi(YakuType.Toitoi, "toitoiho", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Sanankou 的說明對照。 */
    Sanankou(YakuType.Sanankou, "sananko", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Sankantsu 的說明對照。 */
    Sankantsu(YakuType.Sankantsu, "sankantsu", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** SanshokuDokoku 的說明對照。 */
    SanshokuDokoku(YakuType.SanshokuDokoku, "sanshokudohko", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** SanshokuDoujun 的說明對照。 */
    SanshokuDoujun(YakuType.SanshokuDoujun, "sanshokudohjun", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Honchan 的說明對照。 */
    Honchan(YakuType.Honchan, "chanta", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Junchan 的說明對照。 */
    Junchan(YakuType.Junchan, "junchan", RiichiCatalogueCategory.HAN_3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** Honitsu 的說明對照。 */
    Honitsu(YakuType.Honitsu, "honitsu", RiichiCatalogueCategory.HAN_3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.OPEN_HAN_2),

    /** Ryanpeikou 的說明對照。 */
    Ryanpeikou(YakuType.Ryanpeikou, "ryanpeiko", RiichiCatalogueCategory.HAN_3, RiichiCatalogueKeys.HAN_3, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Ittuitsu 的說明對照。 */
    Ittuitsu(YakuType.Ittuitsu, "ikkitsukan", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.OPEN_HAN_1),

    /** Honroutou 的說明對照。 */
    Honroutou(YakuType.Honroutou, "honrohtoh", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Chinitsu 的說明對照。 */
    Chinitsu(YakuType.Chinitsu, "chinitsu", RiichiCatalogueCategory.HAN_6, RiichiCatalogueKeys.HAN_6, RiichiCatalogueKeys.OPEN_HAN_5),

    /** Shousangen 的說明對照。 */
    Shousangen(YakuType.Shousangen, "shosangen", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2),

    /** Chiitoitsu 的說明對照。 */
    Chiitoitsu(YakuType.Chiitoitsu, "chitoitsu", RiichiCatalogueCategory.HAN_2, RiichiCatalogueKeys.HAN_2, RiichiCatalogueKeys.CLOSED_ONLY),

    /** RoundWind 的說明對照。 */
    RoundWind(YakuType.RoundWind, "bakaze", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** SeatWind 的說明對照。 */
    SeatWind(YakuType.SeatWind, "jikaze", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** Dragon 的說明對照。 */
    Dragon(YakuType.Dragon, "chun", RiichiCatalogueCategory.HAN_1, RiichiCatalogueKeys.HAN_1),

    /** KokushiMusou 的說明對照。 */
    KokushiMusou(YakuType.KokushiMusou, "kokushimuso", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** ChurenPoto 的說明對照。 */
    ChurenPoto(YakuType.ChurenPoto, "churenpohto", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Tsuuiisou 的說明對照。 */
    Tsuuiisou(YakuType.Tsuuiisou, "tsuiso", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Ryuuuiisou 的說明對照。 */
    Ryuuuiisou(YakuType.Ryuuuiisou, "ryuiso", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Suuankou 的說明對照。 */
    Suuankou(YakuType.Suuankou, "suanko", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Sukantsu 的說明對照。 */
    Sukantsu(YakuType.Sukantsu, "sukantsu", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Shousuushi 的說明對照。 */
    Shousuushi(YakuType.Shousuushi, "shosushi", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Daisangen 的說明對照。 */
    Daisangen(YakuType.Daisangen, "daisangen", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Chinroutou 的說明對照。 */
    Chinroutou(YakuType.Chinroutou, "chinroto", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN),

    /** Tenhou 的說明對照。 */
    Tenhou(YakuType.Tenhou, "tenho", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Chiihou 的說明對照。 */
    Chiihou(YakuType.Chiihou, "chiho", RiichiCatalogueCategory.YAKUMAN, RiichiCatalogueKeys.YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** KokushiMusou13 的說明對照。 */
    KokushiMusou13(YakuType.KokushiMusou13, "kokushimuso_jusanmenmachi", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** ChurenPoto9 的說明對照。 */
    ChurenPoto9(YakuType.ChurenPoto9, "junsei_churenpohto", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** SuuankouTanki 的說明對照。 */
    SuuankouTanki(YakuType.SuuankouTanki, "suanko_tanki", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN, RiichiCatalogueKeys.CLOSED_ONLY),

    /** Daisuushii 的說明對照。 */
    Daisuushii(YakuType.Daisuushii, "daisushi", RiichiCatalogueCategory.DOUBLE_YAKUMAN, RiichiCatalogueKeys.DOUBLE_YAKUMAN),
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
