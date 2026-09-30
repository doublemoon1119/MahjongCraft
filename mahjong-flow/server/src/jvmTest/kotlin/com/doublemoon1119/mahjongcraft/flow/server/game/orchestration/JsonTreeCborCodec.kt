package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * 僅供同一棵 JSON 資料樹的格式大小比較；實作 RFC 8949 所需的有限型別。
 *
 * 支援整數、雙精度浮點數、文字、布林值、null、定長列表與定長物件；
 * 不處理標籤、位元組字串、無定長容器或其他 CBOR 擴充型別。
 */
internal object JsonTreeCborCodec {
    /**
     * 將支援的 JSON 基本值、列表與物件編為 CBOR 位元組。
     *
     * @param value 待比較的 JSON 資料樹。
     * @return CBOR 編碼結果。
     */
    fun encode(value: JsonElement): ByteArray = ByteArrayOutputStream().also { encodeValue(it, value) }.toByteArray()

    /**
     * 解碼測試原型支援的 CBOR 型別，並拒絕尾端多餘位元組。
     *
     * @param bytes CBOR 編碼資料。
     * @return 對應的 JSON 資料樹。
     */
    fun decode(bytes: ByteArray): JsonElement {
        val input = ByteArrayInputStream(bytes)
        val result = decodeValue(input)
        require(input.available() == 0) { "Trailing CBOR bytes" }
        return result
    }

    /**
     * 依 JSON 節點種類寫入對應的 CBOR 主型別與內容。
     *
     * @param output 接收編碼位元組的串流。
     * @param value 待編碼的 JSON 節點。
     */
    private fun encodeValue(output: ByteArrayOutputStream, value: JsonElement) {
        when (value) {
            JsonNull -> output.write(0xf6)
            is JsonObject -> {
                writeHeader(output, 5, value.size.toLong())
                value.forEach { (key, entry) ->
                    writeString(output, key)
                    encodeValue(output, entry)
                }
            }
            is JsonArray -> {
                writeHeader(output, 4, value.size.toLong())
                value.forEach { encodeValue(output, it) }
            }
            is JsonPrimitive -> when {
                value.isString -> writeString(output, value.content)
                value.content == "true" -> output.write(0xf5)
                value.content == "false" -> output.write(0xf4)
                else -> {
                    val integer = value.content.toLongOrNull()
                    if (integer != null) {
                        if (integer >= 0) writeHeader(output, 0, integer) else writeHeader(output, 1, -(integer + 1))
                    } else {
                        output.write(0xfb)
                        writeUnsigned(output, value.content.toDouble().toBits(), 8)
                    }
                }
            }
        }
    }

    /**
     * 以 UTF-8 位元組長度及內容寫入 CBOR 文字字串。
     *
     * @param output 接收編碼位元組的串流。
     * @param value 待編碼的文字。
     */
    private fun writeString(output: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeHeader(output, 3, bytes.size.toLong())
        output.write(bytes)
    }

    /**
     * 使用能容納 [value] 的最短長度寫入 CBOR 項目標頭。
     *
     * @param output 接收編碼位元組的串流。
     * @param major CBOR 主型別編號。
     * @param value 標頭中的非負整數參數。
     */
    private fun writeHeader(output: ByteArrayOutputStream, major: Int, value: Long) {
        require(value >= 0)
        val prefix = major shl 5
        when {
            value < 24 -> output.write(prefix or value.toInt())
            value <= 0xff -> {
                output.write(prefix or 24)
                writeUnsigned(output, value, 1)
            }
            value <= 0xffff -> {
                output.write(prefix or 25)
                writeUnsigned(output, value, 2)
            }
            value <= 0xffffffffL -> {
                output.write(prefix or 26)
                writeUnsigned(output, value, 4)
            }
            else -> {
                output.write(prefix or 27)
                writeUnsigned(output, value, 8)
            }
        }
    }

    /**
     * 以大端序寫入指定寬度的無號數值位元組。
     *
     * @param output 接收編碼位元組的串流。
     * @param value 待寫入的數值。
     * @param size 寫入的位元組數。
     */
    private fun writeUnsigned(output: ByteArrayOutputStream, value: Long, size: Int) {
        for (shift in (size - 1) downTo 0) output.write((value ushr (shift * 8)).toInt() and 0xff)
    }

    /**
     * 讀取單一 CBOR 項目，並拒絕測試原型未支援的型別。
     *
     * @param input 待解碼的位元組串流。
     * @return 解碼後的 JSON 節點。
     */
    private fun decodeValue(input: ByteArrayInputStream): JsonElement {
        val first = input.read()
        require(first >= 0) { "Truncated CBOR" }
        val major = first ushr 5
        val additional = first and 31
        if (major == 7) {
            return when (additional) {
                20 -> JsonPrimitive(false)
                21 -> JsonPrimitive(true)
                22 -> JsonNull
                27 -> JsonPrimitive(Double.fromBits(readUnsigned(input, 8)))
                else -> error("Unsupported CBOR simple value: $additional")
            }
        }
        val size = readArgument(input, additional)
        return when (major) {
            0 -> JsonPrimitive(size)
            1 -> JsonPrimitive(-1L - size)
            3 -> JsonPrimitive(readBytes(input, checkedSize(size)).toString(Charsets.UTF_8))
            4 -> JsonArray(List(checkedSize(size)) { decodeValue(input) })
            5 -> {
                val entries = buildMap {
                    repeat(checkedSize(size)) {
                        val key = decodeValue(input) as? JsonPrimitive ?: error("Non-string CBOR map key")
                        require(key.isString) { "Non-string CBOR map key" }
                        put(key.content, decodeValue(input))
                    }
                }
                JsonObject(entries)
            }
            else -> error("Unsupported CBOR major type: $major")
        }
    }

    /**
     * 解析項目標頭中的附加資訊與後續無號整數。
     *
     * @param input 待解碼的位元組串流。
     * @param additional CBOR 標頭的附加資訊值。
     * @return 解析後的非負整數。
     */
    private fun readArgument(input: ByteArrayInputStream, additional: Int): Long = when (additional) {
        in 0..23 -> additional.toLong()
        24 -> readUnsigned(input, 1)
        25 -> readUnsigned(input, 2)
        26 -> readUnsigned(input, 4)
        27 -> readUnsigned(input, 8)
        else -> error("Unsupported CBOR additional information: $additional")
    }

    /**
     * 以大端序讀取固定寬度的無號數值位元組。
     *
     * @param input 待解碼的位元組串流。
     * @param size 讀取的位元組數。
     * @return 解析後的無號數值位元模式。
     */
    private fun readUnsigned(input: ByteArrayInputStream, size: Int): Long {
        var value = 0L
        repeat(size) {
            val byte = input.read()
            require(byte >= 0) { "Truncated CBOR integer" }
            value = (value shl 8) or byte.toLong()
        }
        return value
    }

    /**
     * 讀取指定長度的內容，資料不足時明確失敗。
     *
     * @param input 待解碼的位元組串流。
     * @param size 預期讀取的位元組數。
     * @return 讀取到的內容。
     */
    private fun readBytes(input: ByteArrayInputStream, size: Int): ByteArray = ByteArray(size).also {
        require(input.read(it) == size) { "Truncated CBOR byte string" }
    }

    /**
     * 驗證容器長度可安全轉為 JVM 列表使用的整數。
     *
     * @param value CBOR 項目宣告的容器長度。
     * @return 可供列表使用的整數長度。
     */
    private fun checkedSize(value: Long): Int {
        require(value in 0..Int.MAX_VALUE.toLong()) { "CBOR collection too large" }
        return value.toInt()
    }
}

/**
 * 計算一份位元組資料作為獨立 gzip 串流時的大小。
 *
 * @param bytes 待壓縮資料。
 * @return 包含 gzip 標頭與結尾的總位元組數。
 */
internal fun gzipSize(bytes: ByteArray): Int = ByteArrayOutputStream().also { output ->
    GZIPOutputStream(output).use { it.write(bytes) }
}.size()
