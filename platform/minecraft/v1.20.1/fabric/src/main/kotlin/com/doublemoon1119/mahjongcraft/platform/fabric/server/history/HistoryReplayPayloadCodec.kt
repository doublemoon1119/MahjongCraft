package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * `history_replay.payload` 的存放編碼：Replay JSON 的 UTF-8 位元組以 gzip（預設壓縮等級）壓縮後存成 BLOB。
 *
 * 解壓以串流進行並累計輸出大小，超過上限立即停止；不採信 gzip 尾端記錄的原始長度，也不依該長度預先配置緩衝區。
 */
internal object HistoryReplayPayloadCodec {
    /** 每次自解壓串流讀取的位元組數。 */
    private const val READ_BUFFER_BYTES = 16 * 1024

    /**
     * 把 Replay JSON 壓縮成存放格式。
     *
     * @param json Replay JSON 文字。
     * @return gzip 壓縮後的位元組。
     */
    fun compress(json: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(json.encodeToByteArray()) }
        return output.toByteArray()
    }

    /**
     * 在解壓後大小上限內還原 Replay JSON。
     *
     * @param stored 資料庫中的存放位元組。
     * @param maximumBytes 解壓後 UTF-8 位元組上限。
     * @return 還原的文字；解壓後超過上限時為 [HistoryReplayPayloadRead.TooLarge]；不是完整 gzip 資料、CRC 不符或不是
     *   有效 UTF-8 時為 [HistoryReplayPayloadRead.Unreadable]。
     */
    fun decompress(stored: ByteArray, maximumBytes: Int): HistoryReplayPayloadRead {
        require(maximumBytes > 0) { "History replay size limit must be positive" }
        val output = ByteArrayOutputStream(minOf(maximumBytes, READ_BUFFER_BYTES))
        try {
            GZIPInputStream(ByteArrayInputStream(stored)).use { input ->
                val buffer = ByteArray(READ_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size().toLong() + read > maximumBytes) return HistoryReplayPayloadRead.TooLarge
                    output.write(buffer, 0, read)
                }
            }
        } catch (error: IOException) {
            return HistoryReplayPayloadRead.Unreadable(error)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            HistoryReplayPayloadRead.Found(decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString())
        } catch (error: CharacterCodingException) {
            HistoryReplayPayloadRead.Unreadable(error)
        }
    }
}
