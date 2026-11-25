package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ChannelDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.PacketTransport
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.NetworkEvent

/** 以 Forge 的事件頻道實作 [PacketTransport]；封包以原版的自訂資料封包傳送。 */
object ForgePacketTransport : PacketTransport {
    override fun declareChannel(channelId: ResourceLocation, direction: ChannelDirection) {
        ForgeNetworkChannels.declare(channelId)
    }

    override fun sendToPlayer(player: ServerPlayer, channelId: ResourceLocation, buf: FriendlyByteBuf) {
        player.connection.send(ClientboundCustomPayloadPacket(channelId, buf))
    }

    override fun registerServerReceiver(channelId: ResourceLocation, receiver: (MinecraftServer, ServerPlayer, FriendlyByteBuf) -> Unit) {
        ForgeNetworkChannels.channel(channelId).registerObject(ServerPayloadListener(receiver))
    }
}

/**
 * 頻道上用戶端送來的封包的處理。
 *
 * @property receiver 收到伺服器、送出封包的玩家與封包內容。
 */
private class ServerPayloadListener(private val receiver: (MinecraftServer, ServerPlayer, FriendlyByteBuf) -> Unit) {
    /**
     * 在網路執行緒上把封包交給 [receiver]。
     *
     * @param event 用戶端送來的封包。
     */
    @SubscribeEvent
    fun onPayload(event: NetworkEvent.ClientCustomPayloadEvent) {
        val context = event.source.get()
        val player = context.sender ?: return
        receiver(player.server, player, event.payload)
        context.packetHandled = true
    }
}
