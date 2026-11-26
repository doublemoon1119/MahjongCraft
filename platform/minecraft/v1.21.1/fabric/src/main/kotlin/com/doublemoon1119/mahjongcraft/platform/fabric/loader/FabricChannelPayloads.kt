package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ChannelDirection
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 本 mod 每個頻道的封包型別。
 *
 * - 這個版本的 Fabric API 要求每個頻道先登記成封包型別。
 * - 封包原樣攜帶共用程式寫好的位元組，頻道的編碼仍由共用程式負責。
 */
internal object FabricChannelPayloads {
    /** 已宣告的頻道與它的封包型別。 */
    private val types = ConcurrentHashMap<ResourceLocation, CustomPacketPayload.Type<ChannelPayload>>()

    /**
     * 把頻道登記成指定方向的封包型別。
     *
     * @param channelId 頻道識別碼。
     * @param direction 封包往哪個方向送。
     */
    fun declare(channelId: ResourceLocation, direction: ChannelDirection) {
        val type = CustomPacketPayload.Type<ChannelPayload>(channelId)
        check(types.putIfAbsent(channelId, type) == null) { "Channel $channelId is declared twice" }
        val codec = StreamCodec.of<RegistryFriendlyByteBuf, ChannelPayload>(
            { buf, payload -> buf.writeBytes(payload.bytes) },
            { buf -> ChannelPayload(type, ByteArray(buf.readableBytes()).also(buf::readBytes)) },
        )
        when (direction) {
            ChannelDirection.TO_CLIENT -> PayloadTypeRegistry.playS2C().register(type, codec)
            ChannelDirection.TO_SERVER -> PayloadTypeRegistry.playC2S().register(type, codec)
        }
    }

    /**
     * 頻道的封包型別。
     *
     * @param channelId 已宣告的頻道識別碼。
     * @return 封包型別。
     */
    fun type(channelId: ResourceLocation): CustomPacketPayload.Type<ChannelPayload> = checkNotNull(types[channelId]) { "Channel $channelId is not declared" }

    /**
     * 把寫好的封包內容包成這個頻道的封包。
     *
     * @param channelId 已宣告的頻道識別碼。
     * @param buf 封包內容。從目前的讀取位置讀到結尾。
     * @return 封包。
     */
    fun wrap(channelId: ResourceLocation, buf: FriendlyByteBuf): ChannelPayload = ChannelPayload(
        payloadType = type(channelId),
        bytes = ByteArray(buf.readableBytes()).also(buf::readBytes),
    )

    /**
     * 取出封包內容，交給共用程式讀取。
     *
     * @param payload 收到的封包。
     * @return 封包內容。
     */
    fun unwrap(payload: ChannelPayload): FriendlyByteBuf = FriendlyByteBuf(Unpooled.wrappedBuffer(payload.bytes))
}

/**
 * 一個頻道的封包，原樣攜帶共用程式寫好的位元組。
 *
 * @property payloadType 頻道的封包型別。
 * @property bytes 封包內容。
 */
internal class ChannelPayload(private val payloadType: CustomPacketPayload.Type<ChannelPayload>, val bytes: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ChannelPayload> = payloadType
}
