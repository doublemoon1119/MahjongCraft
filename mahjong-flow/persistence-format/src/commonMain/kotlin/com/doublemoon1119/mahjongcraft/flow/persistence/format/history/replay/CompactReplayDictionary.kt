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
     * 建立可逐層選取欄位的字典讀取器，不展開未要求的子樹。
     *
     * @param encoded 已檢查輸入預算的字典封套。
     * @param budget 本次讀取共用的工作預算。
     * @return 使用同一套字典驗證與展開演算法的唯讀檢視。
     */
    internal fun open(encoded: JsonObject, budget: ReplayReadBudget): View {
        require(encoded.keys == setOf(ReplayFormatKeys.KEY_DICTIONARY, ReplayFormatKeys.STRING_DICTIONARY, ReplayFormatKeys.DATA)) {
            "Dictionary envelope must contain only k, s, and d"
        }
        val keys = readStrings(encoded.getValue(ReplayFormatKeys.KEY_DICTIONARY), "Key dictionary", budget)
        val strings = readStrings(encoded.getValue(ReplayFormatKeys.STRING_DICTIONARY), "String dictionary", budget)
        require(keys.distinct().size == keys.size) { "Key dictionary contains duplicate entries" }
        require(strings.distinct().size == strings.size) { "String dictionary contains duplicate entries" }
        return View(encoded.getValue(ReplayFormatKeys.DATA), keys, strings, budget)
    }

    /**
     * 未展開的資料樹與其已驗證字典。
     *
     * @property data 未展開的根資料。
     * @property keys 已驗證的欄位字典。
     * @property strings 已驗證的字串字典。
     * @property budget 共用工作預算。
     */
    internal class View(
        val data: JsonElement,
        private val keys: List<String>,
        private val strings: List<String>,
        private val budget: ReplayReadBudget,
    ) {
        /**
         * 解讀單層物件欄位名稱，保留子節點的字典編碼。
         * @param value 未展開的物件。
         * @return 已還原欄位名稱的單層唯讀物件。
         */
        fun fields(value: JsonElement): JsonObject {
            require(value is JsonObject) { "Dictionary object must be an object" }
            budget.charge(value.size.toLong() + 1)
            val result = linkedMapOf<String, JsonElement>()
            value.forEach { (key, child) ->
                budget.charge()
                val index = key.toIntOrNull()
                require(index != null && index in keys.indices) { "Invalid key dictionary index" }
                require(result.put(keys[index], child) == null) { "Decoded object contains duplicate keys" }
            }
            return JsonObject(result)
        }

        /**
         * 使用既有演算法還原指定子樹。
         * @param value 欲展開的資料節點。
         * @return 經預算檢查的還原資料。
         */
        fun expand(value: JsonElement): JsonElement = CompactReplayDictionary.expand(value, keys, strings, budget)
    }

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
     * 只還原資料根物件指定欄位，避免查詢設定時展開完整交易內容。
     *
     * @param encoded 包含欄位字典、字串字典與資料樹的封套。
     * @param fields 要還原的根欄位名稱。
     * @return 只包含要求欄位的 JSON object。
     */
    fun decodeFields(encoded: JsonObject, fields: Set<String>): JsonObject {
        require(encoded.keys == setOf(ReplayFormatKeys.KEY_DICTIONARY, ReplayFormatKeys.STRING_DICTIONARY, ReplayFormatKeys.DATA)) {
            "Dictionary envelope must contain only k, s, and d"
        }
        val keys = readStrings(encoded.getValue(ReplayFormatKeys.KEY_DICTIONARY), "Key dictionary")
        val strings = readStrings(encoded.getValue(ReplayFormatKeys.STRING_DICTIONARY), "String dictionary")
        require(keys.distinct().size == keys.size) { "Key dictionary contains duplicate entries" }
        require(strings.distinct().size == strings.size) { "String dictionary contains duplicate entries" }
        val data = encoded.getValue(ReplayFormatKeys.DATA) as? JsonObject ?: error("Dictionary data must be an object")
        val result = linkedMapOf<String, JsonElement>()
        val visitedKeys = mutableSetOf<String>()
        data.forEach { (encodedKey, value) ->
            val keyIndex = encodedKey.toIntOrNull()
            require(keyIndex != null && keyIndex in keys.indices) { "Invalid key dictionary index: $encodedKey" }
            val key = keys[keyIndex]
            require(visitedKeys.add(key)) { "Decoded object contains duplicate keys" }
            if (key in fields) result[key] = expand(value, keys, strings)
        }
        return JsonObject(result)
    }

    /**
     * 驗證並讀取字典中的字串陣列。
     *
     * @param value 待驗證的 JSON 值。
     * @param name 用於錯誤訊息的字典名稱。
     * @param budget 有界讀取預算；完整解碼時為 null。
     * @return 字典中的有序字串。
     */
    private fun readStrings(value: JsonElement, name: String, budget: ReplayReadBudget? = null): List<String> {
        require(value is JsonArray) { "$name must be an array" }
        budget?.charge(value.size.toLong() + 1)
        return value.map { item ->
            budget?.charge()
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
     * @param budget 有界讀取預算；完整解碼時為 null。
     * @return 展開字典引用後的 JSON 節點。
     */
    private fun expand(value: JsonElement, keys: List<String>, strings: List<String>, budget: ReplayReadBudget? = null): JsonElement {
        budget?.charge()
        budget?.expandedNode()
        return when (value) {
            is JsonObject -> {
                budget?.charge(value.size.toLong())
                val result = linkedMapOf<String, JsonElement>()
                value.forEach { (encodedKey, child) ->
                    val index = encodedKey.toIntOrNull()
                    require(index != null && index in keys.indices) { "Invalid key dictionary index: $encodedKey" }
                    budget?.expandedString(keys[index])
                    require(result.put(keys[index], expand(child, keys, strings, budget)) == null) { "Decoded object contains duplicate keys" }
                }
                JsonObject(result)
            }
            is JsonArray -> {
                budget?.charge(value.size.toLong())
                JsonArray(value.map { expand(it, keys, strings, budget) })
            }
            is JsonPrimitive -> {
                val expanded = when {
                    !value.isString -> value
                    value.content.startsWith("~~") -> JsonPrimitive(value.content.drop(1))
                    value.content.startsWith("~") -> {
                        val index = value.content.drop(1).toIntOrNull()
                        require(index != null && index in strings.indices) { "Invalid string dictionary index: ${value.content}" }
                        JsonPrimitive(strings[index])
                    }
                    else -> value
                }
                if (expanded.isString) budget?.expandedString(expanded.content)
                expanded
            }
        }
    }
}
