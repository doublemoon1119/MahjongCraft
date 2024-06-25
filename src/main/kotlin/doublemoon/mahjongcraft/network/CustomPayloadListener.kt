package doublemoon.mahjongcraft.network

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload

interface CustomPayloadListener<T : CustomPayload> {

    val id: CustomPayload.Id<T>

    val codec: PacketCodec<RegistryByteBuf, T>

    val channelType: ChannelType

    @Environment(EnvType.CLIENT)
    fun onClientReceive(
        payload: T,
        context: ClientPlayNetworking.Context,
    ) {
    }

    fun onServerReceive(
        payload: T,
        context: ServerPlayNetworking.Context,
    ) {
    }

    fun registerCommon() {
        if (channelType.s2c) PayloadTypeRegistry.playS2C().register(id, codec)
        if (channelType.c2s) PayloadTypeRegistry.playC2S().register(id, codec)
    }

    @Environment(EnvType.CLIENT)
    fun registerClient() {
        if (!channelType.s2c) return
        ClientPlayNetworking.registerGlobalReceiver(id, ::onClientReceive)
    }

    fun registerServer() {
        if (!channelType.c2s) return
        ServerPlayNetworking.registerGlobalReceiver(id, ::onServerReceive)
    }
}