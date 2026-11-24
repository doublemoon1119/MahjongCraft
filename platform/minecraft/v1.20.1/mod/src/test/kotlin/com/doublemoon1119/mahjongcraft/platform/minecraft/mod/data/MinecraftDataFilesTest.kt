package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證這個 Minecraft 版本的資料包檔案：配方、掉落表與物品標籤。這些檔案的格式與資料夾名稱會隨 Minecraft 版本改變。 */
class MinecraftDataFilesTest {
    /** Vanilla 1.20.1 提供的全部染料 item ID。 */
    private val vanillaDyeIds = setOf(
        "minecraft:white_dye",
        "minecraft:orange_dye",
        "minecraft:magenta_dye",
        "minecraft:light_blue_dye",
        "minecraft:yellow_dye",
        "minecraft:lime_dye",
        "minecraft:pink_dye",
        "minecraft:gray_dye",
        "minecraft:light_gray_dye",
        "minecraft:cyan_dye",
        "minecraft:purple_dye",
        "minecraft:blue_dye",
        "minecraft:brown_dye",
        "minecraft:green_dye",
        "minecraft:red_dye",
        "minecraft:black_dye",
    )

    /** 驗證無形配方使用三種既定材料並產出兩顆骰子。 */
    @Test
    fun `dice recipe combines concrete and pip dyes`() {
        val recipe = loadJson("/data/mahjongcraft/recipes/mahjong_dice.json")
        val ingredients = recipe.getValue("ingredients").jsonArray.mapTo(mutableSetOf()) { ingredient ->
            ingredient.jsonObject.getValue("item").jsonPrimitive.content
        }
        val result = recipe.getValue("result").jsonObject

        assertEquals("minecraft:crafting_shapeless", recipe.getValue("type").jsonPrimitive.content)
        assertEquals(
            expected = setOf("minecraft:white_concrete", "minecraft:red_dye", "minecraft:black_dye"),
            actual = ingredients,
        )
        assertEquals("mahjongcraft:mahjong_dice", result.getValue("item").jsonPrimitive.content)
        assertEquals(2, result.getValue("count").jsonPrimitive.content.toInt())
    }

    /** 驗證兩款掉落表各自只掉落一個對應 BlockItem。 */
    @Test
    fun `stool loot tables drop their matching block items`() {
        listOf("wooden", "plastic").forEach { style ->
            val id = "${style}_mahjong_stool"
            val lootTable = loadJson("/data/mahjongcraft/loot_tables/blocks/$id.json")
            val entry = lootTable.getValue("pools").jsonArray.single().jsonObject
                .getValue("entries").jsonArray.single().jsonObject

            assertEquals("mahjongcraft:$id", entry.getValue("name").jsonPrimitive.content)
        }
    }

    /** 驗證木製款接受任意木製半磚與柵欄，並一次產出兩張凳子。 */
    @Test
    fun `wooden stool recipe uses vanilla wooden material tags`() {
        val recipe = loadJson("/data/mahjongcraft/recipes/wooden_mahjong_stool.json")

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
        val recipe = loadJson("/data/mahjongcraft/recipes/plastic_mahjong_stool.json")
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
        .getValue("item").jsonPrimitive.content

    /**
     * 讀取配方輸出的物品數量。
     *
     * @return recipe result 中的物品數量。
     */
    private fun JsonObject.resultCount(): Int = getValue("result").jsonObject
        .getValue("count").jsonPrimitive.content.toInt()

    /** 驗證麻將牌配方使用穩定染料 tag、固定牌體材料，並一次產出 32 張。 */
    @Test
    fun `tile recipe uses the portable dye tag and produces thirty two tiles`() {
        val recipe = loadJson("/data/mahjongcraft/recipes/mahjong_tile.json")
        val key = recipe.getValue("key").jsonObject
        val result = recipe.getValue("result").jsonObject

        assertEquals(listOf(" WG", "DWG", " WG"), assertIs<JsonArray>(recipe["pattern"]).map { it.jsonPrimitive.content })
        assertEquals("minecraft:white_concrete", key.getValue("W").jsonObject.getValue("item").jsonPrimitive.content)
        assertEquals(
            expected = "mahjongcraft:mahjong_tile_back_concretes",
            actual = key.getValue("G").jsonObject.getValue("tag").jsonPrimitive.content,
        )
        assertEquals("mahjongcraft:dyes", key.getValue("D").jsonObject.getValue("tag").jsonPrimitive.content)
        assertEquals("mahjongcraft:mahjong_tile", result.getValue("item").jsonPrimitive.content)
        assertEquals(32, result.getValue("count").jsonPrimitive.content.toInt())
    }

    /** 驗證跨 loader fallback tag 完整列出 16 種 Vanilla 染料。 */
    @Test
    fun `vanilla dyes tag contains every vanilla dye color`() {
        val tag = loadJson("/data/mahjongcraft/tags/items/vanilla_dyes.json")
        val values = assertIs<JsonArray>(tag["values"]).mapTo(mutableSetOf()) {
            assertIs<JsonPrimitive>(it).content
        }

        assertEquals(vanillaDyeIds, values)
    }

    /** 驗證牌背材料同時接受綠色與淺綠色混凝土。 */
    @Test
    fun `tile back concretes tag contains green and lime concrete`() {
        val tag = loadJson("/data/mahjongcraft/tags/items/mahjong_tile_back_concretes.json")
        val values = assertIs<JsonArray>(tag["values"]).mapTo(mutableSetOf()) {
            assertIs<JsonPrimitive>(it).content
        }

        assertEquals(setOf("minecraft:green_concrete", "minecraft:lime_concrete"), values)
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
