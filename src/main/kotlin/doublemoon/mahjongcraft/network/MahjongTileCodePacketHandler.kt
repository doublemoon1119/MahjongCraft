package doublemoon.mahjongcraft.network

import doublemoon.mahjongcraft.entity.MahjongTileEntity
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

object MahjongTileCodePacketHandler : CustomPacketHandler {

    override val channelName = id("mahjong_tile_code_packet")

    private data class MahjongTileCodePacket(
        val gameBlockPos: BlockPos,
        val id: Int,
        val code: Int
    ) : CustomPacket {
        constructor(byteBuf: PacketByteBuf) : this(
            gameBlockPos = byteBuf.readBlockPos(),
            id = byteBuf.readVarInt(),
            code = byteBuf.readVarInt()
        )

        override fun writeByteBuf(byteBuf: PacketByteBuf) {
            with(byteBuf) {
                writeBlockPos(gameBlockPos)
                writeVarInt(id)
                writeVarInt(code)
            }
        }
    }

    @Environment(EnvType.CLIENT)
    fun ClientPlayerEntity.requestTileCode(
        gameBlockPos: BlockPos,
        id: Int
    ) = this.networkHandler.sendPacket(
        ClientPlayNetworking.createC2SPacket(
            channelName,
            MahjongTileCodePacket(gameBlockPos, id, 0).createByteBuf() //code 不重要
        )
    )

    private fun ServerPlayerEntity.sendTileCode(
        id: Int,
        code: Int
    ) = this.networkHandler.sendPacket(
        ServerPlayNetworking.createS2CPacket(
            channelName,
            MahjongTileCodePacket(BlockPos.ORIGIN, id, code).createByteBuf() //gameBlockPos 不重要
        )
    )

    override fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
        MahjongTileCodePacket(byteBuf).apply {
            //ClientWorld 並沒有 getEntity(uuid) 的方法, 但是 ServerWorld 就有 (wat??)
            kotlin.runCatching {
                client.world?.getEntityById(id) as MahjongTileEntity? ?: return@apply
            }.fold(
                onSuccess = {
                    it.code = this.code
                },
                onFailure = {
                    //因為包的處理不在主線程上, 所以有機會在 getEntityById 時,
                    // 物體已經消失然後造成 IndexOutOfBoundsException 錯誤, 直接把這個錯誤忽略
                    if (it !is IndexOutOfBoundsException) it.printStackTrace()
                }
            )
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
            server.worlds.forEach { world ->
                (world.getEntityById(id) as MahjongTileEntity?)?.also {
                    val code = it.getCodeForPlayer(player)
                    player.sendTileCode(id = id, code = code)
                    return@forEach
                }
            }
        }
    }
}