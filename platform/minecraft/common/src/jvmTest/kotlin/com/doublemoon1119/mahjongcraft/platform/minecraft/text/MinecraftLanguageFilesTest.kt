package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證 MahjongCraft Minecraft 語系資源的結構與 key 一致性。 */
class MinecraftLanguageFilesTest {
    /** 支援的 Minecraft 語系代碼。 */
    private val locales = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

    /** MahjongCraft 玩家訊息 translation key 的命名空間前綴。 */
    private val messagePrefix = MinecraftModMetadata.MOD_ID + ".message."

    /** 驗證所有語系檔具有相同 key，且每個翻譯都是非空字串。 */
    @Test
    fun `all language files contain the same non-empty string translations`() {
        val translationsByLocale = locales.associateWith(::loadTranslations)
        val expectedKeys = translationsByLocale.getValue("en_us").keys

        translationsByLocale.forEach { (locale, translations) ->
            assertEquals(expectedKeys, translations.keys, "$locale language keys do not match en_us")
            translations.forEach { (key, value) ->
                val translation = assertIs<JsonPrimitive>(value, "$locale key $key must be a JSON primitive")
                assertTrue(translation.isString, "$locale key $key must be a string")
                assertTrue(translation.content.isNotBlank(), "$locale key $key must not be blank")
            }
        }
    }

    /** 驗證語系檔中的玩家訊息 key 與程式碼定義完全一致。 */
    @Test
    fun `language message keys match the production message key schema`() {
        locales.forEach { locale ->
            val messageKeys = loadTranslations(locale).keys.filterTo(mutableSetOf()) { it.startsWith(messagePrefix) }

            assertEquals(
                MinecraftMessageKeys.ALL,
                messageKeys,
                "$locale message keys do not match the production message schema",
            )
        }
    }

    /** 驗證所有語系檔都提供主要創造模式物品分類的顯示名稱。 */
    @Test
    fun `all language files contain the main item group translation`() {
        locales.forEach { locale ->
            assertTrue(
                MinecraftItemGroupKeys.MAIN in loadTranslations(locale),
                "$locale language file does not contain the main item group translation",
            )
        }
    }

    /** 從測試 classpath 載入並解析指定 Minecraft 語系資源。 */
    private fun loadTranslations(locale: String): JsonObject {
        val resourcePath = "/assets/mahjongcraft/lang/$locale.json"
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Language resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }

        return assertIs<JsonObject>(Json.parseToJsonElement(content), "Language resource must be a JSON object: $resourcePath")
    }
}
