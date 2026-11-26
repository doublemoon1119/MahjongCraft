package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientPacketTransport
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor

/**
 * 以 NeoForge 的封包內容類型實作 [ClientPacketTransport]。
 *
 * 伺服器沒有協商該頻道時不送出，例如沒有安裝本 mod 的伺服器，理由與伺服器端相同。
 */
object NeoForgeClientPacketTransport : ClientPacketTransport {
    override fun sendToServer(channelId: ResourceLocation, buf: FriendlyByteBuf) {
        val connection = Minecraft.getInstance().connection ?: return
        if (!connection.hasChannel(channelId)) return
        PacketDistributor.sendToServer(NeoForgeChannelPayloads.wrap(channelId, buf))
    }

    override fun registerClientReceiver(channelId: ResourceLocation, receiver: (ClientPacketListener, FriendlyByteBuf) -> Unit) {
        NeoForgeChannelPayloads.registerClientReceiver(channelId) { buf ->
            // 收到封包時一定在遊戲中，用戶端一定有連線。
            receiver(checkNotNull(Minecraft.getInstance().connection), buf)
        }
    }
}
