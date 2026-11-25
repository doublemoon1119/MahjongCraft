package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * 載入器提供的封包收送方式中，伺服器與用戶端都會用到的部分。
 *
 * 每個頻道以 [ResourceLocation] 識別，封包內容是原始的 [FriendlyByteBuf]。
 */
interface PacketTransport {
    /**
     * 宣告一個頻道；連線時需要事先知道雙方有哪些頻道的載入器在這裡建立頻道。
     *
     * 每個頻道在收送任何封包之前宣告一次，伺服器與用戶端都會宣告。每個頻道只往一個方向送。
     *
     * @param channelId 頻道識別碼。
     * @param direction 頻道上的封包往哪個方向送。
     */
    fun declareChannel(channelId: ResourceLocation, direction: ChannelDirection)

    /**
     * 從伺服器送一個封包給玩家。
     *
     * @param player 收到封包的玩家。
     * @param channelId 頻道識別碼。
     * @param buf 封包內容。
     */
    fun sendToPlayer(player: ServerPlayer, channelId: ResourceLocation, buf: FriendlyByteBuf)

    /**
     * 登記伺服器收到這個頻道的封包時的處理。
     *
     * [receiver] 依載入器在網路執行緒或伺服器主執行緒上呼叫，封包內容只在呼叫期間有效。
     *
     * @param channelId 頻道識別碼。
     * @param receiver 收到伺服器、送出封包的玩家與封包內容。
     */
    fun registerServerReceiver(channelId: ResourceLocation, receiver: (MinecraftServer, ServerPlayer, FriendlyByteBuf) -> Unit)
}
