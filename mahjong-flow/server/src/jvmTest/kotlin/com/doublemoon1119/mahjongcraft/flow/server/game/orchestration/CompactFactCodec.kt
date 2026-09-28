package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 測試用可逆語意事件編碼。
 *
 * 事實與動作種類保存在對局字典；已接受動作拆出常見欄位，其他事實與未知 payload
 * 保留原始物件內容，不依規則名稱推測額外狀態變化。
 */
internal object CompactFactCodec {
    /**
     * 將已翻譯的語意事實編碼為帶種類索引的陣列。
     *
     * @param fact 含穩定種類名稱與完整 payload 的事實。
     * @param factTypes 同一場對局共用的事實種類字典。
     * @param actionTypes 同一場對局共用的動作種類字典。
     * @return 可由字典還原的事實陣列。
     */
    fun encode(
        fact: JsonObject,
        factTypes: MutableMap<String, Int>,
        actionTypes: MutableMap<String, Int>,
    ): JsonArray {
        val type = fact.getValue("type").jsonPrimitive.content
        val typeIndex = JsonPrimitive(factTypes.getOrPut(type) { factTypes.size })
        if (type == "action_accepted") {
            val action = fact.getValue("action") as JsonObject
            val actionType = action.getValue("type").jsonPrimitive.content
            val actionIndex = JsonPrimitive(actionTypes.getOrPut(actionType) { actionTypes.size })
            val payload = JsonObject(action.filterKeys { it != "type" })
            val extras = JsonObject(fact.filterKeys { it !in setOf("type", "action", "directTiles") })
            val directTiles = fact["directTiles"]
            if (payload.isEmpty() && extras.isEmpty()) {
                return JsonArray(listOf(typeIndex, actionIndex) + listOfNotNull(directTiles))
            }
            val fields = mutableListOf<JsonElement>(
                typeIndex,
                actionIndex,
                if (payload.isEmpty()) JsonNull else payload,
                directTiles ?: JsonNull,
                if (extras.isEmpty()) JsonNull else extras,
            )
            while (fields.last() == JsonNull) fields.removeAt(fields.lastIndex)
            return JsonArray(fields)
        }
        return JsonArray(listOf(typeIndex, JsonObject(fact.filterKeys { it != "type" })))
    }

    /**
     * 將事實陣列還原為含種類名稱的完整事實。
     *
     * @param encoded 由 [encode] 產生的事實陣列。
     * @param factTypes 與編碼端順序一致的事實種類名稱。
     * @param actionTypes 與編碼端順序一致的動作種類名稱。
     * @return 還原後的事實物件。
     */
    fun decode(encoded: JsonArray, factTypes: List<String>, actionTypes: List<String>): JsonObject {
        val type = factTypes[encoded[0].jsonPrimitive.content.toInt()]
        if (type == "action_accepted") {
            val simple = encoded.size == 2 || (encoded.size == 3 && encoded[2] is JsonArray)
            val action = linkedMapOf<String, JsonElement>("type" to JsonPrimitive(actionTypes[encoded[1].jsonPrimitive.content.toInt()]))
            if (!simple && encoded.size > 2 && encoded[2] != JsonNull) action.putAll(encoded[2] as JsonObject)
            val result = linkedMapOf<String, JsonElement>("type" to JsonPrimitive(type), "action" to JsonObject(action))
            val directTiles = if (simple) encoded.getOrNull(2) else encoded.getOrNull(3)
            if (directTiles != null && directTiles != JsonNull) result["directTiles"] = directTiles
            if (!simple && encoded.size > 4 && encoded[4] != JsonNull) result.putAll(encoded[4] as JsonObject)
            return JsonObject(result)
        }
        return JsonObject(mapOf("type" to JsonPrimitive(type)) + (encoded[1] as JsonObject))
    }
}
