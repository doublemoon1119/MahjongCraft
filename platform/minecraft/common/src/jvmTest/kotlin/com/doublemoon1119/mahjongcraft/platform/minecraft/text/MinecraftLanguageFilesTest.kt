package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementYakuTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.registerBuiltInGameConfigPresentations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
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

    /** MahjongCraft 胡牌結算 translation key 的命名空間前綴。 */
    private val settlementPrefix = MinecraftModMetadata.MOD_ID + ".settlement."

    /** MahjongCraft 役滿 showcase translation key 的命名空間前綴。 */
    private val showcasePrefix = MinecraftModMetadata.MOD_ID + ".showcase."

    /** MahjongCraft 役種 translation key 的命名空間前綴。 */
    private val yakuPrefix = MinecraftModMetadata.MOD_ID + ".game.yaku."

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

    /** 驗證語系檔中的胡牌結算 key 與呈現協定定義完全一致。 */
    @Test
    fun `language settlement keys match the production settlement key schema`() {
        locales.forEach { locale ->
            val settlementKeys = loadTranslations(locale).keys.filterTo(mutableSetOf()) { it.startsWith(settlementPrefix) }

            assertEquals(
                WinSettlementTranslationKeys.ALL,
                settlementKeys,
                "$locale settlement keys do not match the production settlement schema",
            )
        }
    }

    /** 驗證語系檔中的內建 showcase key 與程式碼定義完全一致。 */
    @Test
    fun `language showcase keys match the production showcase key schema`() {
        locales.forEach { locale ->
            val showcaseKeys = loadTranslations(locale).keys.filterTo(mutableSetOf()) { it.startsWith(showcasePrefix) }

            assertEquals(
                MinecraftShowcaseKeys.ALL,
                showcaseKeys,
                "$locale showcase keys do not match the production showcase schema",
            )
        }
    }

    /** 驗證語系檔中的役種 key 與役種列舉推導出的完整 key 集合完全一致。 */
    @Test
    fun `language yaku keys match the production yaku key schema`() {
        locales.forEach { locale ->
            val yakuKeys = loadTranslations(locale).keys.filterTo(mutableSetOf()) { it.startsWith(yakuPrefix) }

            assertEquals(
                WinSettlementYakuTranslationKeys.ALL,
                yakuKeys,
                "$locale yaku keys do not match the production yaku schema",
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

    /**
     * 驗證畫面與指令 key 物件宣告的每一個翻譯鍵都在所有語系檔中有對應翻譯。
     *
     * 這幾個 key 物件沒有 `ALL` 常數，改以反射列舉其字串常數：新增欄位時不需要同步維護第二份清單，
     * 忘記補翻譯會直接讓這個測試失敗，而不是等到遊戲中顯示未格式化的原始 key。
     */
    @Test
    fun `all declared screen and command keys have translations in every language`() {
        val declared = keyHolders.flatMap { holder -> declaredTranslationKeys(holder).map { holder.simpleName to it } }
        assertTrue(declared.isNotEmpty(), "no translation keys were discovered on the key holder objects")

        locales.forEach { locale ->
            val translations = loadTranslations(locale)
            declared.forEach { (holder, key) ->
                assertTrue(key in translations, "$locale is missing $key declared by $holder")
            }
        }
    }

    /**
     * 驗證內建遊戲設定 schema 產生的每一個翻譯鍵都在所有語系檔中有對應翻譯。
     *
     * 這些鍵不是程式碼中的字面值，而是由 [GameConfigPresentationRegistry] 的分類、欄位、單位與單選
     * 選項定義推導出來的，靜態掃描看不到；新增一個設定欄位卻沒補上四種語言的名稱與說明時，這裡會
     * 失敗。
     */
    @Test
    fun `all built-in game config presentation keys have translations in every language`() {
        val keys = builtInGameConfigTranslationKeys()
        assertTrue(keys.isNotEmpty(), "built-in game config presentations produced no translation keys")

        locales.forEach { locale ->
            val translations = loadTranslations(locale)
            keys.forEach { key ->
                assertTrue(key in translations, "$locale is missing game config key $key")
            }
        }
    }

    /** 沒有 `ALL` 常數、改以反射檢查的翻譯鍵物件。 */
    private val keyHolders = listOf(
        MinecraftRoomScreenKeys::class,
        MinecraftClientConfigScreenKeys::class,
        MinecraftConfigCommandKeys::class,
    )

    /**
     * 以反射列舉 Kotlin `object` 上所有 MahjongCraft 命名空間的字串常數。
     *
     * 以 `.` 結尾的常數是組成完整鍵用的前綴（例如單選選項前綴），本身不是翻譯鍵，不納入檢查。
     */
    private fun declaredTranslationKeys(holder: KClass<*>): List<String> = holder.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .mapNotNull { field ->
            field.isAccessible = true
            (field.get(null) as? String)
                ?.takeIf { it.startsWith(MinecraftModMetadata.MOD_ID + ".") && !it.endsWith(".") }
        }

    /** 收集內建設定 schema 的分類、欄位、說明、單位與單選選項翻譯鍵。 */
    private fun builtInGameConfigTranslationKeys(): Set<String> {
        val registry = GameConfigPresentationRegistryImpl().apply {
            registerBuiltInGameConfigPresentations()
            freeze()
        }

        return buildSet {
            registry.ruleModuleIds.forEach { ruleModuleId ->
                val definition = checkNotNull(registry.find(ruleModuleId)) { "Missing presentation: $ruleModuleId" }
                add(definition.descriptionTranslationKey)
                definition.unavailableReasonTranslationKey?.let(::add)
                definition.categories.forEach { add(it.nameTranslationKey) }
                definition.fields.forEach { field ->
                    add(field.nameTranslationKey)
                    add(field.descriptionTranslationKey)
                    when (val editor = field.editor) {
                        GameConfigEditorSpec.BooleanToggle -> Unit
                        is GameConfigEditorSpec.IntegerInput -> editor.unitTranslationKey?.let(::add)
                        is GameConfigEditorSpec.SingleChoice ->
                            editor.optionIds.forEach { add(MinecraftRoomScreenKeys.configOption(it)) }
                    }
                }
            }
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
