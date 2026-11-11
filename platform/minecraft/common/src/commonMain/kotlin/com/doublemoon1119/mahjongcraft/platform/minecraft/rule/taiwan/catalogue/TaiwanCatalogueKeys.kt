package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將圖鑑使用的翻譯鍵與分類識別碼。 */
internal object TaiwanCatalogueKeys {
    /** 圖鑑翻譯鍵前綴。 */
    const val PREFIX: String = "mahjongcraft.rule_catalogue.taiwan."

    /** 花牌停用時顯示的原因。 */
    const val FLOWERS_UNAVAILABLE: String = PREFIX + "unavailable.flowers"

    /** 四風連打停用時顯示的原因。 */
    const val FOUR_WINDS_UNAVAILABLE: String = PREFIX + "unavailable.four_winds"

    /** 門清牌例的情境說明。 */
    const val CONCEALED_HAND_EXAMPLE: String = PREFIX + "example.concealed_hand"

    /** 自摸牌例的情境說明。 */
    const val SELF_DRAW_EXAMPLE: String = PREFIX + "example.self_draw"

    /** 門清自摸牌例的情境說明。 */
    const val FULLY_CONCEALED_HAND_EXAMPLE: String = PREFIX + "example.fully_concealed_hand"

    /** 門風牌例的情境說明。 */
    const val SEAT_WIND_EXAMPLE: String = PREFIX + "example.seat_wind"

    /** 圈風牌例的情境說明。 */
    const val ROUND_WIND_EXAMPLE: String = PREFIX + "example.round_wind"

    /** 正花牌例的情境說明。 */
    const val SEAT_FLOWER_EXAMPLE: String = PREFIX + "example.seat_flower"

    /** 獨聽牌例的情境說明。 */
    const val SINGLE_WAIT_EXAMPLE: String = PREFIX + "example.single_wait"

    /** 搶槓牌例的情境說明。 */
    const val ROBBING_KONG_EXAMPLE: String = PREFIX + "example.robbing_kong"

    /** 槓上開花牌例的情境說明。 */
    const val REPLACEMENT_WIN_EXAMPLE: String = PREFIX + "example.replacement_win"

    /** 海底撈月牌例的情境說明。 */
    const val LAST_DRAW_EXAMPLE: String = PREFIX + "example.last_draw"

    /** 宣告聽牌牌例的情境說明。 */
    const val DECLARED_READY_EXAMPLE: String = PREFIX + "example.declared_ready"

    /** 天聽牌例的情境說明。 */
    const val HEAVENLY_READY_EXAMPLE: String = PREFIX + "example.heavenly_ready"

    /** 地聽牌例的情境說明。 */
    const val EARTHLY_READY_EXAMPLE: String = PREFIX + "example.earthly_ready"

    /** 人胡牌例的情境說明。 */
    const val HUMAN_WIN_EXAMPLE: String = PREFIX + "example.human_win"

    /** 地胡牌例的情境說明。 */
    const val EARTHLY_WIN_EXAMPLE: String = PREFIX + "example.earthly_win"

    /** 天胡牌例的情境說明。 */
    const val HEAVENLY_WIN_EXAMPLE: String = PREFIX + "example.heavenly_win"
}

/** 台灣麻將圖鑑依台數劃分的主要分類。 */
internal enum class TaiwanCatalogueCategory(
    private val slug: String,
) {
    /** 一台項目。 */
    TAI_1("tai_1"),

    /** 兩台項目。 */
    TAI_2("tai_2"),

    /** 四台項目。 */
    TAI_4("tai_4"),

    /** 五台項目。 */
    TAI_5("tai_5"),

    /** 八台項目。 */
    TAI_8("tai_8"),

    /** 十六台項目。 */
    TAI_16("tai_16"),

    /** 二十四台項目。 */
    TAI_24("tai_24"),

    /** 一般與途中流局。 */
    DRAW("draw"),
    ;

    /** namespaced 分類識別碼。 */
    val id: String get() = MahjongCraftMetadata.id("catalogue/taiwan/category/$slug")

    /** 分類名稱翻譯鍵。 */
    val nameKey: String get() = TaiwanCatalogueKeys.PREFIX + "category.$slug"
}
