package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 將語意事實與已接受動作拆成可由種類字典還原的緊湊陣列。 */
object CompactFactCodec {
    /**
     * 編碼事實，並依首次出現順序更新事實種類與動作種類字典。
     *
     * @param fact 待編碼的語意事實。
     * @param factTypes 語意事實種類到字典索引的可變對照。
     * @param actionTypes 動作種類到字典索引的可變對照。
     * @return 使用種類索引的精簡事實陣列。
     */
    fun encode(fact: JsonObject, factTypes: MutableMap<String, Int>, actionTypes: MutableMap<String, Int>): JsonArray {
        val type = (fact[ReplaySourceKeys.TYPE] as? JsonPrimitive)?.content ?: error("Fact is missing a string type")
        val typeIndex = JsonPrimitive(factTypes.getOrPut(type) { factTypes.size })
        if (type == HistoryFactTypeKeys.ACTION_ACCEPTED) {
            val action = fact[ReplaySourceKeys.ACTION] as? JsonObject ?: error("Fact is missing an action object")
            val actionType = (action[ReplaySourceKeys.TYPE] as? JsonPrimitive)?.content ?: error("Action is missing a string type")
            val actionIndex = JsonPrimitive(actionTypes.getOrPut(actionType) { actionTypes.size })
            val payload = JsonObject(action.filterKeys { it != ReplaySourceKeys.TYPE })
            val extras = JsonObject(fact.filterKeys { it !in setOf(ReplaySourceKeys.TYPE, ReplaySourceKeys.ACTION, ReplaySourceKeys.DIRECT_TILES) })
            val directTiles = fact[ReplaySourceKeys.DIRECT_TILES]
            require(directTiles == null || directTiles is JsonArray) { "directTiles must be an array" }
            if (payload.isEmpty() && extras.isEmpty()) return JsonArray(listOf(typeIndex, actionIndex) + listOfNotNull(directTiles))
            val fields = mutableListOf<JsonElement>(typeIndex, actionIndex, if (payload.isEmpty()) JsonNull else payload, directTiles ?: JsonNull, if (extras.isEmpty()) JsonNull else extras)
            while (fields.last() == JsonNull) fields.removeAt(fields.lastIndex)
            return JsonArray(fields)
        }
        val payload = fact.filterKeys { it != ReplaySourceKeys.TYPE }.toMutableMap()
        if (type in WIN_DETAIL_FACT_TYPES) payload[ReplaySourceKeys.WIN_DETAILS]?.let { payload[ReplaySourceKeys.WIN_DETAILS] = CompactWinDetailsCodec.encode(it) }
        return JsonArray(listOf(typeIndex, JsonObject(payload)))
    }

    /**
     * 解碼事實；字典索引、陣列長度與 payload 型別錯誤均會被拒絕。
     *
     * @param encoded 待解碼的精簡事實陣列。
     * @param factTypes 依索引排列的語意事實種類。
     * @param actionTypes 依索引排列的動作種類。
     * @return 還原後的語意事實。
     */
    fun decode(encoded: JsonArray, factTypes: List<String>, actionTypes: List<String>): JsonObject {
        require(encoded.size >= 2)
        val type = requireNotNull(factTypes.getOrNull(index(encoded[0]))) { "Invalid fact type index" }
        if (type != HistoryFactTypeKeys.ACTION_ACCEPTED) {
            require(encoded.size == 2)
            val payload = encoded[1] as? JsonObject ?: error("Fact payload must be an object")
            require(ReplaySourceKeys.TYPE !in payload) { "Fact payload must not override its type" }
            val restored = payload.toMutableMap()
            if (type in WIN_DETAIL_FACT_TYPES) restored[ReplaySourceKeys.WIN_DETAILS]?.let { restored[ReplaySourceKeys.WIN_DETAILS] = CompactWinDetailsCodec.decode(it) }
            return JsonObject(linkedMapOf(ReplaySourceKeys.TYPE to JsonPrimitive(type)) + restored)
        }
        val actionType = requireNotNull(actionTypes.getOrNull(index(encoded[1]))) { "Invalid action type index" }
        val simple = encoded.size == 2 || (encoded.size == 3 && encoded[2] is JsonArray)
        require(encoded.size <= if (simple) 3 else 5)
        val action = linkedMapOf<String, JsonElement>(ReplaySourceKeys.TYPE to JsonPrimitive(actionType))
        if (!simple && encoded.size > 2 && encoded[2] != JsonNull) {
            val payload = encoded[2] as? JsonObject ?: error("Action payload must be an object")
            require(ReplaySourceKeys.TYPE !in payload) { "Action payload must not override its type" }
            action.putAll(payload)
        }
        val result = linkedMapOf<String, JsonElement>(ReplaySourceKeys.TYPE to JsonPrimitive(type), ReplaySourceKeys.ACTION to JsonObject(action))
        val directTiles = if (simple) encoded.getOrNull(2) else encoded.getOrNull(3)
        if (directTiles != null && directTiles != JsonNull) {
            require(directTiles is JsonArray)
            result[ReplaySourceKeys.DIRECT_TILES] = directTiles
        }
        if (!simple && encoded.size > 4 && encoded[4] != JsonNull) {
            val extras = encoded[4] as? JsonObject ?: error("Fact extras must be an object")
            require(extras.keys.none { it in setOf(ReplaySourceKeys.TYPE, ReplaySourceKeys.ACTION, ReplaySourceKeys.DIRECT_TILES) }) { "Fact extras must not override structural fields" }
            result.putAll(extras)
        }
        return JsonObject(result)
    }

    /**
     * 讀取不可為負的字典索引。
     *
     * @param value 待驗證的 JSON 值。
     * @return 非負整數索引。
     */
    private fun index(value: JsonElement): Int {
        val result = requireNotNull((value as? JsonPrimitive)?.content?.toIntOrNull()) { "Index must be an integer" }
        require(result >= 0)
        return result
    }

    /** 由內建契約持有和牌明細的事實種類；不解析第三方任意同名欄位。 */
    private val WIN_DETAIL_FACT_TYPES = setOf(HistoryFactTypeKeys.WIN_SETTLED, HistoryFactTypeKeys.RULE_EFFECT_RESOLVED)
}
