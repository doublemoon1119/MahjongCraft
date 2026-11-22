package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientPacketTransport
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** 以 Fabric API 的用戶端網路功能實作 [ClientPacketTransport]。 */
object FabricClientPacketTransport : ClientPacketTransport {
    override fun sendToServer(channelId: ResourceLocation, buf: FriendlyByteBuf) {
        ClientPlayNetworking.send(channelId, buf)
    }

    override fun registerClientReceiver(channelId: ResourceLocation, receiver: (ClientPacketListener, FriendlyByteBuf) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(channelId) { _, connection, buf, _ -> receiver(connection, buf) }
    }
}
