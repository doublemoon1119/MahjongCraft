package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 驗證麻將骰子的 item model、材質、配方與語系資源。 */
class MahjongDiceResourceFilesTest {
    /** 驗證舊版骰子尺寸、六面 UV 及材質路徑完整移植。 */
    @Test
    fun `dice model preserves the validated cube and face layout`() {
        val model = loadJson("/assets/mahjongcraft/models/item/mahjong_dice.json")
        val element = model.getValue("elements").jsonArray.single().jsonObject
        val faces = element.getValue("faces").jsonObject

        assertEquals(listOf(3.0, 0.0, 3.0), element.coordinates("from"))
        assertEquals(listOf(13.0, 10.0, 13.0), element.coordinates("to"))
        assertEquals(setOf("down", "up", "north", "south", "west", "east"), faces.keys)
        assertEquals("mahjongcraft:item/dice/dice", model.texture("dice"))
    }

    /** 驗證骰子材質維持 main branch 的 32 平方像素。 */
    @Test
    fun `dice texture has the validated dimensions`() {
        val resourcePath = "/assets/mahjongcraft/textures/item/dice/dice.png"
        val image = javaClass.getResourceAsStream(resourcePath)?.use(ImageIO::read)

        assertNotNull(image, "Resource is not a readable image: $resourcePath")
        assertEquals(32, image.width)
        assertEquals(32, image.height)
    }

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
            setOf("minecraft:white_concrete", "minecraft:red_dye", "minecraft:black_dye"),
            ingredients,
        )
        assertEquals("mahjongcraft:mahjong_dice", result.getValue("item").jsonPrimitive.content)
        assertEquals(2, result.getValue("count").jsonPrimitive.content.toInt())
    }

    /** 讀取模型的指定材質路徑。 */
    private fun JsonObject.texture(name: String): String = getValue("textures").jsonObject
        .getValue(name).jsonPrimitive.content

    /** 讀取模型 element 的小數座標。 */
    private fun JsonObject.coordinates(name: String): List<Double> = getValue(name).jsonArray.decimalCoordinates()

    /** 將 JSON 座標陣列轉為小數清單。 */
    private fun JsonArray.decimalCoordinates(): List<Double> = map { it.jsonPrimitive.content.toDouble() }

    /** 從測試 classpath 載入並解析指定 JSON object。 */
    private fun loadJson(resourcePath: String): JsonObject {
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }

        return Json.parseToJsonElement(content).jsonObject
    }
}
