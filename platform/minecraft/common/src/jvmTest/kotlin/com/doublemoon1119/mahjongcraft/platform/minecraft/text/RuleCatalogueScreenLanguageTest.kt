package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證規則一覽畫面使用的翻譯鍵在所有支援語系中都有非空翻譯。 */
class RuleCatalogueScreenLanguageTest {
    /** 支援的 Minecraft 語系代碼。 */
    private val locales = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

    /** 驗證每個語系都提供規則一覽畫面的完整翻譯鍵集合。 */
    @Test
    fun `all locales contain rule catalogue screen translations`() {
        locales.forEach { locale ->
            val resource = "/assets/mahjongcraft/lang/$locale.json"
            val text = checkNotNull(javaClass.getResourceAsStream(resource)) {
                "Language resource not found: $resource"
            }.bufferedReader().use { it.readText() }
            val translations = assertIs<JsonObject>(Json.parseToJsonElement(text))

            MinecraftRuleCatalogueScreenKeys.ALL.forEach { key ->
                assertTrue(
                    translations[key]?.jsonPrimitive?.content?.isNotBlank() == true,
                    "$locale is missing rule catalogue screen key: $key",
                )
            }
        }
    }
}
