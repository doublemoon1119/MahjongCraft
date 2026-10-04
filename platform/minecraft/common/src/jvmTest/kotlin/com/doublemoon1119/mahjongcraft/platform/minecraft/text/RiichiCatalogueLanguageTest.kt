package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.RiichiCatalogueProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證日麻目錄實際引用的四語文字，不以 enum 名稱代替翻譯。 */
class RiichiCatalogueLanguageTest {
    /** 每個配置分支的分類、名稱、條件、說明與範例文字必須存在。 */
    @Test
    fun `all four locales cover every referenced catalogue key`() {
        val provider = RiichiCatalogueProvider()
        val keys = buildSet {
            listOf(RiichiRuleConfig(), RiichiRuleConfig(allowOpenTanyao = false, redDoraCount = 0)).forEach { config ->
                val catalogue = checkNotNull(provider.catalogue(config))
                catalogue.categories.forEach { add(it.nameTranslationKey) }
                catalogue.entries.forEach { entry ->
                    add(entry.nameTranslationKey)
                    add(entry.descriptionTranslationKey)
                    addAll(entry.labelTranslationKeys)
                    entry.unavailableReasonTranslationKey?.let(::add)
                    entry.examples.forEach { it.descriptionTranslationKey?.let(::add) }
                }
            }
        }
        listOf("en_us", "ja_jp", "zh_cn", "zh_tw").forEach { locale ->
            val resource = "/assets/mahjongcraft/lang/$locale.json"
            val text = checkNotNull(javaClass.getResourceAsStream(resource)) { "Language resource not found: $resource" }.bufferedReader().use { it.readText() }
            val translations = assertIs<JsonObject>(Json.parseToJsonElement(text))
            keys.forEach { key ->
                assertTrue(translations[key]?.jsonPrimitive?.content?.isNotBlank() == true, "$locale is missing catalogue key: $key")
            }
        }
    }
}
