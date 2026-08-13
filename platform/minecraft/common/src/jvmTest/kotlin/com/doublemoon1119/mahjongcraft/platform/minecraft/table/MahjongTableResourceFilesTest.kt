package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證兩種首發麻將桌的配方與材料 tag 資源。 */
class MahjongTableResourceFilesTest {
    /** Vanilla 1.20.1 提供的全部混凝土 item ID。 */
    private val concreteIds = setOf(
        "minecraft:white_concrete",
        "minecraft:orange_concrete",
        "minecraft:magenta_concrete",
        "minecraft:light_blue_concrete",
        "minecraft:yellow_concrete",
        "minecraft:lime_concrete",
        "minecraft:pink_concrete",
        "minecraft:gray_concrete",
        "minecraft:light_gray_concrete",
        "minecraft:cyan_concrete",
        "minecraft:purple_concrete",
        "minecraft:blue_concrete",
        "minecraft:brown_concrete",
        "minecraft:green_concrete",
        "minecraft:red_concrete",
        "minecraft:black_concrete",
    )

    /** 驗證木製款接受所有 planks，且輸出固定木製麻將桌。 */
    @Test
    fun `wooden table recipe uses the vanilla planks tag`() {
        val recipe = loadJson("/data/mahjongcraft/recipes/wooden_mahjong_table.json")

        assertEquals("minecraft:planks", recipe.keyEntry("P")["tag"]?.jsonPrimitive?.content)
        assertEquals("minecraft:wool_carpets", recipe.keyEntry("C")["tag"]?.jsonPrimitive?.content)
        assertEquals("mahjongcraft:wooden_mahjong_table", recipe.resultItem())
    }

    /** 驗證混凝土款使用專用混凝土 tag，且輸出固定混凝土麻將桌。 */
    @Test
    fun `concrete table recipe uses the MahjongCraft concretes tag`() {
        val recipe = loadJson("/data/mahjongcraft/recipes/concrete_mahjong_table.json")

        assertEquals("mahjongcraft:concretes", recipe.keyEntry("M")["tag"]?.jsonPrimitive?.content)
        assertEquals("minecraft:wool_carpets", recipe.keyEntry("C")["tag"]?.jsonPrimitive?.content)
        assertEquals("mahjongcraft:concrete_mahjong_table", recipe.resultItem())
    }

    /** 驗證專用 tag 完整列出 16 種 Vanilla 混凝土。 */
    @Test
    fun `concretes tag contains every vanilla concrete color`() {
        val tag = loadJson("/data/mahjongcraft/tags/items/concretes.json")
        val values = assertIs<JsonArray>(tag["values"]).mapTo(mutableSetOf()) {
            assertIs<JsonPrimitive>(it).content
        }

        assertEquals(concreteIds, values)
    }

    /** 讀取配方指定符號的 ingredient 物件。 */
    private fun JsonObject.keyEntry(symbol: String): JsonObject = getValue("key").jsonObject.getValue(symbol).jsonObject

    /** 讀取配方輸出的 item ID。 */
    private fun JsonObject.resultItem(): String = getValue("result").jsonObject.getValue("item").jsonPrimitive.content

    /** 從測試 classpath 載入並解析指定 JSON object。 */
    private fun loadJson(resourcePath: String): JsonObject {
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }

        return Json.parseToJsonElement(content).jsonObject
    }
}
