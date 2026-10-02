package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryAiFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

/** 驗證列舉推導出的歷史選項鍵與保存狀態鍵在所有語系均完整。 */
class HistoryScreenTranslationKeysTest {
    /** 動態選項與狀態也必須具有四種語言，不能只驗證固定常數。 */
    @Test
    fun `dynamic history options have translations in all languages`() {
        val keys = buildList {
            add(MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_LABEL)
            add(MinecraftHistoryScreenKeys.PAGE_RANGE)
            add(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP)
            add(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)
            addAll(HistoryQueryScopeDto.entries.map { MinecraftHistoryScreenKeys.SCOPE_PREFIX + it.name.lowercase() })
            addAll(HistorySortFieldDto.entries.map { MinecraftHistoryScreenKeys.SORT_PREFIX + it.name.lowercase() })
            addAll(HistorySortDirectionDto.entries.map { MinecraftHistoryScreenKeys.DIRECTION_PREFIX + it.name.lowercase() })
            addAll(HistoryAiFilterDto.entries.map { MinecraftHistoryScreenKeys.FILTER_AI + "." + it.name.lowercase() })
            addAll(HistoryIntegrityFilterDto.entries.map { MinecraftHistoryScreenKeys.FILTER_INTEGRITY + "." + it.name.lowercase() })
            addAll(HistoryOutcomeFilterDto.entries.map { MinecraftHistoryScreenKeys.FILTER_OUTCOME + "." + it.name.lowercase() })
            addAll(
                HistoryBrowseFilterField.entries.filterNot { it == HistoryBrowseFilterField.RULE }.flatMap {
                    listOf(MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_PREFIX + it.name.lowercase(), MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_PREFIX + it.name.lowercase())
                },
            )
            addAll(HistoryBrowseFilterError.entries.map { MinecraftHistoryScreenKeys.FILTER_ERROR_PREFIX + it.name.lowercase() })
            addAll(
                HistoryArchiveStatusDto.entries.filterNot { it == HistoryArchiveStatusDto.SAVED }.map {
                    MinecraftHistoryScreenKeys.ARCHIVE_STATUS_PREFIX + it.name.lowercase()
                },
            )
        }
        listOf("en_us", "ja_jp", "zh_cn", "zh_tw").forEach { locale ->
            val resource = checkNotNull(javaClass.classLoader.getResourceAsStream("assets/mahjongcraft/lang/$locale.json")) {
                "Missing language resource: $locale"
            }
            val translations = Json.parseToJsonElement(resource.bufferedReader().use { it.readText() }).jsonObject
            keys.forEach { key -> assertTrue(key in translations, "$locale is missing $key") }
        }
    }
}
