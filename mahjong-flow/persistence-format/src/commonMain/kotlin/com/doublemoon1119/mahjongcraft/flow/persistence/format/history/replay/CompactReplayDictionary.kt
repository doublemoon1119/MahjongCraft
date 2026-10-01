package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 將 JSON 文件中的重複欄位名稱與長字串集中到可重用的字典。
 * 編碼格式使用 k、s、d 保存欄位字典、字串字典與資料樹；波浪號引用可無損還原。
 */
object CompactReplayDictionary {
    /**
     * 將文件編碼成含欄位字典、字串字典與資料樹的 JSON 封套。
     *
     * @param document 待字典化的完整 JSON 文件。
     * @return 可無損還原的字典封套。
     */
    fun encode(document: JsonElement): JsonObject {
        val frequencies = mutableMapOf<String, Int>()
        visitStrings(document) { value -> frequencies[value] = (frequencies[value] ?: 0) + 1 }
        val repeated = frequencies.filter { (value, count) -> count >= 2 && value.length >= 12 }.keys.sorted()
        val strings = repeated.withIndex().associate { (index, value) -> value to index }
        val keys = linkedMapOf<String, Int>()
        val data = compact(document, keys, strings)
        return JsonObject(
            linkedMapOf(
                ReplayFormatKeys.KEY_DICTIONARY to JsonArray(keys.keys.map(::JsonPrimitive)),
                ReplayFormatKeys.STRING_DICTIONARY to JsonArray(repeated.map(::JsonPrimitive)),
                ReplayFormatKeys.DATA to data,
            ),
        )
    }

    /**
     * 依封套字典還原原始文件；任何格式、型別或索引錯誤都會立即拒絕。
     *
     * @param encoded 包含欄位字典、字串字典與資料樹的封套。
     * @return 還原後的原始 JSON 文件。
     */
    fun decode(encoded: JsonObject): JsonElement {
        require(encoded.keys == setOf(ReplayFormatKeys.KEY_DICTIONARY, ReplayFormatKeys.STRING_DICTIONARY, ReplayFormatKeys.DATA)) {
            "Dictionary envelope must contain only k, s, and d"
        }
        val keys = readStrings(encoded.getValue(ReplayFormatKeys.KEY_DICTIONARY), "Key dictionary")
        val strings = readStrings(encoded.getValue(ReplayFormatKeys.STRING_DICTIONARY), "String dictionary")
        require(keys.distinct().size == keys.size) { "Key dictionary contains duplicate entries" }
        require(strings.distinct().size == strings.size) { "String dictionary contains duplicate entries" }
        return expand(encoded.getValue(ReplayFormatKeys.DATA), keys, strings)
    }

    /**
     * 驗證並讀取字典中的字串陣列。
     *
     * @param value 待驗證的 JSON 值。
     * @param name 用於錯誤訊息的字典名稱。
     * @return 字典中的有序字串。
     */
    private fun readStrings(value: JsonElement, name: String): List<String> {
        require(value is JsonArray) { "$name must be an array" }
        return value.map { item ->
            require(item is JsonPrimitive && item.isString) { "$name entries must be strings" }
            item.content
        }
    }

    /**
     * 遞迴走訪所有 JSON 字串值。
     *
     * @param value 待走訪的 JSON 節點。
     * @param emit 接收每個字串值的回呼。
     */
    private fun visitStrings(value: JsonElement, emit: (String) -> Unit) {
        when (value) {
            is JsonObject -> value.values.forEach { visitStrings(it, emit) }
            is JsonArray -> value.forEach { visitStrings(it, emit) }
            is JsonPrimitive -> if (value.isString) emit(value.content)
        }
    }

    /**
     * 遞迴替換欄位名稱與字串引用。
     *
     * @param value 待壓縮的 JSON 節點。
     * @param keys 欄位名稱到字典索引的可變對照。
     * @param strings 重複字串到字典索引的對照。
     * @return 使用字典索引的 JSON 節點。
     */
    private fun compact(value: JsonElement, keys: MutableMap<String, Int>, strings: Map<String, Int>): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.map { (key, child) -> keys.getOrPut(key) { keys.size }.toString() to compact(child, keys, strings) }.toMap())
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

    /**
     * 遞迴依字典還原欄位名稱與字串值。
     *
     * @param value 待還原的 JSON 節點。
     * @param keys 依索引排列的欄位名稱。
     * @param strings 依索引排列的字串值。
     * @return 展開字典引用後的 JSON 節點。
     */
    private fun expand(value: JsonElement, keys: List<String>, strings: List<String>): JsonElement = when (value) {
        is JsonObject -> {
            val result = linkedMapOf<String, JsonElement>()
            value.forEach { (encodedKey, child) ->
                val index = encodedKey.toIntOrNull()
                require(index != null && index in keys.indices) { "Invalid key dictionary index: $encodedKey" }
                require(result.put(keys[index], expand(child, keys, strings)) == null) { "Decoded object contains duplicate keys" }
            }
            JsonObject(result)
        }
        is JsonArray -> JsonArray(value.map { expand(it, keys, strings) })
        is JsonPrimitive -> when {
            !value.isString -> value
            value.content.startsWith("~~") -> JsonPrimitive(value.content.drop(1))
            value.content.startsWith("~") -> {
                val index = value.content.drop(1).toIntOrNull()
                require(index != null && index in strings.indices) { "Invalid string dictionary index: ${value.content}" }
                JsonPrimitive(strings[index])
            }
            else -> value
        }
    }
}
