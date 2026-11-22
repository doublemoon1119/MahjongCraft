package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientPacketTransport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModPackets
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.PacketTransport
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledNetworkJson
import kotlinx.serialization.builtins.serializer
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證頻道透過載入器的收送方式宣告頻道與送出要求。 */
class ChannelTransportTest {
    /** 記錄宣告過的頻道。 */
    private val transport = RecordingPacketTransport()

    /** 記錄用戶端送出的封包。 */
    private val clientTransport = RecordingClientPacketTransport()

    /** 每個測試使用新的記錄用收送方式。 */
    @BeforeTest
    fun installTransports() {
        ModPackets.install(transport)
        ModPackets.installClient(clientTransport)
    }

    /** 頻道建立時就向收送方式宣告自己。 */
    @Test
    fun `channels declare themselves when created`() {
        val request = C2SChannel(id = "test_request", serializer = String.serializer(), rateLimit = C2SRateLimit.HandledByReceiver)
        val response = S2CChannel(id = "test_response", serializer = String.serializer())

        assertEquals(listOf(request.channelId, response.channelId), transport.declared)
    }

    /** 用戶端送出的要求能被伺服器端的解碼完整還原。 */
    @Test
    fun `requests sent to the server decode back to the same value`() {
        val channel = C2SChannel(
            id = "test_request",
            serializer = String.serializer(),
            rateLimit = C2SRateLimit.HandledByReceiver,
            maxPayloadBytes = 64,
        )

        channel.sendToServer(bundledNetworkJson(), "麻將")

        val (channelId, buf) = clientTransport.sent.single()
        assertEquals(channel.channelId, channelId)
        assertEquals("麻將", decodeBoundedPayload(buf, bundledNetworkJson(), String.serializer(), maxPayloadBytes = 64))
    }

    /** 超過大小上限的要求在送出前就被擋下。 */
    @Test
    fun `oversized requests are rejected before sending`() {
        val channel = C2SChannel(
            id = "test_request",
            serializer = String.serializer(),
            rateLimit = C2SRateLimit.HandledByReceiver,
            maxPayloadBytes = 8,
        )

        assertFailsWith<IllegalArgumentException> { channel.sendToServer(bundledNetworkJson(), "超過上限的內容") }
        assertTrue(clientTransport.sent.isEmpty())
    }

    /** 只記錄宣告的頻道；這些測試不經過伺服器端的收送。 */
    private class RecordingPacketTransport : PacketTransport {
        /** 依宣告順序記錄的頻道。 */
        val declared = mutableListOf<ResourceLocation>()

        override fun declareChannel(channelId: ResourceLocation) {
            declared += channelId
        }

        override fun sendToPlayer(player: ServerPlayer, channelId: ResourceLocation, buf: FriendlyByteBuf) = error("Not used in these tests")

        override fun registerServerReceiver(channelId: ResourceLocation, receiver: (MinecraftServer, ServerPlayer, FriendlyByteBuf) -> Unit) = error("Not used in these tests")
    }

    /** 記錄用戶端送出的封包。 */
    private class RecordingClientPacketTransport : ClientPacketTransport {
        /** 依送出順序記錄的頻道與封包內容。 */
        val sent = mutableListOf<Pair<ResourceLocation, FriendlyByteBuf>>()

        override fun sendToServer(channelId: ResourceLocation, buf: FriendlyByteBuf) {
            sent += channelId to buf
        }

        override fun registerClientReceiver(channelId: ResourceLocation, receiver: (ClientPacketListener, FriendlyByteBuf) -> Unit) = error("Not used in these tests")
    }
}
