package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** 驗證麻將牌 item predicate、子模型與貼圖資源完整對齊 asset key schema。 */
class MahjongTileResourceFilesTest {
    /** 模組自帶資源的所有牌面：基本牌、內建 extension 登記的擴充牌種與 unknown。 */
    private val assetKeys: List<String> = MinecraftTileAssetRegistryImpl().apply {
        BuiltInMinecraftMahjongExtension.registerTileAssets(this)
        BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
        freeze()
    }.allTileAssetKeys()

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

    /**
     * 驗證主 item model 是 `builtin/entity` marker，實際牌面由 `MahjongTileItemRenderer`
     * （`platform/minecraft/<version>/<loader>`）依 asset key 動態解析，不再依賴固定 predicate
     * override 清單。
     */
    @Test
    fun `item model is a builtin entity marker`() {
        val model = loadJson("/assets/mahjongcraft/models/item/mahjong_tile.json")

        assertEquals("builtin/entity", model.getValue("parent").jsonPrimitive.content)
    }

    /** 驗證每個 asset key 都具有可載入的子模型及 PNG 貼圖。 */
    @Test
    fun `every tile asset key has a model and texture resource`() {
        assetKeys.forEach { assetKey ->
            assertNotNull(
                actual = javaClass.getResource("/assets/mahjongcraft/models/item/mahjong_tile/mahjong_tile_$assetKey.json"),
                message = "Missing item model for tile asset key: $assetKey",
            )
            assertNotNull(
                actual = javaClass.getResource("/assets/mahjongcraft/textures/item/mahjong_tile/mahjong_tile_$assetKey.png"),
                message = "Missing texture for tile asset key: $assetKey",
            )
        }
    }

    /** 驗證牌面及共用牌體貼圖維持模型 UV 所依賴的像素尺寸。 */
    /**
     * 每種標準牌面都有尺寸相同的透明版貼圖與平面預覽貼圖，透明牌身與一般牌身尺寸相同；新增或改動標準牌面後須以
     * Gradle 任務 `generateTransparentTileTextures` 重新產生。
     */
    @Test
    fun `every standard face has a transparent texture of the same size`() {
        BuiltInMinecraftTileStyles.TRANSPARENT_TILES.forEach { tile ->
            val standard = texture(tile.toAssetKey(MinecraftTileAssetRegistryImpl()))
            val transparentKey = BuiltInMinecraftTileStyles.transparentAssetKey(tile)
            listOf(texture(transparentKey), texture("${transparentKey}_preview")).forEach { transparent ->
                assertEquals(standard.width to standard.height, transparent.width to transparent.height, "$tile")
            }
        }
        val cover = texture("cover")
        val transparentCover = texture("cover_transparent")
        assertEquals(cover.width to cover.height, transparentCover.width to transparentCover.height)
    }

    /**
     * 讀取內建牌面貼圖。
     *
     * @param key 貼圖檔名中 `mahjong_tile_` 之後的部分。
     * @return 讀到的圖片。
     */
    private fun texture(key: String) = assertNotNull(
        actual = javaClass.getResourceAsStream("/assets/mahjongcraft/textures/item/mahjong_tile/mahjong_tile_$key.png")?.use(ImageIO::read),
        message = "Missing texture for $key",
    )

    @Test
    fun `tile textures retain dimensions required by the base model`() {
        assetKeys.forEach { assetKey ->
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

    /** 牌背預覽的 south UV 取自 canonical 牌體模型，而不是只靠重複的 magic number。 */
    @Test
    fun `flat back preview matches canonical south face uv`() {
        val model = loadJson("/assets/mahjongcraft/models/item/mahjong_tile_base.json")
        val southUv = model["elements"]!!.jsonArray
            .single().jsonObject["faces"]!!.jsonObject["south"]!!.jsonObject["uv"]!!.jsonArray
            .map { it.jsonPrimitive.float }
        val preview = tilePreviewTexture(TILE_BACK_PREVIEW_ASSET_KEY, MinecraftTileAssetRegistryImpl())

        assertEquals(southUv[0] / 16f, preview.u0)
        assertEquals(southUv[1] / 16f, preview.v0)
        assertEquals(southUv[2] / 16f, preview.u1)
        assertEquals(southUv[3] / 16f, preview.v1)
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

    /** 從測試 classpath 載入指定 JSON object。 */
    private fun loadJson(resourcePath: String): JsonObject {
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(content).jsonObject
    }
}
