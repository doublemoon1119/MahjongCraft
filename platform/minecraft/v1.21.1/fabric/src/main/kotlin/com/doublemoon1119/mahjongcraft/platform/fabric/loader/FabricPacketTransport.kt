package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ChannelDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.PacketTransport
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/** 以 Fabric API 的網路功能實作 [PacketTransport]；每個頻道登記成 [FabricChannelPayloads] 的封包型別。 */
object FabricPacketTransport : PacketTransport {
    override fun declareChannel(channelId: ResourceLocation, direction: ChannelDirection) {
        FabricChannelPayloads.declare(channelId, direction)
    }

    override fun sendToPlayer(player: ServerPlayer, channelId: ResourceLocation, buf: FriendlyByteBuf) {
        ServerPlayNetworking.send(player, FabricChannelPayloads.wrap(channelId, buf))
    }

    override fun registerServerReceiver(channelId: ResourceLocation, receiver: (MinecraftServer, ServerPlayer, FriendlyByteBuf) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(FabricChannelPayloads.type(channelId)) { payload, context ->
            receiver(context.server(), context.player(), FabricChannelPayloads.unwrap(payload))
        }
    }
}
