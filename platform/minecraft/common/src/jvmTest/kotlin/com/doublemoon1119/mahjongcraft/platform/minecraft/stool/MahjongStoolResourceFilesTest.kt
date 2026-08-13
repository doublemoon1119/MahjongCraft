package com.doublemoon1119.mahjongcraft.platform.minecraft.stool

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

/** 驗證兩款麻將凳的 blockstate、模型、物品模型與掉落表資源。 */
class MahjongStoolResourceFilesTest {
    /** 木製款模型 cuboids，順序與碰撞 profile 相同。 */
    private val woodenCuboids = listOf(
        ModelCuboid(listOf(1.0, 7.0, 4.0), listOf(15.0, 9.0, 12.0)),
        ModelCuboid(listOf(3.0, -0.5, 4.5), listOf(5.0, 7.0, 6.5)),
        ModelCuboid(listOf(3.0, -0.5, 9.5), listOf(5.0, 7.0, 11.5)),
        ModelCuboid(listOf(11.0, -0.5, 4.5), listOf(13.0, 7.0, 6.5)),
        ModelCuboid(listOf(11.0, -0.5, 9.5), listOf(13.0, 7.0, 11.5)),
        ModelCuboid(listOf(2.5, 2.5, 5.0), listOf(4.5, 4.0, 11.0)),
        ModelCuboid(listOf(11.5, 2.5, 5.0), listOf(13.5, 4.0, 11.0)),
    )

    /** 驗證木製款所有模型 cuboids 及材質與設計一致。 */
    @Test
    fun `wooden stool model contains narrow seat slanted legs and supports`() {
        val model = loadJson("/assets/mahjongcraft/models/block/mahjong_stool/wooden.json")

        assertEquals(woodenCuboids, model.modelCuboids())
        assertEquals("minecraft:block/stripped_oak_log", model.texture("wood"))
    }

    /** 驗證塑膠款具有中央孔、內凹止滑格與單一亮紅塑膠材質。 */
    @Test
    fun `plastic stool model contains hollow frame and top grip grid`() {
        val model = loadJson("/assets/mahjongcraft/models/block/mahjong_stool/plastic.json")
        val cuboids = model.modelCuboids()
        val gripTiles = cuboids.filter { cuboid -> cuboid.from[1] == 8.875 && cuboid.to[1] == 9.0 }

        assertEquals(24, gripTiles.size)
        assertEquals(1.75, cuboids.minOf { cuboid -> cuboid.from[0] })
        assertEquals(1.75, cuboids.minOf { cuboid -> cuboid.from[2] })
        assertEquals(14.25, cuboids.maxOf { cuboid -> cuboid.to[0] })
        assertEquals(14.25, cuboids.maxOf { cuboid -> cuboid.to[2] })
        assertEquals(9.0, cuboids.maxOf { cuboid -> cuboid.to[1] })
        assertEquals(false, cuboids.any(ModelCuboid::coversSeatCenter))
        assertEquals(false, cuboids.any(ModelCuboid::coversSeatCorner))
        assertEquals("mahjongcraft:block/mahjong_stool/red_plastic", model.texture("plastic"))
    }

    /** 驗證 blockstates 與 BlockItem models 指向各自固定款式模型。 */
    @Test
    fun `stool blockstates and item models reference fixed designs`() {
        listOf("wooden", "plastic").forEach { style ->
            val id = "${style}_mahjong_stool"
            val blockstate = loadJson("/assets/mahjongcraft/blockstates/$id.json")
            val itemModel = loadJson("/assets/mahjongcraft/models/item/$id.json")

            val variants = blockstate.getValue("variants").jsonObject
            assertEquals(setOf("facing=north", "facing=east", "facing=south", "facing=west"), variants.keys)
            assertEquals(
                "mahjongcraft:block/mahjong_stool/$style",
                variants.getValue("facing=north").jsonObject.getValue("model").jsonPrimitive.content,
            )
            assertEquals(90, variants.rotation("facing=east"))
            assertEquals(180, variants.rotation("facing=south"))
            assertEquals(270, variants.rotation("facing=west"))
            assertEquals("mahjongcraft:block/mahjong_stool/$style", itemModel.getValue("parent").jsonPrimitive.content)
        }
    }

    /** 驗證兩款凳子的專用低解析度材質皆可由資源包載入。 */
    @Test
    fun `stool textures have their intended pixel dimensions`() {
        mapOf(
            "red_plastic" to 16,
        ).forEach { (texture, size) ->
            val resourcePath = "/assets/mahjongcraft/textures/block/mahjong_stool/$texture.png"
            val image = javaClass.getResourceAsStream(resourcePath)?.use(ImageIO::read)

            assertNotNull(image, "Resource is not a readable image: $resourcePath")
            assertEquals(size, image.width)
            assertEquals(size, image.height)
        }
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

    /** 讀取模型的指定材質路徑。 */
    private fun JsonObject.texture(name: String): String = getValue("textures").jsonObject
        .getValue(name).jsonPrimitive.content

    /** 讀取指定 blockstate variant 的水平旋轉角度。 */
    private fun JsonObject.rotation(variant: String): Int = getValue(variant).jsonObject
        .getValue("y").jsonPrimitive.content.toInt()

    /** 讀取有形配方指定符號的 ingredient。 */
    private fun JsonObject.keyEntry(symbol: String): JsonObject = getValue("key").jsonObject
        .getValue(symbol).jsonObject

    /** 讀取配方輸出的物品 ID。 */
    private fun JsonObject.resultItem(): String = getValue("result").jsonObject
        .getValue("item").jsonPrimitive.content

    /** 讀取配方輸出的物品數量。 */
    private fun JsonObject.resultCount(): Int = getValue("result").jsonObject
        .getValue("count").jsonPrimitive.content.toInt()

    /** 將 block model elements 轉成可直接比對的 cuboids。 */
    private fun JsonObject.modelCuboids(): List<ModelCuboid> = getValue("elements").jsonArray.map { element ->
        val objectValue = element.jsonObject
        ModelCuboid(
            from = objectValue.getValue("from").jsonArray.decimalCoordinates(),
            to = objectValue.getValue("to").jsonArray.decimalCoordinates(),
        )
    }

    /** 將 JSON 座標陣列轉為小數清單。 */
    private fun JsonArray.decimalCoordinates(): List<Double> = map { coordinate ->
        coordinate.jsonPrimitive.content.toDouble()
    }

    /** 從測試 classpath 載入並解析指定 JSON object。 */
    private fun loadJson(resourcePath: String): JsonObject {
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }

        return Json.parseToJsonElement(content).jsonObject
    }

    /** 可比較的方塊模型 element 座標。 */
    private data class ModelCuboid(
        /** 最小角座標。 */
        val from: List<Double>,
        /** 最大角座標。 */
        val to: List<Double>,
    ) {
        /** 此 cuboid 是否在座板高度覆蓋中央 `2 × 2` 方孔。 */
        fun coversSeatCenter(): Boolean = from[1] >= 7.0 && from[0] < 9.0 && to[0] > 7.0 && from[2] < 9.0 && to[2] > 7.0

        /** 此 cuboid 是否在座板高度覆蓋任一切除的外角。 */
        fun coversSeatCorner(): Boolean = from[1] >= 7.0 &&
            listOf(
                2.1 to 2.1,
                13.9 to 2.1,
                2.1 to 13.9,
                13.9 to 13.9,
            ).any { (x, z) -> x > from[0] && x < to[0] && z > from[2] && z < to[2] }
    }
}
