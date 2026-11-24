package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** 載入器提供的封包收送方式中，只在用戶端使用的部分。 */
interface ClientPacketTransport {
    /**
     * 從用戶端送一個封包給伺服器。
     *
     * @param channelId 頻道識別碼。
     * @param buf 封包內容。
     */
    fun sendToServer(channelId: ResourceLocation, buf: FriendlyByteBuf)

    /**
     * 登記用戶端收到這個頻道的封包時的處理。
     *
     * [receiver] 在網路執行緒上呼叫，封包內容只在呼叫期間有效。
     *
     * @param channelId 頻道識別碼。
     * @param receiver 收到送來封包的連線與封包內容。
     */
    fun registerClientReceiver(channelId: ResourceLocation, receiver: (ClientPacketListener, FriendlyByteBuf) -> Unit)
}
