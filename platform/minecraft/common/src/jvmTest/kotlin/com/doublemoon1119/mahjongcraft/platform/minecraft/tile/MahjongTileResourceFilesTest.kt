package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** 驗證麻將牌 item predicate、子模型與貼圖資源完整對齊 asset key schema。 */
class MahjongTileResourceFilesTest {
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

    /** 驗證主 item model 的 predicate 順序及目標模型與 production asset keys 一致。 */
    @Test
    fun `item model overrides match every tile asset key in order`() {
        val model = loadJson("/assets/mahjongcraft/models/item/mahjong_tile.json")
        val overrides = model.getValue("overrides") as JsonArray

        assertEquals(ALL_RIICHI_TILE_ASSET_KEYS.size, overrides.size)
        overrides.zip(ALL_RIICHI_TILE_ASSET_KEYS).forEachIndexed { index, (element, assetKey) ->
            val override = element.jsonObject
            assertEquals(index / 100.0, override.getValue("predicate").jsonObject.getValue("tile").jsonPrimitive.content.toDouble())
            assertEquals(
                "mahjongcraft:item/mahjong_tile/mahjong_tile_$assetKey",
                override.getValue("model").jsonPrimitive.content,
            )
        }
    }

    /** 驗證每個 asset key 都具有可載入的子模型及 PNG 貼圖。 */
    @Test
    fun `every tile asset key has a model and texture resource`() {
        ALL_RIICHI_TILE_ASSET_KEYS.forEach { assetKey ->
            assertNotNull(
                javaClass.getResource("/assets/mahjongcraft/models/item/mahjong_tile/mahjong_tile_$assetKey.json"),
                "Missing item model for tile asset key: $assetKey",
            )
            assertNotNull(
                javaClass.getResource("/assets/mahjongcraft/textures/item/mahjong_tile/mahjong_tile_$assetKey.png"),
                "Missing texture for tile asset key: $assetKey",
            )
        }
    }

    /** 驗證牌面及共用牌體貼圖維持模型 UV 所依賴的像素尺寸。 */
    @Test
    fun `tile textures retain dimensions required by the base model`() {
        ALL_RIICHI_TILE_ASSET_KEYS.forEach { assetKey ->
            val path = "/assets/mahjongcraft/textures/item/mahjong_tile/mahjong_tile_$assetKey.png"
            val image = checkNotNull(javaClass.getResourceAsStream(path)) {
                "Texture resource not found: $path"
            }.use(ImageIO::read)
            assertEquals(48, image.width, "Unexpected texture width for tile asset key: $assetKey")
            assertEquals(64, image.height, "Unexpected texture height for tile asset key: $assetKey")
        }

        val coverPath = "/assets/mahjongcraft/textures/item/mahjong_tile/mahjong_tile_cover.png"
        val cover = checkNotNull(javaClass.getResourceAsStream(coverPath)) {
            "Texture resource not found: $coverPath"
        }.use(ImageIO::read)
        assertEquals(256, cover.width, "Unexpected Mahjong tile cover texture width")
        assertEquals(256, cover.height, "Unexpected Mahjong tile cover texture height")
    }

    /** 驗證麻將牌配方使用穩定染料 tag、固定牌體材料，並一次產出 32 張。 */
    @Test
    fun `tile recipe uses the portable dye tag and produces thirty two tiles`() {
        val recipe = loadJson("/data/mahjongcraft/recipes/mahjong_tile.json")
        val key = recipe.getValue("key").jsonObject
        val result = recipe.getValue("result").jsonObject

        assertEquals(listOf(" WG", "DWG", " WG"), assertIs<JsonArray>(recipe["pattern"]).map { it.jsonPrimitive.content })
        assertEquals("minecraft:white_concrete", key.getValue("W").jsonObject.getValue("item").jsonPrimitive.content)
        assertEquals(
            "mahjongcraft:mahjong_tile_back_concretes",
            key.getValue("G").jsonObject.getValue("tag").jsonPrimitive.content,
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

    /** 從測試 classpath 載入指定 JSON object。 */
    private fun loadJson(resourcePath: String): JsonObject {
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(content).jsonObject
    }
}
