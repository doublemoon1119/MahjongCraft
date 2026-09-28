package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 測試用無損字典；重複的欄位名稱與長字串值在封套中只寫一次。
 *
 * 字串引用以波浪號開頭；原本以波浪號開頭的值會增加一個波浪號，避免與引用衝突。
 */
internal object CompactReplayDictionary {
    /**
     * 將重複欄位名稱與長字串收進文件表頭，並編碼原資料樹。
     *
     * @param document 待編碼的完整資料樹。
     * @return 含欄位字典、字串字典及編碼資料的封套。
     */
    fun encode(document: JsonElement): JsonObject {
        val frequencies = mutableMapOf<String, Int>()
        visitStrings(document) { value -> frequencies.merge(value, 1, Int::plus) }
        val repeated = frequencies.filter { (value, count) -> count >= 2 && value.length >= 12 }
            .keys.sorted()
        val strings = repeated.withIndex().associate { (index, value) -> value to index }
        val keys = linkedMapOf<String, Int>()
        val data = compact(document, keys, strings)
        return JsonObject(
            mapOf(
                "k" to JsonArray(keys.keys.map(::JsonPrimitive)),
                "s" to JsonArray(repeated.map(::JsonPrimitive)),
                "d" to data,
            ),
        )
    }

    /**
     * 依封套內的字典還原資料樹。
     *
     * @param encoded 由 [encode] 產生的封套。
     * @return 原始欄位名稱與字串值構成的資料樹。
     */
    fun decode(encoded: JsonObject): JsonElement {
        val keys = (encoded.getValue("k") as JsonArray).map { it.jsonPrimitive.content }
        val strings = (encoded.getValue("s") as JsonArray).map { it.jsonPrimitive.content }
        return expand(encoded.getValue("d"), keys, strings)
    }

    /** 走訪所有字串值以計算重複次數；物件欄位名稱另由欄位字典處理。 */
    private fun visitStrings(value: JsonElement, emit: (String) -> Unit) {
        when (value) {
            is JsonObject -> value.values.forEach { visitStrings(it, emit) }
            is JsonArray -> value.forEach { visitStrings(it, emit) }
            is JsonPrimitive -> if (value.isString) emit(value.content)
        }
    }

    /** 遞迴編碼欄位名稱與字串值，並跳脫原本以波浪號開頭的字串。 */
    private fun compact(value: JsonElement, keys: MutableMap<String, Int>, strings: Map<String, Int>): JsonElement = when (value) {
        is JsonObject -> JsonObject(
            value.map { (key, child) -> keys.getOrPut(key) { keys.size }.toString() to compact(child, keys, strings) }.toMap(),
        )
        is JsonArray -> JsonArray(value.map { compact(it, keys, strings) })
        is JsonPrimitive -> {
            val index = if (value.isString) strings[value.content] else null
            when {
                index != null -> JsonPrimitive("~$index")
                value.isString && value.content.startsWith("~") -> JsonPrimitive("~${value.content}")
                else -> value
            }
        }
    }

    /** 依欄位與字串字典逆向還原資料樹。 */
    private fun expand(value: JsonElement, keys: List<String>, strings: List<String>): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.map { (key, child) -> keys[key.toInt()] to expand(child, keys, strings) }.toMap())
        is JsonArray -> JsonArray(value.map { expand(it, keys, strings) })
        is JsonPrimitive -> when {
            !value.isString -> value
            value.content.startsWith("~~") -> JsonPrimitive(value.content.drop(1))
            value.content.startsWith("~") -> JsonPrimitive(strings[value.content.drop(1).toInt()])
            else -> value
        }
    }
}
