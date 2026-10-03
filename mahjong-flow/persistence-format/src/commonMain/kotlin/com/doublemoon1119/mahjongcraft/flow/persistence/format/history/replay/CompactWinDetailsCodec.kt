package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 將和牌明細的固定結構轉成精簡陣列；翻譯鍵與擴充欄位 ID 仍由既有字典處理。 */
internal object CompactWinDetailsCodec {
    /**
     * 編碼逐位明細，省略可還原的空白尾端欄位。
     * @param details 保存 DTO 產生的明細陣列。
     * @return 使用局內玩家／牌參照的精簡明細。
     */
    fun encode(details: JsonElement): JsonArray = array(details).mapArray { raw ->
        val winner = obj(raw)
        JsonArray(
            listOf(
                winner.getValue(PLAYER_ID),
                winner.getValue(TEMPLATE_KEY),
                array(winner.getValue(DETAIL_FIELDS)).mapArray { fieldRaw ->
                    val field = obj(fieldRaw)
                    JsonArray(listOf(field.getValue(ID), encodeValue(obj(field.getValue(VALUE)))))
                },
            ),
        )
    }

    /**
     * 還原明細的明確結構，不重新計分或猜測未知值種類。
     * @param details 精簡明細陣列。
     * @return 供既有歷史投影 mapper 使用的明細物件。
     */
    fun decode(details: JsonElement): JsonArray = array(details).mapArray { raw ->
        val winner = array(raw)
        require(winner.size == 3) { "Compact winner detail must contain three fields" }
        JsonObject(
            mapOf(
                PLAYER_ID to winner[0],
                TEMPLATE_KEY to winner[1],
                DETAIL_FIELDS to array(winner[2]).mapArray { fieldRaw ->
                    val field = array(fieldRaw)
                    require(field.size == 2) { "Compact winner field must contain two fields" }
                    JsonObject(mapOf(ID to field[0], VALUE to decodeValue(array(field[1]))))
                },
            ),
        )
    }

    /**
     * 編碼三種明確明細值，種類代碼為固定格式契約而非 enum ordinal。
     * @param value 保存的明細值。
     * @return 精簡值。
     */
    private fun encodeValue(value: JsonObject): JsonArray = when ((value[TYPE] as? JsonPrimitive)?.content) {
        TEXT -> JsonArray(listOf(JsonPrimitive(TEXT_CODE), value.getValue(TRANSLATION_KEY)) + array(value[ARGUMENTS] ?: JsonArray(emptyList())))
        TILES -> JsonArray(listOf(JsonPrimitive(TILES_CODE), value.getValue(TILE_IDS)))
        ENTRIES -> JsonArray(
            listOf(
                JsonPrimitive(ENTRIES_CODE),
                array(value.getValue(ENTRIES)).mapArray { raw ->
                    val entry = obj(raw)
                    val parts = mutableListOf(entry.getValue(TRANSLATION_KEY), entry[TRAILING_TEXT]?.takeUnless { it == JsonPrimitive("") } ?: JsonNull, entry[TRAILING_KEY] ?: JsonNull, entry[TRAILING_ARGUMENT] ?: JsonNull)
                    while (parts.last() == JsonNull) parts.removeAt(parts.lastIndex)
                    JsonArray(parts)
                },
            ),
        )
        else -> error("Unsupported winner detail value type")
    }

    /**
     * 解碼固定值種類，拒絕錯誤陣列長度與未支援代碼。
     * @param value 精簡值。
     * @return 明確的明細值物件。
     */
    private fun decodeValue(value: JsonArray): JsonObject {
        require(value.size >= 2) { "Compact winner value is incomplete" }
        return when (value[0]) {
            JsonPrimitive(TEXT_CODE) -> JsonObject(mapOf(TYPE to JsonPrimitive(TEXT), TRANSLATION_KEY to value[1], ARGUMENTS to JsonArray(value.drop(2))))
            JsonPrimitive(TILES_CODE) -> {
                require(value.size == 2) { "Compact tile detail has unexpected fields" }
                JsonObject(mapOf(TYPE to JsonPrimitive(TILES), TILE_IDS to array(value[1])))
            }
            JsonPrimitive(ENTRIES_CODE) -> {
                require(value.size == 2) { "Compact entry detail has unexpected fields" }
                JsonObject(
                    mapOf(
                        TYPE to JsonPrimitive(ENTRIES),
                        ENTRIES to array(value[1]).mapArray { raw ->
                            val entry = array(raw)
                            require(entry.size in 1..4) { "Compact winner entry has invalid fields" }
                            JsonObject(mapOf(TRANSLATION_KEY to entry[0], TRAILING_TEXT to (entry.getOrNull(1)?.takeUnless { it == JsonNull } ?: JsonPrimitive("")), TRAILING_KEY to (entry.getOrNull(2) ?: JsonNull), TRAILING_ARGUMENT to (entry.getOrNull(3) ?: JsonNull)))
                        },
                    ),
                )
            }
            else -> throw IllegalArgumentException("Unsupported compact winner detail code")
        }
    }

    /**
     * 映射陣列並保留原順序。
     * @param transform 單項結構轉換。
     * @return 轉換後的 JSON 陣列。
     */
    private fun JsonArray.mapArray(transform: (JsonElement) -> JsonElement): JsonArray = JsonArray(map(transform))

    /**
     * 驗證明確物件結構。
     * @param value 待驗證值。
     * @return JSON 物件。
     */
    private fun obj(value: JsonElement): JsonObject = value as? JsonObject ?: error("Winner detail must be an object")

    /**
     * 驗證明確陣列結構。
     * @param value 待驗證值。
     * @return JSON 陣列。
     */
    private fun array(value: JsonElement): JsonArray = value as? JsonArray ?: error("Winner detail must be an array")

    /** 原始保存格式的逐位明細欄位。 */
    const val WIN_DETAILS = "winDetails"

    /** 玩家參照。 */
    private const val PLAYER_ID = "playerId"

    /** 規則模板識別碼。 */
    private const val TEMPLATE_KEY = "templateKey"

    /** 有序明細欄位。 */
    private const val DETAIL_FIELDS = "detailFields"

    /** 明細欄位識別碼。 */
    private const val ID = "id"

    /** 明細值。 */
    private const val VALUE = "value"

    /** 值種類。 */
    private const val TYPE = "type"

    /** 文字值種類與固定代碼。 */
    private const val TEXT = "text"

    /** 文字值固定代碼。 */
    private const val TEXT_CODE = 0

    /** 牌參照值種類。 */
    private const val TILES = "tiles"

    /** 牌參照值固定代碼。 */
    private const val TILES_CODE = 1

    /** 條目值種類。 */
    private const val ENTRIES = "entries"

    /** 條目值固定代碼。 */
    private const val ENTRIES_CODE = 2

    /** 文字翻譯鍵。 */
    private const val TRANSLATION_KEY = "translationKey"

    /** 文字翻譯參數。 */
    private const val ARGUMENTS = "arguments"

    /** 牌參照陣列。 */
    private const val TILE_IDS = "tileIds"

    /** 尾端純文字。 */
    private const val TRAILING_TEXT = "trailingText"

    /** 尾端翻譯鍵。 */
    private const val TRAILING_KEY = "trailingTranslationKey"

    /** 尾端翻譯參數。 */
    private const val TRAILING_ARGUMENT = "trailingTranslationArgument"
}
