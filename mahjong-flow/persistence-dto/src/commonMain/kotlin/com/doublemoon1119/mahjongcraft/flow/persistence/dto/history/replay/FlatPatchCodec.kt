package com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.replay

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 將 JSON 差異攤平成路徑操作，並以原列表長度防止錯誤基底的靜默套用。 */
object FlatPatchCodec {
    /**
     * 編碼差異並依首次出現順序更新共用路徑字典。
     *
     * @param patch 待編碼的結構差異。
     * @param paths 路徑到字典索引的可變對照。
     * @return 依執行順序排列的精簡操作。
     */
    fun encode(patch: JsonElement, paths: MutableMap<List<JsonElement>, Int>): JsonArray {
        val output = mutableListOf<JsonElement>()
        flatten(patch, emptyList(), paths, output)
        return JsonArray(output)
    }

    /**
     * 依序套用操作；操作格式或基底不符時立即拒絕。
     *
     * @param before 套用差異前的 JSON 節點。
     * @param operations 依執行順序排列的精簡操作。
     * @param paths 依索引排列的操作路徑。
     * @return 套用所有操作後的 JSON 節點。
     */
    fun apply(before: JsonElement, operations: JsonArray, paths: List<List<JsonElement>>): JsonElement = operations.fold(before) { current, encodedElement ->
        val encoded = encodedElement as? JsonArray ?: error("Patch operation must be an array")
        require(encoded.size >= 2) { "Patch operation must contain at least two elements" }
        val id = nonNegativeInt(encoded[0], "Path index")
        require(id in paths.indices) { "Invalid path index" }
        update(current, paths[id], encoded)
    }

    /**
     * 遞迴展開物件與列表差異為葉節點操作。
     *
     * @param patch 待展開的差異節點。
     * @param path 目前節點相對於根節點的路徑。
     * @param paths 路徑到字典索引的可變對照。
     * @param output 收集編碼操作的目標列表。
     */
    private fun flatten(patch: JsonElement, path: List<JsonElement>, paths: MutableMap<List<JsonElement>, Int>, output: MutableList<JsonElement>) {
        require(patch is JsonObject) { "Patch node must be an object" }
        patch[ReplayPatchKeys.OBJECT]?.let { children ->
            require(children is JsonObject)
            children.forEach { (key, child) -> flatten(child, path + JsonPrimitive(key), paths, output) }
            return
        }
        patch[ReplayPatchKeys.LIST]?.let { children ->
            require(children is JsonObject)
            children.forEach { (index, child) ->
                require(index.toIntOrNull()?.let { it >= 0 } == true)
                flatten(child, path + JsonPrimitive(index), paths, output)
            }
            return
        }
        val pathId = JsonPrimitive(paths.getOrPut(path) { paths.size })
        when {
            ReplayPatchKeys.VALUE in patch && patch.keys == setOf(ReplayPatchKeys.VALUE) ->
                output.add(JsonArray(listOf(pathId, JsonPrimitive(0), patch.getValue(ReplayPatchKeys.VALUE))))
            ReplayPatchKeys.DELETE in patch && patch.keys == setOf(ReplayPatchKeys.DELETE) -> output.add(JsonArray(listOf(pathId, JsonPrimitive(1))))
            ReplayPatchKeys.SPLICE in patch && patch.keys == setOf(ReplayPatchKeys.SPLICE) -> {
                val splice = patch.getValue(ReplayPatchKeys.SPLICE) as? JsonArray ?: error("_a must be an array")
                require(splice.size == 4)
                val prefix = nonNegativeInt(splice[0], "prefix")
                val removed = nonNegativeInt(splice[1], "removed")
                val inserted = splice[2] as? JsonArray ?: error("inserted must be an array")
                val suffix = nonNegativeInt(splice[3], "suffix")
                output.add(
                    when {
                        removed == 0 && suffix == 0 && inserted.size == 1 -> JsonArray(listOf(pathId, JsonPrimitive(4), JsonPrimitive(prefix), inserted[0]))
                        prefix == 0 && removed == 1 && inserted.isEmpty() -> JsonArray(listOf(pathId, JsonPrimitive(3), JsonPrimitive(suffix)))
                        prefix == 0 && suffix == 0 && inserted.isEmpty() -> JsonArray(listOf(pathId, JsonPrimitive(5), JsonPrimitive(removed)))
                        else -> JsonArray(listOf(pathId, JsonPrimitive(2)) + splice)
                    },
                )
            }
            else -> error("Unknown patch operation or unexpected fields")
        }
    }

    /**
     * 沿路徑更新單一節點並驗證列表原始長度。
     *
     * @param value 目前的 JSON 節點。
     * @param path 從目前節點到更新位置的路徑。
     * @param operation 待執行的精簡操作。
     * @return 更新後的 JSON 節點。
     */
    private fun update(value: JsonElement, path: List<JsonElement>, operation: JsonArray): JsonElement {
        val opcode = nonNegativeInt(operation[1], "Opcode")
        if (path.isEmpty()) {
            return when (opcode) {
                0 -> {
                    require(operation.size == 3)
                    operation[2]
                }
                2 -> {
                    require(operation.size == 6)
                    val source = value as? JsonArray ?: error("Target is not an array")
                    val prefix = nonNegativeInt(operation[2], "prefix")
                    val removed = nonNegativeInt(operation[3], "removed")
                    val suffix = nonNegativeInt(operation[5], "suffix")
                    require(prefix + removed + suffix == source.size)
                    JsonArray(source.take(prefix) + (operation[4] as? JsonArray ?: error("inserted must be an array")) + source.takeLast(suffix))
                }
                3 -> {
                    require(operation.size == 3)
                    val source = value as? JsonArray ?: error("Target is not an array")
                    val suffix = nonNegativeInt(operation[2], "suffix")
                    require(source.size == suffix + 1)
                    JsonArray(source.drop(1))
                }
                4 -> {
                    require(operation.size == 4)
                    val source = value as? JsonArray ?: error("Target is not an array")
                    val prefix = nonNegativeInt(operation[2], "prefix")
                    require(source.size == prefix)
                    JsonArray(source + operation[3])
                }
                5 -> {
                    require(operation.size == 3)
                    val source = value as? JsonArray ?: error("Target is not an array")
                    val removed = nonNegativeInt(operation[2], "removed")
                    require(source.size == removed)
                    JsonArray(emptyList())
                }
                else -> error("Invalid opcode: $opcode")
            }
        }
        val head = path.first()
        val tail = path.drop(1)
        if (value is JsonObject) {
            val key = head as? JsonPrimitive ?: error("Object path segment must be a string")
            val fields = value.toMutableMap()
            if (tail.isEmpty() && opcode == 1) {
                require(operation.size == 2)
                fields.remove(key.content)
            } else {
                fields[key.content] = update(fields[key.content] ?: JsonNull, tail, operation)
            }
            return JsonObject(fields)
        }
        val source = value as? JsonArray ?: error("Path target is neither an object nor an array")
        val index = nonNegativeInt(head, "Array path index")
        require(index in source.indices)
        return JsonArray(source.mapIndexed { position, entry -> if (position == index) update(entry, tail, operation) else entry })
    }

    /**
     * 讀取不可為負的整數操作欄位。
     *
     * @param value 待解析的 JSON 值。
     * @param name 用於錯誤訊息的欄位名稱。
     * @return 非負整數欄位值。
     */
    private fun nonNegativeInt(value: JsonElement, name: String): Int {
        val primitive = value as? JsonPrimitive ?: error("$name must be an integer")
        val result = primitive.content.toIntOrNull() ?: error("$name must be an integer")
        require(result >= 0) { "$name must not be negative" }
        return result
    }
}
