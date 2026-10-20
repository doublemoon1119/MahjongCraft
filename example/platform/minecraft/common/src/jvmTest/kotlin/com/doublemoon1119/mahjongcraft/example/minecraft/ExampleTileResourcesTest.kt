package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistryImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證實際牌面、牌體與字型資源能由第三方登記內容找到，且維持固定模型。 */
class ExampleTileResourcesTest {
    /** 範例共用外觀、正面與標籤均綁定到同一個完整素材 key。 */
    @Test
    fun `registered tile resources use the example namespace and standard model`() {
        val extension = ExampleMinecraftMahjongExtension()
        val assets = MinecraftTileAssetRegistryImpl().apply(extension::registerTileAssets).apply { freeze() }
        val key = assertNotNull(assets.find(ExamplePairingIds.INVERTED_ONE_MAN))
        val resources = assertNotNull(assets.findResources(key))
        val appearance = assertNotNull(assets.findAppearanceForRule(ExamplePairingIds.RULE_MODULE))
        assertTrue(key.startsWith("mahjongcraft_example:"))
        assertTrue(resources.textureId.startsWith("mahjongcraft_example:"))
        assertTrue(appearance.coverTextureId.startsWith("mahjongcraft_example:"))
        assertTrue(appearance.unknownFaceTextureId.startsWith("mahjongcraft_example:"))
        val model = readJson(resourcePath(resources.modelId, "models", ".json"))
        assertEquals("mahjongcraft:item/mahjong_tile_base", model.getValue("parent").jsonPrimitive.content)
        assertTrue("elements" !in model, "Example must inherit standard tile geometry")
        assertTrue("display" !in model, "Example must inherit standard display transforms")
        val textures = model.getValue("textures").jsonObject
        assertEquals(resources.textureId.modelTextureId(), textures.getValue("0").jsonPrimitive.content)
        assertEquals(appearance.coverTextureId.modelTextureId(), textures.getValue("1").jsonPrimitive.content)
        readImage(textureResourcePath(appearance.unknownFaceTextureId))
        val cover = readImage(textureResourcePath(appearance.coverTextureId))
        val standardCover = readImage("/assets/mahjongcraft/textures/item/mahjong_tile/mahjong_tile_cover.png")
        assertEquals(standardCover.width, cover.width)
        assertEquals(standardCover.height, cover.height)
        assertNotEquals(standardCover.getRGB(60, 20), cover.getRGB(60, 20), "Example back must visibly differ")
        val labels = TileLabelRegistryImpl().apply(extension::registerTileLabels)
        assertNotNull(labels.find(key))
    }

    /** 倒置一萬的像素正好是內建一萬旋轉半圈，文字用 emoji 也使用這份圖案。 */
    @Test
    fun `face is rotated and bitmap font matches registered emoji`() {
        val extension = ExampleMinecraftMahjongExtension()
        val assets = MinecraftTileAssetRegistryImpl().apply(extension::registerTileAssets)
        val key = assertNotNull(assets.find(ExamplePairingIds.INVERTED_ONE_MAN))
        val resources = assertNotNull(assets.findResources(key))
        val inverted = readImage(textureResourcePath(resources.textureId))
        val original = readImage("/assets/mahjongcraft/textures/item/mahjong_tile/mahjong_tile_m1.png")
        assertEquals(original.width, inverted.width)
        assertEquals(original.height, inverted.height)
        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
                assertEquals(original.getRGB(x, y), inverted.getRGB(original.width - x - 1, original.height - y - 1))
            }
        }
        val emojis = TileEmojiRegistryImpl().apply(extension::registerTileEmojis)
        val provider = readJson("/assets/minecraft/font/default.json").getValue("providers").jsonArray.single().jsonObject
        assertEquals("bitmap", provider.getValue("type").jsonPrimitive.content)
        assertEquals(emojis.find(key), provider.getValue("chars").jsonArray.single().jsonPrimitive.content)
        val fontGlyphs = javaClass.classLoader.getResources("assets/minecraft/font/default.json").toList().flatMap { resource ->
            val font = resource.openStream().bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
            font.getValue("providers").jsonArray.flatMap { entry ->
                entry.jsonObject["chars"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            }
        }
        assertTrue("🀇" in fontGlyphs, "Built-in tile font must remain available beside the example provider")
        assertEquals(1, fontGlyphs.count { it == emojis.find(key) }, "Example glyph must have one provider")
        val bitmap = readImage(resourcePath(provider.getValue("file").jsonPrimitive.content.removeSuffix(".png"), "textures", ".png"))
        assertEquals(inverted.width, bitmap.width)
        assertEquals(inverted.height, bitmap.height)
        for (y in 0 until inverted.height) {
            for (x in 0 until inverted.width) assertEquals(inverted.getRGB(x, y), bitmap.getRGB(x, y))
        }
    }

    /**
     * 將完整資源 ID 對應到實際 classpath 檔案。
     *
     * @param id 完整 namespace:path 資源 ID。
     * @param kind models 或 textures 資源類別。
     * @param suffix 檔案副檔名。
     * @return 可讀取的 classpath 路徑。
     */
    private fun resourcePath(id: String, kind: String, suffix: String): String = "/assets/${id.substringBefore(':')}/$kind/${id.substringAfter(':')}$suffix"

    /**
     * 直接讀取登記的完整貼圖路徑，不替錯誤登記補上目錄或副檔名。
     *
     * @param id 完整貼圖資源 ID。
     * @return classpath 中的原樣資源路徑。
     */
    private fun textureResourcePath(id: String): String {
        val path = id.substringAfter(':')
        assertTrue(path.startsWith("textures/") && path.endsWith(".png"), "Expected full texture resource ID: $id")
        return "/assets/${id.substringBefore(':')}/$path"
    }

    /**
     * 將已驗證的完整貼圖檔案 ID 轉為模型 JSON 材質槽使用的名稱。
     *
     * @receiver 完整貼圖檔案 ID。
     * @return 不含 textures 目錄及副檔名的模型材質名稱。
     */
    private fun String.modelTextureId(): String {
        textureResourcePath(this)
        return "${substringBefore(':')}:${substringAfter(':').removePrefix("textures/").removeSuffix(".png")}"
    }

    /**
     * 讀取實際 JSON 資源。
     *
     * @param path classpath 絕對路徑。
     * @return JSON 物件。
     */
    private fun readJson(path: String): JsonObject = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing resource: $path" }.bufferedReader().use { it.readText() },
    ).jsonObject

    /**
     * 讀取實際 PNG 圖片。
     *
     * @param path classpath 絕對路徑。
     * @return 解碼後的圖片。
     */
    private fun readImage(path: String): BufferedImage = checkNotNull(javaClass.getResourceAsStream(path)) {
        "Missing image resource: $path"
    }.use { stream -> checkNotNull(ImageIO.read(stream)) { "Invalid image resource: $path" } }
}
