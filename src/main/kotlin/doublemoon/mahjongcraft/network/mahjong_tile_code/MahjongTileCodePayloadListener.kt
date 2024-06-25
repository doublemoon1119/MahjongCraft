package doublemoon.mahjongcraft.network.mahjong_tile_code

import doublemoon.mahjongcraft.entity.MahjongTileEntity
import doublemoon.mahjongcraft.network.ChannelType
import doublemoon.mahjongcraft.network.CustomPayloadListener
import doublemoon.mahjongcraft.network.sendPayloadToPlayer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload

object MahjongTileCodePayloadListener : CustomPayloadListener<MahjongTileCodePayload> {

    override val id: CustomPayload.Id<MahjongTileCodePayload> = MahjongTileCodePayload.ID

    override val codec: PacketCodec<RegistryByteBuf, MahjongTileCodePayload> = MahjongTileCodePayload.CODEC

    override val channelType: ChannelType = ChannelType.Both

    override fun onClientReceive(
        payload: MahjongTileCodePayload,
        context: ClientPlayNetworking.Context,
    ) {
        val (id, code) = payload
        val world = context.client().world ?: return

        kotlin.runCatching {
            // ClientWorld 並沒有 getEntity(uuid) 的方法, 但是 ServerWorld 就有 (what??)
            world.getEntityById(id) as MahjongTileEntity? ?: return
        }.fold(
            onSuccess = { it.code = code },
            onFailure = {
                // 因為包的處理不在主線程上, 所以有機會在 getEntityById 時,
                // 物體已經消失然後造成 IndexOutOfBoundsException 錯誤, 直接把這個錯誤忽略
                if (it !is IndexOutOfBoundsException) it.printStackTrace()
            }
        )
    }

    override fun onServerReceive(
        payload: MahjongTileCodePayload,
        context: ServerPlayNetworking.Context,
    ) {
        val (id, _) = payload
        val player = context.player()
        val server = player.server

        for (world in server.worlds) {
            val entity = world.getEntityById(id) as? MahjongTileEntity ?: continue
            val code = entity.getCodeForPlayer(player)

            sendPayloadToPlayer(
                player = player,
                payload = MahjongTileCodePayload(id = id, code = code)
            )

            break
        }
    }
}