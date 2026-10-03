package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import io.netty.buffer.Unpooled
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.minecraft.network.PacketByteBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證有界 JSON payload 的長度、編碼與封包完整性檢查。 */
class BoundedPayloadTest {
    /** 單局要求沿用正式 4 KiB buffer 防護，不能夾帶額外封包或超長配對鍵。
     */
    @Test
    fun `round replay requests share the bounded packet decoder`() {
        val events = HistoryRoundEventsRequestDto("events", Uuid.random().toString(), roundNumber = 1)
        val eventPayload = Json.encodeToString(HistoryRoundEventsRequestDto.serializer(), events)
        assertEquals(events, decodeBoundedPayload(bufferFor(eventPayload), Json, HistoryRoundEventsRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES))
        val trailing = bufferFor(eventPayload).also { it.writeByte(1) }
        assertNull(decodeBoundedPayload(trailing, Json, HistoryRoundEventsRequestDto.serializer(), HistoryQueryLimits.REQUEST_BYTES))
        val state = HistoryRoundStateRequestDto("state", events.matchId, roundNumber = 1, position = HistoryRoundPositionDto.AfterTransaction(4))
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

    /** UTF-8 位元組超限時，即使字元數仍然較短，也必須拒絕。 */
    @Test
    fun `utf8 byte limit rejects multibyte payload`() {
        val payload = "\"界界界界\""

        assertNull(decodeBoundedPayload(bufferFor(payload), Json, String.serializer(), payload.toByteArray().size - 1))
    }

    /** 原始 buffer 超過上限時應在讀取字串前拒絕。 */
    @Test
    fun `encoded buffer limit rejects before decoding`() {
        val buffer = PacketByteBuf(Unpooled.buffer())
        buffer.writeBytes(ByteArray(128))

        assertNull(decodeBoundedPayload(buffer, Json, String.serializer(), 8))
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

    /**
     * 建立含有 Minecraft 字串長度前綴的封包 buffer。
     *
     * @param payload 待驗證的 JSON 字串。
     * @return 可由接收端讀取的測試 buffer。
     */
    private fun bufferFor(payload: String): PacketByteBuf = PacketByteBuf(Unpooled.buffer()).also { it.writeString(payload) }
}
