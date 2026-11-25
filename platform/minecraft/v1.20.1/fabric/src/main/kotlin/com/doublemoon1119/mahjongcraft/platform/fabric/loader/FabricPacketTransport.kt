package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ChannelDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.PacketTransport
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/** 以 Fabric API 的網路功能實作 [PacketTransport]。 */
object FabricPacketTransport : PacketTransport {
    /** Fabric 在登記接收時自動告知對方有哪些頻道，不需要事先宣告。 */
    override fun declareChannel(channelId: ResourceLocation, direction: ChannelDirection) = Unit

    override fun sendToPlayer(player: ServerPlayer, channelId: ResourceLocation, buf: FriendlyByteBuf) {
        ServerPlayNetworking.send(player, channelId, buf)
    }

    override fun registerServerReceiver(channelId: ResourceLocation, receiver: (MinecraftServer, ServerPlayer, FriendlyByteBuf) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(channelId) { server, player, _, buf, _ -> receiver(server, player, buf) }
    }
}
