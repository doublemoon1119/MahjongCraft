package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證牌譜存放編碼的壓縮、有上限的解壓，以及損壞資料的判定。 */
class HistoryReplayPayloadCodecTest {
    /** 一般牌譜與含多位元組字元的內容壓縮後都能原樣還原。 */
    @Test
    fun `compressed replay restores the same text`() {
        listOf(
            "{\"rounds\":[[1,2,3],[4,5,6]],\"players\":[\"a\",\"b\"]}",
            "{\"name\":\"龍\",\"note\":\"東一局 😀\"}",
        ).forEach { json ->
            val stored = HistoryReplayPayloadCodec.compress(json)

            assertEquals(HistoryReplayPayloadRead.Found(json), HistoryReplayPayloadCodec.decompress(stored, json.encodeToByteArray().size))
        }
    }

    /** 解壓後剛好等於上限可讀，少一個位元組的上限即回報過大。 */
    @Test
    fun `limit applies to restored utf8 bytes`() {
        val json = "{\"name\":\"龍龍龍\"}"
        val bytes = json.encodeToByteArray().size
        val stored = HistoryReplayPayloadCodec.compress(json)

        assertEquals(HistoryReplayPayloadRead.Found(json), HistoryReplayPayloadCodec.decompress(stored, bytes))
        assertEquals(HistoryReplayPayloadRead.TooLarge, HistoryReplayPayloadCodec.decompress(stored, bytes - 1))
    }

    /** 壓縮比極高的資料以小上限解壓時停在上限，回報過大。 */
    @Test
    fun `highly compressible payload stops at the limit`() {
        val stored = HistoryReplayPayloadCodec.compress("x".repeat(BOMB_BYTES))
        assertTrue(stored.size < BOMB_LIMIT, "The fixture should be far smaller than the limit when stored")

        assertEquals(HistoryReplayPayloadRead.TooLarge, HistoryReplayPayloadCodec.decompress(stored, BOMB_LIMIT))
    }

    /** 不是 gzip、被截斷、CRC 不符或解壓後不是有效 UTF-8 的資料都回報無法還原。 */
    @Test
    fun `damaged stored bytes are unreadable`() {
        val stored = HistoryReplayPayloadCodec.compress("{\"payload\":true}")
        val crcTampered = stored.copyOf().also { it[it.size - CRC_OFFSET_FROM_END] = (it[it.size - CRC_OFFSET_FROM_END] + 1).toByte() }
        val invalidUtf8 = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(byteArrayOf(0xC3.toByte(), 0x28)) }
        }.toByteArray()

        listOf(
            "{\"payload\":true}".encodeToByteArray(),
            ByteArray(0),
            stored.copyOf(stored.size - 4),
            crcTampered,
            invalidUtf8,
        ).forEach { damaged ->
            assertIs<HistoryReplayPayloadRead.Unreadable>(HistoryReplayPayloadCodec.decompress(damaged, LIMIT))
        }
    }

    /** 測試用大小常數。 */
    private companion object {
        /** 一般測試的解壓上限。 */
        const val LIMIT = 1024

        /** 高壓縮比資料解壓後的大小。 */
        const val BOMB_BYTES = 4 * 1024 * 1024

        /** 高壓縮比資料的解壓上限。 */
        const val BOMB_LIMIT = 64 * 1024

        /** gzip 尾端 CRC32 欄位起點距結尾的位元組數。 */
        const val CRC_OFFSET_FROM_END = 8
    }
}
