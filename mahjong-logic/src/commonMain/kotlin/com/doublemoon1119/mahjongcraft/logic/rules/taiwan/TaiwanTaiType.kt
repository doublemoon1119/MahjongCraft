package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 台灣麻將預設台表的計台項目，莊家與連莊由付款另計。
 *
 * @property path 台麻內的項目路徑。
 * @property tai 每次成立的台數。
 */
enum class TaiwanTaiType(private val path: String, val tai: Int) {
    /** 門清。 */
    CONCEALED_HAND("concealed_hand", 1),

    /** 自摸。 */
    SELF_DRAW("self_draw", 1),

    /** 門清自摸額外的一台。 */
    FULLY_CONCEALED_HAND("fully_concealed_hand", 1),

    /** 門風台。 */
    SEAT_WIND("seat_wind", 1),

    /** 圈風台。 */
    ROUND_WIND("round_wind", 1),

    /** 三元台。 */
    DRAGON("dragon", 1),

    /** 正花。 */
    SEAT_FLOWER("seat_flower", 1),

    /** 花槓。 */
    FLOWER_SET("flower_set", 1),

    /** 獨聽，包含符合條件的單吊、中洞及邊張。 */
    SINGLE_WAIT("single_wait", 1),

    /** 搶槓。 */
    ROBBING_KONG("robbing_kong", 1),

    /** 槓上開花。 */
    REPLACEMENT_WIN("replacement_win", 1),

    /** 海底撈月。 */
    LAST_DRAW("last_draw", 1),

    /** 平胡。 */
    ALL_SEQUENCES("all_sequences", 2),

    /** 三暗刻。 */
    THREE_CONCEALED_TRIPLETS("three_concealed_triplets", 2),

    /** 四暗刻。 */
    FOUR_CONCEALED_TRIPLETS("four_concealed_triplets", 5),

    /** 五暗刻。 */
    FIVE_CONCEALED_TRIPLETS("five_concealed_triplets", 8),

    /** 全求人。 */
    ALL_EXPOSED("all_exposed", 2),

    /** 碰碰胡。 */
    ALL_TRIPLETS("all_triplets", 4),

    /** 小三元。 */
    LITTLE_DRAGONS("little_dragons", 4),

    /** 大三元。 */
    BIG_DRAGONS("big_dragons", 8),

    /** 混一色，也稱湊一色。 */
    HALF_FLUSH("half_flush", 4),

    /** 清一色。 */
    FULL_FLUSH("full_flush", 8),

    /** 小四喜。 */
    LITTLE_WINDS("little_winds", 8),

    /** 大四喜。 */
    BIG_WINDS("big_winds", 16),

    /** 字一色。 */
    ALL_HONORS("all_honors", 16),

    /** 宣告聽牌後胡牌。 */
    DECLARED_READY("declared_ready", 1),

    /** 莊家第一打後已聽牌的資格。 */
    HEAVENLY_READY("heavenly_ready", 16),

    /** 地聽資格。 */
    EARTHLY_READY("earthly_ready", 8),

    /** 人胡。 */
    HUMAN_WIN("human_win", 16),

    /** 地胡。 */
    EARTHLY_WIN("earthly_win", 16),

    /** 天胡。 */
    HEAVENLY_WIN("heavenly_win", 24),

    /** 八仙過海的獨立花胡。 */
    EIGHT_FLOWERS("eight_flowers", 8),

    /** 七搶一的獨立花胡。 */
    SEVEN_FLOWERS("seven_flowers", 8),
    ;

    /** 計台項目的完整識別碼。 */
    val id: String get() = MahjongCraftMetadata.id("taiwan/tai/$path")
}
