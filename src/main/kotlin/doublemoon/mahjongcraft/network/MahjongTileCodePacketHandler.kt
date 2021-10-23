package doublemoon.mahjongcraft.network

import doublemoon.mahjongcraft.entity.MahjongTileEntity
import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.id
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.*

object MahjongTileCodePacketHandler : CustomPacketHandler {

    override val channelName = id("mahjong_tile_code_packet")

    private data class MahjongTileCodePacket(
        val gameBlockPos: BlockPos,
        val uuid: UUID,
        val code: Int
    ) : CustomPacket {
        constructor(byteBuf: PacketByteBuf) : this(
            gameBlockPos = byteBuf.readBlockPos(),
            uuid = byteBuf.readUuid(),
            code = byteBuf.readVarInt()
        )

        override fun writeByteBuf(byteBuf: PacketByteBuf) {
            with(byteBuf) {
                writeBlockPos(gameBlockPos)
                writeUuid(uuid)
                writeVarInt(code)
            }
        }
    }

    @Environment(EnvType.CLIENT)
    fun ClientPlayerEntity.requestTileCode(
        gameBlockPos: BlockPos,
        uuid: UUID,
    ) = this.networkHandler.sendPacket(
        ClientPlayNetworking.createC2SPacket(
            channelName,
            MahjongTileCodePacket(gameBlockPos, uuid, 0).createByteBuf() //code 不重要
        )
    )

    private fun ServerPlayerEntity.sendTileCode(
        uuid: UUID,
        code: Int
    ) = this.networkHandler.sendPacket(
        ServerPlayNetworking.createS2CPacket(
            channelName,
            MahjongTileCodePacket(BlockPos.ORIGIN, uuid, code).createByteBuf() //gameBlockPos 不重要
        )
    )

    override fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
        MahjongTileCodePacket(byteBuf).apply {
            val world = MinecraftClient.getInstance().world ?: return
            val tile = world.entities.find { it.uuid == uuid } as MahjongTileEntity? ?: return
            tile.code = code
        }
    }

    override fun onServerReceive(
        server: MinecraftServer,
        player: ServerPlayerEntity,
        handler: ServerPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
        MahjongTileCodePacket(byteBuf).apply {
            val entity = GameManager.getMahjongTileEntityBy(uuid) as MahjongTileEntity? ?: return
            val code = entity.getCodeForPlayer(player)
//            logger.info("Received a packet from ${player.name}, code->$code")
            player.sendTileCode(uuid = uuid, code = code)
        }
    }
}