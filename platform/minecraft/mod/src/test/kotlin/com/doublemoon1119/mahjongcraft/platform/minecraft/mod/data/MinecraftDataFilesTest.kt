package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.data

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.DataPackLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證這個 Minecraft 版本的資料包檔案：配方、掉落表與物品標籤。這些檔案的格式與資料夾名稱會隨 Minecraft 版本改變。 */
class MinecraftDataFilesTest {
    /** 驗證無形配方使用三種既定材料並產出兩顆骰子。 */
    @Test
    fun `dice recipe combines concrete and pip dyes`() {
        val recipe = loadJson("/${DataPackLayout.RECIPES}/mahjong_dice.json")
        val ingredients = recipe.getValue("ingredients").jsonArray.mapTo(mutableSetOf()) { ingredient ->
            ingredient.jsonObject.getValue("item").jsonPrimitive.content
        }
        val result = recipe.getValue("result").jsonObject

        assertEquals("minecraft:crafting_shapeless", recipe.getValue("type").jsonPrimitive.content)
        assertEquals(
            expected = setOf("minecraft:white_concrete", "minecraft:red_dye", "minecraft:black_dye"),
            actual = ingredients,
        )
        assertEquals("mahjongcraft:mahjong_dice", result.getValue(DataPackLayout.RECIPE_RESULT_ITEM).jsonPrimitive.content)
        assertEquals(2, result.getValue("count").jsonPrimitive.content.toInt())
    }

    /** 驗證兩款掉落表各自只掉落一個對應 BlockItem。 */
    @Test
    fun `stool loot tables drop their matching block items`() {
        listOf("wooden", "plastic").forEach { style ->
            val id = "${style}_mahjong_stool"
            val lootTable = loadJson("/${DataPackLayout.LOOT_TABLES}/blocks/$id.json")
            val entry = lootTable.getValue("pools").jsonArray.single().jsonObject
                .getValue("entries").jsonArray.single().jsonObject

            assertEquals("mahjongcraft:$id", entry.getValue("name").jsonPrimitive.content)
        }
    }

    /** 驗證木製款接受任意木製半磚與柵欄，並一次產出兩張凳子。 */
    @Test
    fun `wooden stool recipe uses vanilla wooden material tags`() {
        val recipe = loadJson("/${DataPackLayout.RECIPES}/wooden_mahjong_stool.json")

        assertEquals("minecraft:crafting_shaped", recipe.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("SSS", "F F"), recipe.getValue("pattern").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("minecraft:wooden_slabs", recipe.keyEntry("S").getValue("tag").jsonPrimitive.content)
        assertEquals("minecraft:wooden_fences", recipe.keyEntry("F").getValue("tag").jsonPrimitive.content)
        assertEquals("mahjongcraft:wooden_mahjong_stool", recipe.resultItem())
        assertEquals(2, recipe.resultCount())
    }

    /** 驗證塑膠款可由紅色染料與鷹架任意擺放合成。 */
    @Test
    fun `plastic stool recipe combines red dye and scaffolding`() {
        val recipe = loadJson("/${DataPackLayout.RECIPES}/plastic_mahjong_stool.json")
        val ingredients = recipe.getValue("ingredients").jsonArray.mapTo(mutableSetOf()) { ingredient ->
            ingredient.jsonObject.getValue("item").jsonPrimitive.content
        }

        assertEquals("minecraft:crafting_shapeless", recipe.getValue("type").jsonPrimitive.content)
        assertEquals(setOf("minecraft:red_dye", "minecraft:scaffolding"), ingredients)
        assertEquals("mahjongcraft:plastic_mahjong_stool", recipe.resultItem())
        assertEquals(1, recipe.resultCount())
    }

    /**
     * 讀取有形配方指定符號的 ingredient。
     *
     * @param symbol 配方 key 中的符號。
     * @return 對應的 ingredient JSON object。
     */
    private fun JsonObject.keyEntry(symbol: String): JsonObject = getValue("key").jsonObject
        .getValue(symbol).jsonObject

    /**
     * 讀取配方輸出的物品 ID。
     *
     * @return recipe result 中的 item ID。
     */
    private fun JsonObject.resultItem(): String = getValue("result").jsonObject
        .getValue(DataPackLayout.RECIPE_RESULT_ITEM).jsonPrimitive.content

    /**
     * 讀取配方輸出的物品數量。
     *
     * @return recipe result 中的物品數量。
     */
    private fun JsonObject.resultCount(): Int = getValue("result").jsonObject
        .getValue("count").jsonPrimitive.content.toInt()

    /** 驗證麻將牌配方使用這個版本的染料標籤、牌背接受綠色與黃綠色混凝土，並一次產出 32 張。 */
    @Test
    fun `tile recipe uses the version dye tag and produces thirty two tiles`() {
        val recipe = loadJson("/${DataPackLayout.RECIPES}/mahjong_tile.json")
        val key = recipe.getValue("key").jsonObject
        val result = recipe.getValue("result").jsonObject

        assertEquals(listOf(" WG", "DWG", " WG"), assertIs<JsonArray>(recipe["pattern"]).map { it.jsonPrimitive.content })
        assertEquals("minecraft:white_concrete", key.getValue("W").jsonObject.getValue("item").jsonPrimitive.content)
        assertEquals(
            expected = listOf("minecraft:green_concrete", "minecraft:lime_concrete"),
            actual = key.getValue("G").jsonArray.map { it.jsonObject.getValue("item").jsonPrimitive.content },
        )
        assertEquals(DataPackLayout.DYE_TAG, key.getValue("D").jsonObject.getValue("tag").jsonPrimitive.content)
        assertEquals("mahjongcraft:mahjong_tile", result.getValue(DataPackLayout.RECIPE_RESULT_ITEM).jsonPrimitive.content)
        assertEquals(32, result.getValue("count").jsonPrimitive.content.toInt())
    }

    /**
     * 從測試 classpath 載入並解析指定 JSON object。
     *
     * @param resourcePath classpath 資源路徑。
     * @return 解析後的 JSON object。
     */
    private fun loadJson(resourcePath: String): JsonObject {
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }

        return Json.parseToJsonElement(content).jsonObject
    }
}
