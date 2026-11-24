package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.debug.HistoryDebugKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.debug.StressDebugKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證 debug 模組自己的語系檔：各語系的 key 一致、翻譯不為空，且宣告的每個 key 都有翻譯。 */
class DebugLanguageFilesTest {
    /** 需要提供翻譯的語系。 */
    private val locales = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

    /** 宣告 debug 翻譯鍵的物件。 */
    private val keyHolders: List<KClass<*>> = listOf(HistoryDebugKeys::class, StressDebugKeys::class, DebugMessageKeys::class)

    /** 所有語系的 key 與英文相同，每個翻譯都是非空字串。 */
    @Test
    fun `all language files contain the same non-empty string translations`() {
        val translationsByLocale = locales.associateWith(::loadTranslations)
        val expectedKeys = translationsByLocale.getValue("en_us").keys

        translationsByLocale.forEach { (locale, translations) ->
            assertEquals(expectedKeys, translations.keys, "$locale language keys do not match en_us")
            translations.forEach { (key, value) ->
                val translation = assertIs<JsonPrimitive>(value, "$locale key $key must be a JSON primitive")
                assertTrue(translation.isString && translation.content.isNotBlank(), "$locale key $key must be a non-blank string")
            }
        }
    }

    /** 語系檔的 key 與 debug 程式宣告的 key 完全一致。 */
    @Test
    fun `language keys match the declared debug keys`() {
        val declared = keyHolders.flatMapTo(sortedSetOf(), ::declaredTranslationKeys)
        assertTrue(declared.isNotEmpty(), "no translation keys were discovered on the key holder objects")

        locales.forEach { locale ->
            assertEquals(declared, loadTranslations(locale).keys.toSortedSet(), "$locale does not match the declared debug keys")
        }
    }

    /**
     * 以反射列舉 `object` 上所有字串常數。
     *
     * @param holder 宣告翻譯鍵的物件。
     * @return 物件上的字串常數值。
     */
    private fun declaredTranslationKeys(holder: KClass<*>): List<String> = holder.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { field ->
            field.isAccessible = true
            field.get(null) as String
        }

    /**
     * 讀取這個模組的一個語系檔。
     *
     * @param locale 語系代碼。
     * @return 語系檔內容。
     */
    private fun loadTranslations(locale: String): JsonObject {
        val directory = File(checkNotNull(System.getProperty("mahjongcraft.debug.langDirectory")) { "Debug language directory is not configured" })
        val content = directory.resolve("$locale.json").readText()
        return assertIs<JsonObject>(Json.parseToJsonElement(content), "Language file must be a JSON object: $locale")
    }
}
