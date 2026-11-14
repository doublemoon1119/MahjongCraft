package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證每種顏色的牌尺都有模型、標籤、配方與四種語言的名稱，且配方產出正確的牌尺。 */
class MahjongRulerResourcesTest {
    /** 每種顏色的牌尺都列在牌尺標籤裡，物品模型都指向同一個牌尺模型，顏色在遊戲裡染上。 */
    @Test
    fun `every ruler shares the ruler model and is in the ruler tag`() {
        val tagged = resource("data/mahjongcraft/tags/items/mahjong_rulers.json").jsonObject.getValue("values").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(MahjongRulerColor.entries.map { "mahjongcraft:${it.itemPath}" }, tagged)
        resource("assets/mahjongcraft/models/item/mahjong_ruler/base.json")
        MahjongRulerColor.entries.forEach { color ->
            assertEquals("mahjongcraft:item/mahjong_ruler/base", resource("assets/mahjongcraft/models/item/${color.itemPath}.json").jsonObject.string("parent"))
        }
    }

    /** 一排三片玻璃片合成四把同色牌尺；染色牌尺另外可以用任意牌尺加上同色染料換色。 */
    @Test
    fun `glass panes and dyes craft rulers of the matching colour`() {
        MahjongRulerColor.entries.forEach { color ->
            val glass = resource("data/mahjongcraft/recipes/${color.itemPath}.json").jsonObject
            val pane = color.dyeName?.let { "minecraft:${it}_stained_glass_pane" } ?: "minecraft:glass_pane"
            assertEquals(listOf("GGG"), glass.getValue("pattern").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(pane, glass.getValue("key").jsonObject.getValue("G").jsonObject.string("item"))
            assertEquals("mahjongcraft:${color.itemPath}", glass.getValue("result").jsonObject.string("item"))
            assertEquals(RULERS_PER_CRAFT, glass.getValue("result").jsonObject.string("count").toInt())

            val dyeName = color.dyeName ?: return@forEach
            val dyeing = resource("data/mahjongcraft/recipes/${color.itemPath}_from_dye.json").jsonObject
            val ingredients = dyeing.getValue("ingredients").jsonArray.map { it.jsonObject }
            assertEquals(listOf("mahjongcraft:mahjong_rulers"), ingredients.mapNotNull { it["tag"]?.jsonPrimitive?.content })
            assertEquals(listOf("minecraft:${dyeName}_dye"), ingredients.mapNotNull { it["item"]?.jsonPrimitive?.content })
            assertEquals("mahjongcraft:${color.itemPath}", dyeing.getValue("result").jsonObject.string("item"))
        }
    }

    /** 每種顏色的牌尺物品、牌尺實體與牌尺成就在四種語言都有翻譯。 */
    @Test
    fun `rulers and the ruler advancement are translated in every language`() {
        val keys = MahjongRulerColor.entries.map { "item.mahjongcraft.${it.itemPath}" } + listOf(
            "entity.mahjongcraft.${MahjongRulerColor.BASE_ITEM_PATH}",
            "advancements.mahjongcraft.mahjong.ruler_hit.title",
            "advancements.mahjongcraft.mahjong.ruler_hit.description",
        )
        LOCALES.forEach { locale ->
            val translations = resource("assets/mahjongcraft/lang/$locale.json").jsonObject
            keys.forEach { key -> assertTrue(key in translations, "$locale is missing $key") }
        }
    }

    /**
     * 讀取並解析 classpath 上的 JSON 資源。
     *
     * @param path 資源路徑。
     * @return 解析後的 JSON。
     */
    private fun resource(path: String): JsonElement {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream(path), "Missing resource $path")
        return Json.parseToJsonElement(stream.bufferedReader().use { it.readText() })
    }

    /** @return 指定欄位的字串內容。 */
    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        /** 支援的 Minecraft 語系代碼。 */
        val LOCALES: List<String> = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

        /** 一次合成得到的牌尺數量。 */
        const val RULERS_PER_CRAFT: Int = 4
    }
}
