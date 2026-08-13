package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 驗證麻將骰子的 item model、材質、配方與語系資源。 */
class MahjongDiceResourceFilesTest {
    /** 驗證骰子尺寸、六面 UV、材質路徑及每面 16×16 的 atlas 比例。 */
    @Test
    fun `dice model preserves the validated cube and face layout`() {
        val model = loadJson("/assets/mahjongcraft/models/item/mahjong_dice.json")
        val element = model.getValue("elements").jsonArray.single().jsonObject
        val faces = element.getValue("faces").jsonObject

        assertEquals(listOf(3.0, 0.0, 3.0), element.coordinates("from"))
        assertEquals(listOf(13.0, 10.0, 13.0), element.coordinates("to"))
        assertEquals(setOf("down", "up", "north", "south", "west", "east"), faces.keys)
        assertEquals("mahjongcraft:item/dice/dice", model.texture("dice"))
        assertEquals(listOf(64.0, 64.0), model.coordinates("texture_size"))
        assertEquals(listOf(0.0, 0.0, 4.0, 4.0), faces.faceUv("north"))
        assertEquals(listOf(4.0, 0.0, 8.0, 4.0), faces.faceUv("south"))
        assertEquals(listOf(0.0, 4.0, 4.0, 8.0), faces.faceUv("east"))
        assertEquals(listOf(4.0, 4.0, 8.0, 8.0), faces.faceUv("west"))
        assertEquals(listOf(4.0, 12.0, 0.0, 8.0), faces.faceUv("up"))
        assertEquals(listOf(12.0, 0.0, 8.0, 4.0), faces.faceUv("down"))
        assertEquals(
            listOf(30.0, -135.0, 0.0),
            model.getValue("display").jsonObject.getValue("gui").jsonObject.coordinates("rotation"),
        )
        val fixedDisplay = model.getValue("display").jsonObject.getValue("fixed").jsonObject
        assertEquals(listOf(-90.0, 0.0, 0.0), fixedDisplay.coordinates("rotation"))
        assertEquals(listOf(0.0, 0.0, -3.0), fixedDisplay.coordinates("translation"))
    }

    /** 驗證六面共用的 atlas 提供每面 16×16 有效像素。 */
    @Test
    fun `dice texture has the validated dimensions`() {
        val resourcePath = "/assets/mahjongcraft/textures/item/dice/dice.png"
        val image = javaClass.getResourceAsStream(resourcePath)?.use(ImageIO::read)

        assertNotNull(image, "Resource is not a readable image: $resourcePath")
        assertEquals(64, image.width)
        assertEquals(64, image.height)
    }

    /** 驗證六個 atlas 區塊包含正確數量且彼此分離的點數。 */
    @Test
    fun `dice texture preserves every physical face value`() {
        val resourcePath = "/assets/mahjongcraft/textures/item/dice/dice.png"
        val image = javaClass.getResourceAsStream(resourcePath)?.use(ImageIO::read)

        assertNotNull(image, "Resource is not a readable image: $resourcePath")
        assertEquals(
            mapOf(2 to 2, 5 to 5, 3 to 3, 4 to 4, 1 to 1, 6 to 6),
            mapOf(
                2 to image.countPips(0, 0),
                5 to image.countPips(16, 0),
                3 to image.countPips(0, 16),
                4 to image.countPips(16, 16),
                1 to image.countPips(0, 32),
                6 to image.countPips(32, 0),
            ),
        )
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

    /** 讀取指定模型面的 UV 座標。 */
    private fun JsonObject.faceUv(name: String): List<Double> = getValue(name).jsonObject.coordinates("uv")

    /** 將 JSON 座標陣列轉為小數清單。 */
    private fun JsonArray.decimalCoordinates(): List<Double> = map { it.jsonPrimitive.content.toDouble() }

    /** 計算指定 16×16 面內互不相連的點數圖案數量。 */
    private fun BufferedImage.countPips(originX: Int, originY: Int): Int {
        val visited = Array(16) { BooleanArray(16) }
        var components = 0

        for (y in 0 until 16) {
            for (x in 0 until 16) {
                if (visited[y][x] || !isPipPixel(getRGB(originX + x, originY + y))) continue
                components++
                val pending = ArrayDeque<Pair<Int, Int>>()
                pending.add(x to y)
                visited[y][x] = true
                while (pending.isNotEmpty()) {
                    val (currentX, currentY) = pending.removeFirst()
                    listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (offsetX, offsetY) ->
                        val nextX = currentX + offsetX
                        val nextY = currentY + offsetY
                        if (nextX !in 0 until 16 || nextY !in 0 until 16 || visited[nextY][nextX]) return@forEach
                        if (!isPipPixel(getRGB(originX + nextX, originY + nextY))) return@forEach
                        visited[nextY][nextX] = true
                        pending.add(nextX to nextY)
                    }
                }
            }
        }
        return components
    }

    /** 判斷像素是否屬於墨黑或深紅點數，而不是低對比瓷面紋理。 */
    private fun isPipPixel(rgb: Int): Boolean {
        val red = rgb shr 16 and 0xFF
        val green = rgb shr 8 and 0xFF
        val blue = rgb and 0xFF
        return (red < 100 && green < 100 && blue < 100) || (red > 120 && green < 100 && blue < 100)
    }

    /** 從測試 classpath 載入並解析指定 JSON object。 */
    private fun loadJson(resourcePath: String): JsonObject {
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }

        return Json.parseToJsonElement(content).jsonObject
    }
}
