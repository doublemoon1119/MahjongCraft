package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 將和牌明細的固定結構轉成精簡陣列；條目、單位與擴充欄位 ID 仍由既有字典處理。 */
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
                winner.getValue(ReplaySourceKeys.PLAYER_ID),
                array(winner.getValue(ReplaySourceKeys.DETAIL_FIELDS)).mapArray { fieldRaw ->
                    val field = obj(fieldRaw)
                    JsonArray(listOf(field.getValue(ReplaySourceKeys.ID), encodeValue(obj(field.getValue(ReplaySourceKeys.VALUE)))))
                },
            ) + winner[ReplaySourceKeys.WINNING_HAND]?.takeUnless { it == JsonNull }?.let { listOf(encodeHand(obj(it))) }.orEmpty(),
        )
    }

    /**
     * 還原明細的明確結構，不重新計分或猜測未知值種類。
     * @param details 精簡明細陣列。
     * @return 供既有歷史投影 mapper 使用的明細物件。
     */
    fun decode(details: JsonElement): JsonArray = array(details).mapArray { raw ->
        val winner = array(raw)
        require(winner.size in 2..3) { "Compact winner detail must contain two or three fields" }
        val hand = winner.getOrNull(2)?.let { decodeHand(array(it)) }
        JsonObject(
            linkedMapOf(
                ReplaySourceKeys.PLAYER_ID to winner[0],
                ReplaySourceKeys.DETAIL_FIELDS to array(winner[1]).mapArray { fieldRaw ->
                    val field = array(fieldRaw)
                    require(field.size == 2) { "Compact winner field must contain two fields" }
                    JsonObject(mapOf(ReplaySourceKeys.ID to field[0], ReplaySourceKeys.VALUE to decodeValue(array(field[1]))))
                },
            ) + hand?.let { mapOf(ReplaySourceKeys.WINNING_HAND to it) }.orEmpty(),
        )
    }

    /** 將可選的胡牌手牌描述編碼為立牌索引陣列與獨立和牌索引。
     * @param value 明確保存的手牌描述。
     * @return 精簡立牌參照與和牌參照陣列。
     */
    private fun encodeHand(value: JsonObject): JsonArray {
        val standing = array(value.getValue(ReplaySourceKeys.WINNING_STANDING_TILES))
        validateUniqueRefs(standing, "Standing tile")
        val winning = value[ReplaySourceKeys.WINNING_TILE] ?: JsonNull
        require(winning == JsonNull || winning !in standing) { "Winning tile must be separate from standing tiles" }
        return JsonArray(listOf(standing, winning))
    }

    /** 解碼並驗證可選的胡牌手牌描述。
     * @param value 精簡手牌描述。
     * @return 明確的有序立牌與和牌張物件。
     */
    private fun decodeHand(value: JsonArray): JsonObject {
        require(value.size == 2) { "Compact winning hand must contain two fields" }
        val standing = array(value[0])
        validateUniqueRefs(standing, "Standing tile")
        val winning = value[1]
        require(winning == JsonNull || winning !in standing) { "Winning tile must be separate from standing tiles" }
        return JsonObject(mapOf(ReplaySourceKeys.WINNING_STANDING_TILES to standing, ReplaySourceKeys.WINNING_TILE to winning))
    }

    /** 驗證牌參照列表不含重複實體。
     * @param value 待驗證參照列表。
     * @param label 英文診斷名稱。
     */
    private fun validateUniqueRefs(value: JsonArray, label: String) {
        require(value.distinct().size == value.size) { "$label list contains duplicate tiles" }
    }

    /**
     * 編碼三種明確明細值，種類代碼為固定格式契約而非 enum ordinal。
     * @param value 保存的明細值。
     * @return 精簡值。
     */
    private fun encodeValue(value: JsonObject): JsonArray = when ((value[ReplaySourceKeys.TYPE] as? JsonPrimitive)?.content) {
        HistoryDetailValueTypeKeys.QUANTITIES -> JsonArray(
            listOf(JsonPrimitive(QUANTITIES_CODE), array(value.getValue(ReplaySourceKeys.QUANTITIES)).mapArray { encodeQuantity(obj(it)) }),
        )
        HistoryDetailValueTypeKeys.TILES -> JsonArray(listOf(JsonPrimitive(TILES_CODE), value.getValue(ReplaySourceKeys.TILE_IDS)))
        HistoryDetailValueTypeKeys.ENTRIES -> JsonArray(
            listOf(
                JsonPrimitive(ENTRIES_CODE),
                array(value.getValue(ReplaySourceKeys.ENTRIES)).mapArray { raw ->
                    val entry = obj(raw)
                    val quantity = entry[ReplaySourceKeys.QUANTITY]?.takeUnless { it == JsonNull }?.let { encodeQuantity(obj(it)) }
                    JsonArray(listOf(entry.getValue(ReplaySourceKeys.ID)) + quantity.orEmpty())
                },
            ),
        )
        else -> error("Unsupported winner detail value type")
    }

    /**
     * 將有單位數值編碼為單位與數值兩個欄位。
     * @param value 保存的有單位數值。
     * @return 精簡陣列。
     */
    private fun encodeQuantity(value: JsonObject): JsonArray = JsonArray(listOf(value.getValue(ReplaySourceKeys.UNIT_ID), value.getValue(ReplaySourceKeys.AMOUNT)))

    /**
     * 還原有單位數值。
     * @param unitId 單位 ID。
     * @param amount 數值。
     * @return 明確的有單位數值物件。
     */
    private fun decodeQuantity(unitId: JsonElement, amount: JsonElement): JsonObject = JsonObject(mapOf(ReplaySourceKeys.UNIT_ID to unitId, ReplaySourceKeys.AMOUNT to amount))

    /**
     * 解碼固定值種類，拒絕錯誤陣列長度與未支援代碼。
     * @param value 精簡值。
     * @return 明確的明細值物件。
     */
    private fun decodeValue(value: JsonArray): JsonObject {
        require(value.size >= 2) { "Compact winner value is incomplete" }
        return when (value[0]) {
            JsonPrimitive(QUANTITIES_CODE) -> {
                require(value.size == 2) { "Compact quantity detail has unexpected fields" }
                JsonObject(
                    mapOf(
                        ReplaySourceKeys.TYPE to JsonPrimitive(HistoryDetailValueTypeKeys.QUANTITIES),
                        ReplaySourceKeys.QUANTITIES to array(value[1]).mapArray { raw ->
                            val quantity = array(raw)
                            require(quantity.size == 2) { "Compact winner quantity must contain two fields" }
                            decodeQuantity(quantity[0], quantity[1])
                        },
                    ),
                )
            }
            JsonPrimitive(TILES_CODE) -> {
                require(value.size == 2) { "Compact tile detail has unexpected fields" }
                JsonObject(mapOf(ReplaySourceKeys.TYPE to JsonPrimitive(HistoryDetailValueTypeKeys.TILES), ReplaySourceKeys.TILE_IDS to array(value[1])))
            }
            JsonPrimitive(ENTRIES_CODE) -> {
                require(value.size == 2) { "Compact entry detail has unexpected fields" }
                JsonObject(
                    mapOf(
                        ReplaySourceKeys.TYPE to JsonPrimitive(HistoryDetailValueTypeKeys.ENTRIES),
                        ReplaySourceKeys.ENTRIES to array(value[1]).mapArray { raw ->
                            val entry = array(raw)
                            require(entry.size == 1 || entry.size == 3) { "Compact winner entry has invalid fields" }
                            JsonObject(
                                mapOf(ReplaySourceKeys.ID to entry[0]) +
                                    if (entry.size == 3) mapOf(ReplaySourceKeys.QUANTITY to decodeQuantity(entry[1], entry[2])) else emptyMap(),
                            )
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

    /** 有單位數值固定代碼。 */
    private const val QUANTITIES_CODE = 0

    /** 牌參照值固定代碼。 */
    private const val TILES_CODE = 1

    /** 條目值固定代碼。 */
    private const val ENTRIES_CODE = 2
}
