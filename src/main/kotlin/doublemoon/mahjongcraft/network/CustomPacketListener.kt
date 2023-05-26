package doublemoon.mahjongcraft.network

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

interface CustomPacketListener {

    val channelName: Identifier

    @Environment(EnvType.CLIENT)
    fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
    }

    fun onServerReceive(
        server: MinecraftServer,
        player: ServerPlayerEntity,
        handler: ServerPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
    }

    @Environment(EnvType.CLIENT)
    fun registerClient() {
        //這裡用 ClientPlayNetworking.registerReceiver() 會跳錯誤 "Cannot register receiver while not in game!"
        ClientPlayNetworking.registerGlobalReceiver(channelName, ::onClientReceive)
    }

    fun registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(channelName, ::onServerReceive)
    }
}