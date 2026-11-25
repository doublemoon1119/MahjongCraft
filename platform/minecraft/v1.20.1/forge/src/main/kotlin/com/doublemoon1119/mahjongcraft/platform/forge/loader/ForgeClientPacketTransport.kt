package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientPacketTransport
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.NetworkEvent

/** 以 Forge 的事件頻道實作 [ClientPacketTransport]。封包以原版的自訂資料封包傳送。 */
object ForgeClientPacketTransport : ClientPacketTransport {
    /** 沒有連線時不送出。 */
    override fun sendToServer(channelId: ResourceLocation, buf: FriendlyByteBuf) {
        Minecraft.getInstance().connection?.send(ServerboundCustomPayloadPacket(channelId, buf))
    }

    override fun registerClientReceiver(channelId: ResourceLocation, receiver: (ClientPacketListener, FriendlyByteBuf) -> Unit) {
        ForgeNetworkChannels.channel(channelId).registerObject(ClientPayloadListener(receiver))
    }
}

/**
 * 頻道上伺服器送來的封包的處理。
 *
 * @property receiver 收到送來封包的連線與封包內容。
 */
private class ClientPayloadListener(private val receiver: (ClientPacketListener, FriendlyByteBuf) -> Unit) {
    /**
     * 在網路執行緒上把封包交給 [receiver]。沒有連線時略過。
     *
     * @param event 伺服器送來的封包。
     */
    @SubscribeEvent
    fun onPayload(event: NetworkEvent.ServerCustomPayloadEvent) {
        val context = event.source.get()
        val connection = Minecraft.getInstance().connection ?: return
        receiver(connection, event.payload)
        context.packetHandled = true
    }
}
