package com.doublemoon1119.mahjongcraft.platform.minecraft.ruler

/**
 * 牌尺的顏色：無色加上 16 種染料色，每種顏色是一個獨立的物品。
 *
 * 排列順序就是創造模式物品欄的順序，染料色比照原版染色玻璃的排法。
 *
 * @property dyeName 對應的原版染料顏色名稱；無色為 null。
 */
enum class MahjongRulerColor(val dyeName: String?) {
    CLEAR(null),
    WHITE("white"),
    LIGHT_GRAY("light_gray"),
    GRAY("gray"),
    BLACK("black"),
    BROWN("brown"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    LIME("lime"),
    GREEN("green"),
    CYAN("cyan"),
    LIGHT_BLUE("light_blue"),
    BLUE("blue"),
    PURPLE("purple"),
    MAGENTA("magenta"),
    PINK("pink"),
    ;

    /** 這個顏色的牌尺物品註冊路徑，例如無色為 `mahjong_ruler`、紅色為 `red_mahjong_ruler`。 */
    val itemPath: String
        get() = dyeName?.let { "${it}_$BASE_ITEM_PATH" } ?: BASE_ITEM_PATH

    companion object {
        /** 無色牌尺的物品註冊路徑，也是牌尺實體的註冊路徑。 */
        const val BASE_ITEM_PATH: String = "mahjong_ruler"

        /**
         * 依列舉名稱取得顏色。
         *
         * @param name 列舉名稱，例如 `RED`。
         * @return 對應的顏色；名稱不存在時為無色。
         */
        fun fromNameOrDefault(name: String): MahjongRulerColor = entries.firstOrNull { it.name == name } ?: CLEAR
    }
}
