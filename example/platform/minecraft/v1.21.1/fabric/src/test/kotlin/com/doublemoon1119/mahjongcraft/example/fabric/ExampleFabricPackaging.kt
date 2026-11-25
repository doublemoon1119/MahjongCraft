package com.doublemoon1119.mahjongcraft.example.fabric

/** 這個 Minecraft 版本的範例 mod 打包後應有的內容，供共用的打包測試比對。 */
object ExampleFabricPackaging {
    /** 方塊戰利品表在 JAR 中的資料夾。 */
    const val BLOCK_LOOT_TABLES: String = "data/mahjongcraft_example/loot_table/blocks"

    /** 兩份 mod 的 `fabric.mod.json` 應宣告的執行依賴。 */
    val DEPENDENCY_REQUIREMENTS: Map<String, String> = mapOf(
        "fabricloader" to ">=0.19.0",
        "fabric" to "*",
        "minecraft" to "~1.21.1",
        "fabric-language-kotlin" to ">=1.13.14",
    )
}
