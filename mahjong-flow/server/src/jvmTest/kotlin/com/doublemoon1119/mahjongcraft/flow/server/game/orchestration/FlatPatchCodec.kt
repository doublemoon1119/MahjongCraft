package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 測試用路徑字典差異：重複欄位路徑只存一次，未知欄位仍可由通用操作還原。
 *
 * 操作碼 0 為值替換、1 為欄位移除、2 為一般列表片段替換、3 為移除首項、
 * 4 為追加單項、5 為清空列表；解碼時驗證列表的原始長度。
 */
internal object FlatPatchCodec {
    /**
     * 將通用 JSON 差異攤平成路徑操作，並把新路徑加入 [paths]。
     *
     * @param patch 依前一狀態計算出的通用差異。
     * @param paths 同一場對局共用的有序路徑字典。
     * @return 依原差異順序排列的操作列表。
     */
    fun encode(patch: JsonElement, paths: MutableMap<List<JsonElement>, Int>): JsonArray {
        val operations = mutableListOf<JsonElement>()
        flatten(patch, emptyList(), paths, operations)
        return JsonArray(operations)
    }

    /**
     * 依序套用路徑操作，重建交易後的資料樹。
     *
     * @param before 交易前的資料樹。
     * @param operations 該交易的路徑操作。
     * @param paths 與編碼端順序一致的路徑字典。
     * @return 交易後的資料樹。
     */
    fun apply(before: JsonElement, operations: JsonArray, paths: List<List<JsonElement>>): JsonElement = operations.fold(before) { current, encoded ->
        val operation = encoded as JsonArray
        val path = paths[operation[0].jsonPrimitive.content.toInt()]
        update(current, path, operation)
    }

    /** 遞迴展開物件與列表差異，將葉節點轉為可索引操作。 */
    private fun flatten(
        patch: JsonElement,
        path: List<JsonElement>,
        paths: MutableMap<List<JsonElement>, Int>,
        output: MutableList<JsonElement>,
    ) {
        val node = patch as JsonObject
        node["_o"]?.let { children ->
            (children as JsonObject).forEach { (key, child) -> flatten(child, path + JsonPrimitive(key), paths, output) }
            return
        }
        node["_l"]?.let { children ->
            (children as JsonObject).forEach { (index, child) -> flatten(child, path + JsonPrimitive(index.toInt()), paths, output) }
            return
        }
        val pathId = JsonPrimitive(paths.getOrPut(path) { paths.size })
        when {
            "_v" in node -> output.add(JsonArray(listOf(pathId, JsonPrimitive(0), node.getValue("_v"))))
            "_x" in node -> output.add(JsonArray(listOf(pathId, JsonPrimitive(1))))
            "_a" in node -> {
                val splice = node.getValue("_a") as JsonArray
                val prefix = splice[0].jsonPrimitive.content.toInt()
                val removed = splice[1].jsonPrimitive.content.toInt()
                val inserted = splice[2] as JsonArray
                val suffix = splice[3].jsonPrimitive.content.toInt()
                val encoded = when {
                    removed == 0 && suffix == 0 && inserted.size == 1 ->
                        JsonArray(listOf(pathId, JsonPrimitive(4), JsonPrimitive(prefix), inserted[0]))
                    prefix == 0 && removed == 1 && inserted.isEmpty() ->
                        JsonArray(listOf(pathId, JsonPrimitive(3), JsonPrimitive(suffix)))
                    prefix == 0 && suffix == 0 && inserted.isEmpty() ->
                        JsonArray(listOf(pathId, JsonPrimitive(5), JsonPrimitive(removed)))
                    else -> JsonArray(listOf(pathId, JsonPrimitive(2)) + splice)
                }
                output.add(encoded)
            }
            else -> error("Unknown patch operation")
        }
    }

    /** 沿指定路徑更新單一節點；列表操作會驗證原列表長度。 */
    private fun update(value: JsonElement, path: List<JsonElement>, operation: JsonArray): JsonElement {
        if (path.isEmpty()) {
            return when (operation[1].jsonPrimitive.content.toInt()) {
                0 -> operation[2]
                2 -> {
                    val source = value as JsonArray
                    val prefix = operation[2].jsonPrimitive.content.toInt()
                    val removed = operation[3].jsonPrimitive.content.toInt()
                    val suffix = operation[5].jsonPrimitive.content.toInt()
                    require(prefix + removed + suffix == source.size)
                    JsonArray(source.take(prefix) + (operation[4] as JsonArray) + source.takeLast(suffix))
                }
                3 -> {
                    val source = value as JsonArray
                    require(source.size == operation[2].jsonPrimitive.content.toInt() + 1)
                    JsonArray(source.drop(1))
                }
                4 -> {
                    val source = value as JsonArray
                    require(source.size == operation[2].jsonPrimitive.content.toInt())
                    JsonArray(source + operation[3])
                }
                5 -> {
                    val source = value as JsonArray
                    require(source.size == operation[2].jsonPrimitive.content.toInt())
                    JsonArray(emptyList())
                }
                else -> error("Invalid leaf operation")
            }
        }
        val head = path.first()
        val tail = path.drop(1)
        if (value is JsonObject) {
            val key = head.jsonPrimitive.content
            val fields = value.toMutableMap()
            if (tail.isEmpty() && operation[1] == JsonPrimitive(1)) {
                fields.remove(key)
            } else {
                fields[key] = update(fields[key] ?: JsonNull, tail, operation)
            }
            return JsonObject(fields)
        }
        val source = value as JsonArray
        val index = head.jsonPrimitive.content.toInt()
        require(index in source.indices)
        return JsonArray(source.mapIndexed { position, entry -> if (position == index) update(entry, tail, operation) else entry })
    }
}
