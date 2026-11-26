package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ChannelDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.PacketTransport
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

/**
 * 以 NeoForge 的封包內容類型實作 [PacketTransport]。
 *
 * NeoForge 不允許把封包送給沒有協商過該頻道的連線，例如沒有安裝本 mod 的玩家。這種連線收不到封包，與其他載入器上對方
 * 忽略不認得的封包效果相同。
 */
object NeoForgePacketTransport : PacketTransport {
    override fun declareChannel(channelId: ResourceLocation, direction: ChannelDirection) {
        NeoForgeChannelPayloads.declare(channelId, direction)
    }

    override fun sendToPlayer(player: ServerPlayer, channelId: ResourceLocation, buf: FriendlyByteBuf) {
        if (!player.connection.hasChannel(channelId)) return
        PacketDistributor.sendToPlayer(player, NeoForgeChannelPayloads.wrap(channelId, buf))
    }

    override fun registerServerReceiver(channelId: ResourceLocation, receiver: (MinecraftServer, ServerPlayer, FriendlyByteBuf) -> Unit) {
        NeoForgeChannelPayloads.registerServerReceiver(channelId) { context, buf ->
            // 送往伺服器的封包一定來自伺服器上的玩家。
            val player = context.player() as ServerPlayer
            receiver(player.server, player, buf)
        }
    }
}
