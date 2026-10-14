package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import io.netty.buffer.Unpooled
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import net.minecraft.network.PacketByteBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證有界 JSON payload 的長度、編碼與封包完整性檢查。 */
class BoundedPayloadTest {
    /**
     * 單局要求沿用正式 4 KiB buffer 防護，不能夾帶額外封包或超長配對鍵。
     */
    @Test
    fun `round replay requests share the bounded packet decoder`() {
        val events = HistoryRoundEventsRequestDto("events", Uuid.random().toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, startTransactionIndex = 0, limit = 20)
        val eventPayload = Json.encodeToString(HistoryRoundEventsRequestDto.serializer(), events)
        assertEquals(events, decodeBoundedPayload(bufferFor(eventPayload), Json, HistoryRoundEventsRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES))
        val trailing = bufferFor(eventPayload).also { it.writeByte(1) }
        assertNull(decodeBoundedPayload(trailing, Json, HistoryRoundEventsRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES))
        val state = HistoryRoundStateRequestDto("state", events.matchId, roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(4), scope = HistoryQueryScopeDto.OWN)
        val statePayload = Json.encodeToString(HistoryRoundStateRequestDto.serializer(), state)
        assertEquals(state, decodeBoundedPayload(bufferFor(statePayload), Json, HistoryRoundStateRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES))
        val oversized = Json.encodeToString(HistoryRoundEventsRequestDto.serializer(), events.copy(requestId = "界".repeat(2000)))
        assertNull(decodeBoundedPayload(bufferFor(oversized), Json, HistoryRoundEventsRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES))
    }

    /** 合法的長度前綴 JSON 應能完整解碼。 */
    @Test
    fun `valid dto decodes`() {
        val expected = AutomaticControlUpdateRequestDto("request", Uuid.random().toString(), 1L, emptySet())
        val buffer = bufferFor(Json.encodeToString(AutomaticControlUpdateRequestDto.serializer(), expected))

        assertEquals(
            expected,
            decodeBoundedPayload(buffer, Json, AutomaticControlUpdateRequestDto.serializer(), 1024),
        )
    }

    /** 未指定位元組上限時，保留原本的 Minecraft 字串字元上限。 */
    @Test
    fun `default compatibility limit accepts the old maximum character payload`() {
        val payload = "\"${"a".repeat((1 shl 20) - 2)}\""

        val decoded = decodeC2SPayload(bufferFor(payload), Json, String.serializer(), null)

        assertNotNull(decoded)
        assertEquals("a".repeat((1 shl 20) - 2), decoded.getOrThrow())
    }

    /** 未指定位元組上限時，多位元 UTF-8 內容仍沿用原本的字元上限與位元組接受範圍。 */
    @Test
    fun `default compatibility limit accepts multibyte payload`() {
        val value = "界".repeat((1 shl 20) - 2)
        val payload = Json.encodeToString(String.serializer(), value)

        val decoded = decodeC2SPayload(bufferFor(payload), Json, String.serializer(), null)

        assertNotNull(decoded)
        assertEquals(value, decoded.getOrThrow())
    }

    /** 可為 null 的 DTO 仍能區分合法 null 結果與被拒絕的封包。 */
    @Test
    fun `nullable dto keeps a successful null result`() {
        val decoded = decodeC2SPayload(bufferFor("null"), Json, String.serializer().nullable, null)

        assertNotNull(decoded)
        assertEquals(null, decoded.getOrThrow())
    }

    /** UTF-8 位元組超限時，即使字元數仍然較短，也必須拒絕。 */
    @Test
    fun `utf8 byte limit rejects multibyte payload`() {
        val payload = "\"界界界界\""

        assertNull(decodeBoundedPayload(bufferFor(payload), Json, String.serializer(), payload.toByteArray().size - 1))
    }

    /** 明確指定的歷史位元組上限在剛好符合時仍接受合法 payload。 */
    @Test
    fun `explicit byte limit accepts an exact payload`() {
        val payload = "\"history\""
        val limit = payload.toByteArray().size

        assertEquals("history", decodeBoundedPayload(bufferFor(payload), Json, String.serializer(), limit))
    }

    /** 原始 buffer 超過上限時應在讀取字串前拒絕。 */
    @Test
    fun `encoded buffer limit rejects before decoding`() {
        val buffer = PacketByteBuf(Unpooled.buffer())
        buffer.writeBytes(ByteArray(128))
        val serializer = CountingDeserializer()

        assertNull(decodeBoundedPayload(buffer, Json, serializer, 8))
        assertEquals(0, serializer.deserializeCalls)
    }

    /** JSON 語法錯誤時不得產生 DTO。 */
    @Test
    fun `malformed json is rejected`() {
        assertNull(decodeBoundedPayload(bufferFor("{bad"), Json, String.serializer(), 128))
    }

    /** 損壞的 VarInt 字串長度前綴應安全拒絕。 */
    @Test
    fun `damaged string prefix is rejected`() {
        val buffer = PacketByteBuf(Unpooled.buffer())
        buffer.writeBytes(ByteArray(6) { 0x80.toByte() })

        assertNull(decodeBoundedPayload(buffer, Json, String.serializer(), 128))
    }

    /** 合法字串後的額外位元組應被拒絕，避免封包拼接內容被忽略。 */
    @Test
    fun `trailing bytes are rejected`() {
        val buffer = bufferFor("\"valid\"")
        buffer.writeByte(1)

        assertNull(decodeBoundedPayload(buffer, Json, String.serializer(), 128))
    }

    /** 超出上限的 C2S payload 不得交給主執行緒排程函式。 */
    @Test
    fun `overbudget c2s payload does not dispatch`() {
        var dispatched = false
        val payload = "\"${"a".repeat(128)}\""

        val accepted = decodeAndDispatchC2S(
            buf = bufferFor(payload),
            json = Json,
            serializer = String.serializer(),
            maxPayloadBytes = 8,
        ) {
            dispatched = true
        }

        assertEquals(false, accepted)
        assertEquals(false, dispatched)
    }

    /** 通過完整驗證的 C2S payload 才會交給主執行緒排程函式。 */
    @Test
    fun `valid c2s payload dispatches once`() {
        var dispatched: String? = null

        val accepted = decodeAndDispatchC2S(
            buf = bufferFor("\"value\""),
            json = Json,
            serializer = String.serializer(),
            maxPayloadBytes = null,
        ) {
            dispatched = it
        }

        assertEquals(true, accepted)
        assertEquals("value", dispatched)
    }

    /** 解碼器取消時必須把取消例外交回呼叫端。 */
    @Test
    fun `cancellation from deserializer propagates`() {
        assertFailsWith<CancellationException> {
            decodeC2SPayload(bufferFor("\"value\""), Json, ThrowingDeserializer(CancellationException()), null)
        }
    }

    /** 致命錯誤不得被安全解碼邊界轉成一般拒絕。 */
    @Test
    fun `fatal error from deserializer propagates`() {
        assertFailsWith<AssertionError> {
            decodeC2SPayload(bufferFor("\"value\""), Json, ThrowingDeserializer(AssertionError()), null)
        }
    }

    /** 超過舊有字元上限的字串，不能因位元組仍有空間而被接受。 */
    @Test
    fun `default compatibility limit rejects one extra character`() {
        val payload = "\"${"a".repeat((1 shl 20) - 1)}\""
        assertNull(decodeC2SPayload(rawBufferFor(payload.toByteArray(Charsets.UTF_8)), Json, String.serializer(), null))
    }

    /** ASCII 與多位元內容都按實際 UTF-8 長度判斷顯式上限。 */
    @Test
    fun `explicit limit accepts exact utf8 size and rejects one byte less`() {
        for (value in listOf("history", "界界", "😀😀")) {
            val payload = Json.encodeToString(String.serializer(), value)
            val bytes = payload.toByteArray(Charsets.UTF_8).size
            assertEquals(value, decodeBoundedPayload(bufferFor(payload), Json, String.serializer(), bytes))
            assertNull(decodeBoundedPayload(bufferFor(payload), Json, String.serializer(), bytes - 1))
        }
    }

    /** 正式歷史要求在 4 KiB 邊界接受，超過一個位元組就拒絕。 */
    @Test
    fun `history request preserves its exact byte budget`() {
        val original = HistoryRoundEventsRequestDto("", Uuid.random().toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, startTransactionIndex = 0, limit = 20)
        val serializer = HistoryRoundEventsRequestDto.serializer()
        val overhead = Json.encodeToString(serializer, original).toByteArray(Charsets.UTF_8).size
        val exact = original.copy(requestId = "a".repeat(HistoryQueryLimits.REQUEST_BYTES - overhead))
        val exactPayload = Json.encodeToString(serializer, exact)

        assertEquals(HistoryQueryLimits.REQUEST_BYTES, exactPayload.toByteArray(Charsets.UTF_8).size)
        assertEquals(exact, decodeBoundedPayload(bufferFor(exactPayload), Json, serializer, HistoryQueryLimits.REQUEST_BYTES))
        val oversized = Json.encodeToString(serializer, exact.copy(requestId = exact.requestId + "a"))
        assertNull(decodeBoundedPayload(bufferFor(oversized), Json, serializer, HistoryQueryLimits.REQUEST_BYTES))
    }

    /** 各種損壞資料在預設與顯式上限下都不得交給主執行緒排程。 */
    @Test
    fun `malformed packets never dispatch under either limit policy`() {
        for (limit in listOf(null, HistoryQueryLimits.REQUEST_BYTES)) {
            val invalidBuffers = listOf(
                bufferFor("{bad"),
                bufferFor("{}"),
                bufferFor("{\"unknown\":true}"),
                bufferFor("\"valid\"").also { it.writeByte(1) },
                PacketByteBuf(Unpooled.buffer()).also { it.writeBytes(ByteArray(6) { 0x80.toByte() }) },
                PacketByteBuf(Unpooled.buffer()).also { it.writeVarInt(-1) },
                PacketByteBuf(Unpooled.buffer()).also {
                    it.writeVarInt(100)
                    it.writeByte(1)
                },
                PacketByteBuf(Unpooled.buffer()).also { it.writeByte(0x80) },
            )
            var dispatches = 0
            for (buffer in invalidBuffers) {
                assertEquals(false, decodeAndDispatchC2S(buffer, Json, AutomaticControlUpdateRequestDto.serializer(), limit) { dispatches += 1 })
            }
            assertEquals(0, dispatches)
        }
    }

    /** 聲明超限長度但實際資料很短時，仍會在建立字串與反序列化前拒絕。 */
    @Test
    fun `overbudget declared length never invokes deserializer`() {
        val serializer = CountingDeserializer()
        val buffer = PacketByteBuf(Unpooled.buffer()).also {
            it.writeVarInt(129)
            it.writeByte(1)
        }
        assertNull(decodeC2SPayload(buffer, Json, serializer, 128))
        assertEquals(0, serializer.deserializeCalls)
    }

    /** 合法 null DTO 必須正常排程，不與拒絕封包混淆。 */
    @Test
    fun `nullable dto dispatches successfully`() {
        var dispatches = 0
        assertEquals(
            expected = true,
            actual = decodeAndDispatchC2S(bufferFor("null"), Json, String.serializer().nullable, null) {
                assertNull(it)
                dispatches += 1
            },
        )
        assertEquals(1, dispatches)
    }

    /** 原生字串讀取會替換損壞的 UTF-8；本次保留該行為，不額外改變編碼政策。 */
    @Test
    fun `utf8 replacement matches native string decoding`() {
        val bytes = byteArrayOf(0x22, 0xC3.toByte(), 0x28, 0x22)
        val nativeJson = rawBufferFor(bytes).readString(1 shl 20)
        val expected = Json.decodeFromString(String.serializer(), nativeJson)
        assertEquals(expected, decodeC2SPayload(rawBufferFor(bytes), Json, String.serializer(), null)?.getOrThrow())
    }

    /**
     * 直接建立長度前綴與位元組內容，不套用寫入端的字串限制。
     *
     * @param bytes 要放入封包的原始字串位元組。
     * @return 可測試超限或損壞字串的 buffer。
     */
    private fun rawBufferFor(bytes: ByteArray): PacketByteBuf = PacketByteBuf(Unpooled.buffer()).also {
        it.writeVarInt(bytes.size)
        it.writeBytes(bytes)
    }

    /**
     * 建立含有 Minecraft 字串長度前綴的封包 buffer。
     *
     * @param payload 待驗證的 JSON 字串。
     * @return 可由接收端讀取的測試 buffer。
     */
    private fun bufferFor(payload: String): PacketByteBuf = PacketByteBuf(Unpooled.buffer()).also { it.writeString(payload, 1 shl 20) }

    /**
     * 讓測試可以驗證解碼例外不會被安全邊界錯誤吞掉的序列化器。
     *
     * @property failure 解碼時要重新拋出的取消或致命錯誤。
     */
    private class ThrowingDeserializer(private val failure: Throwable) : KSerializer<String> {
        /** 測試序列化器的字串描述。 */
        override val descriptor = PrimitiveSerialDescriptor("ThrowingString", PrimitiveKind.STRING)

        /**
         * 解碼時重新拋出測試指定的錯誤。
         *
         * @param decoder 本次測試不使用的解碼器。
         * @return 不會回傳；固定拋出指定錯誤。
         */
        override fun deserialize(decoder: Decoder): String = throw failure

        /**
         * 提供測試序列化器所需的反向編碼實作。
         *
         * @param encoder 寫入字串的編碼器。
         * @param value 要編碼的字串。
         */
        override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    }

    /** 記錄解碼次數，確認長度驗證會先於 DTO 反序列化執行。 */
    private class CountingDeserializer : KSerializer<String> {
        /** 反序列化器被呼叫的次數。 */
        var deserializeCalls: Int = 0

        /** 測試序列化器的字串描述。 */
        override val descriptor = PrimitiveSerialDescriptor("CountingString", PrimitiveKind.STRING)

        /**
         * 記錄呼叫後解碼輸入字串。
         *
         * @param decoder 讀取輸入字串的解碼器。
         * @return 解碼後的字串。
         */
        override fun deserialize(decoder: Decoder): String {
            deserializeCalls += 1
            return decoder.decodeString()
        }

        /**
         * 提供測試序列化器所需的反向編碼實作。
         *
         * @param encoder 寫入字串的編碼器。
         * @param value 要編碼的字串。
         */
        override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    }
}
