package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

/** 這個 Minecraft 版本內建資料包的資料夾與欄位名稱，供讀取資料包檔案的測試使用。 */
object DataPackLayout {
    /** 配方資料夾。 */
    const val RECIPES: String = "data/mahjongcraft/recipe"

    /** 進度資料夾。 */
    const val ADVANCEMENTS: String = "data/mahjongcraft/advancement"

    /** 戰利品表資料夾。 */
    const val LOOT_TABLES: String = "data/mahjongcraft/loot_table"

    /** 物品標籤資料夾。 */
    const val ITEM_TAGS: String = "data/mahjongcraft/tags/item"

    /** 配方成品中物品 ID 的欄位名稱。 */
    const val RECIPE_RESULT_ITEM: String = "id"

    /** 麻將牌配方使用的染料標籤。 */
    const val DYE_TAG: String = "c:dyes"

    /** 混凝土麻將桌配方使用的混凝土標籤。 */
    const val CONCRETE_TAG: String = "c:concretes"
}
