package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleEventMessageKeys
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleFurnitureMessageKeys
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 檢查範例 Minecraft 共用層的四份語系資源完整且格式一致。 */
class ExampleMinecraftResourcesTest {
    /** 測試範例目前提供的四種語系。 */
    private val languages = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

    /** 所有由範例 Minecraft 共用層使用的翻譯 key。 */
    private val messageKeys = listOf(
        ExampleEventMessageKeys.TITLE,
        ExampleEventMessageKeys.MATCH_STARTED,
        ExampleEventMessageKeys.MATCH_STARTED_SUMMARY,
        ExampleEventMessageKeys.ROUND_SETTLED,
        ExampleEventMessageKeys.ROUND_SETTLED_SUMMARY,
        ExampleEventMessageKeys.MATCH_ENDED,
        ExampleEventMessageKeys.MATCH_ENDED_SUMMARY,
        ExampleEventMessageKeys.EVENT_ID,
        ExampleEventMessageKeys.MATCH_ID,
        ExampleEventMessageKeys.VENUE_ID,
        ExampleEventMessageKeys.RULE_MODULE_ID,
        ExampleEventMessageKeys.TABLE_LOCATION,
        ExampleEventMessageKeys.UNKNOWN_LOCATION,
        ExampleEventMessageKeys.PLAYER,
        ExampleEventMessageKeys.ROUND_NUMBER,
        ExampleEventMessageKeys.OUTCOME_ID,
        ExampleEventMessageKeys.SETTLEMENT_KIND,
        ExampleEventMessageKeys.BENEFICIARIES,
        ExampleEventMessageKeys.RESPONSIBLE_PLAYERS,
        ExampleEventMessageKeys.SCORE_CHANGE,
        ExampleEventMessageKeys.COMPLETION,
        ExampleEventMessageKeys.REASON_ID,
        ExampleEventMessageKeys.STANDING,
        ExampleFurnitureMessageKeys.CHAIR,
        ExampleFurnitureMessageKeys.CUSHION,
        ExampleFurnitureMessageKeys.CUSHION_ENTITY,
        ExampleFurnitureMessageKeys.ITEM_GROUP,
        ExampleMinecraftMessageKeys.RULE_NAME,
        ExampleMinecraftMessageKeys.RULE_DESCRIPTION,
        ExampleMinecraftMessageKeys.AI_NAME,
        ExampleMinecraftMessageKeys.TILE_INVERTED_ONE_MAN,
        ExampleMinecraftMessageKeys.DECLARE,
        ExampleMinecraftMessageKeys.DECLARE_DESCRIPTION,
        ExampleMinecraftMessageKeys.DECLARE_CARD_BODY,
        ExampleMinecraftMessageKeys.DECLARE_CARD_HINT,
        ExampleMinecraftMessageKeys.DECLARED,
        ExampleMinecraftMessageKeys.EXCHANGE,
        ExampleMinecraftMessageKeys.EXHAUSTIVE_DRAW,
        ExampleMinecraftMessageKeys.CONFIG_CATEGORY_RULE,
        ExampleMinecraftMessageKeys.CONFIG_EXCHANGE,
        ExampleMinecraftMessageKeys.CONFIG_EXCHANGE_DESCRIPTION,
        ExampleMinecraftMessageKeys.CONFIG_PAYMENT,
        ExampleMinecraftMessageKeys.CONFIG_PAYMENT_DESCRIPTION,
        ExampleMinecraftMessageKeys.CONFIG_ROUNDS,
        ExampleMinecraftMessageKeys.CONFIG_ROUNDS_DESCRIPTION,
        ExampleMinecraftMessageKeys.ROUND_INFO_WALL_REMAINING,
        ExampleMinecraftMessageKeys.ROUND_INFO_ROUND_COUNT,
        ExampleMinecraftMessageKeys.CATALOGUE_CATEGORY,
        ExampleMinecraftMessageKeys.CATALOGUE_ENTRY,
        ExampleMinecraftMessageKeys.CATALOGUE_ENTRY_DESCRIPTION,
        ExampleMinecraftMessageKeys.PAYMENT_PER_PLAYER,
        ExampleMinecraftMessageKeys.POINTS,
    )

    /** 確認四份語系包含相同 key，且每個範例訊息都有非空翻譯。 */
    @Test
    fun `all language files contain the same non empty keys`() {
        val translations = languages.associateWith(::readLanguage)
        val expectedKeys = translations.getValue("en_us").keys

        languages.forEach { language ->
            val keys = translations.getValue(language).keys
            assertEquals(expectedKeys, keys, "Language keys differ for $language")
            messageKeys.forEach { key ->
                val value = translations.getValue(language)[key]
                assertNotNull(value, "$key is missing from $language")
                assertFalse(value.isBlank(), "$key is blank in $language")
            }
        }
    }

    /** 確認翻譯中的格式佔位符與英文基準一致。 */
    @Test
    fun `language files preserve placeholder positions`() {
        val reference = readLanguage("en_us")
        languages.forEach { language ->
            val translations = readLanguage(language)
            reference.forEach { (key, value) ->
                assertEquals(
                    placeholders(value),
                    placeholders(translations.getValue(key)),
                    "Placeholders differ for $key in $language",
                )
            }
        }
    }

    /**
     * 讀取指定語系的 JSON 資源。
     *
     * @param language Minecraft 語系 ID，例如 `zh_tw`。
     * @return 翻譯 key 與文字的對照表。
     */
    private fun readLanguage(language: String): Map<String, String> {
        val resource = "/assets/mahjongcraft_example/lang/$language.json"
        val text = checkNotNull(javaClass.getResourceAsStream(resource)) {
            "Language resource not found: $resource"
        }.bufferedReader().use { it.readText() }
        val translations = Json.parseToJsonElement(text).jsonObject.mapValues { (_, value) -> value.jsonPrimitive.content }
        translations.keys.forEach { key ->
            assertTrue(
                key.startsWith("mahjongcraft_example.") ||
                    key.startsWith("block.mahjongcraft_example.") ||
                    key.startsWith("item.mahjongcraft_example.") ||
                    key.startsWith("entity.mahjongcraft_example."),
                "Unexpected translation namespace: $key",
            )
        }
        return translations
    }

    /**
     * 擷取 Minecraft 翻譯文字中的格式佔位符。
     *
     * @param text 要檢查的翻譯文字。
     * @return 依出現順序排列的佔位符清單。
     */
    private fun placeholders(text: String): List<String> = "%[a-zA-Z]".toRegex().findAll(text).map { it.value }.toList()
}
