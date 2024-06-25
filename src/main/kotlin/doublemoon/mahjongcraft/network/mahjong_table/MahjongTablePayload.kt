package doublemoon.mahjongcraft.network.mahjong_table

import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTableBehavior
import doublemoon.mahjongcraft.id
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.math.BlockPos

data class MahjongTablePayload(
    val behavior: MahjongTableBehavior,
    val pos: BlockPos,
    val extraData: String = "",
) : CustomPayload {
    constructor(byteBuf: PacketByteBuf) : this(
        behavior = byteBuf.readEnumConstant(MahjongTableBehavior::class.java),
        pos = byteBuf.readBlockPos(),
        extraData = byteBuf.readString(Short.MAX_VALUE.toInt())
    )

    fun writeByteBuf(byteBuf: RegistryByteBuf) {
        with(byteBuf) {
            writeEnumConstant(behavior)
            writeBlockPos(pos)
            writeString(extraData, Short.MAX_VALUE.toInt())
        }
    }

    override fun getId(): CustomPayload.Id<MahjongTablePayload> = ID

    companion object {
        val ID = CustomPayload.Id<MahjongTablePayload>(id("mahjong_table_payload"))
        val CODEC: PacketCodec<RegistryByteBuf, MahjongTablePayload> =
            PacketCodec.of(MahjongTablePayload::writeByteBuf, ::MahjongTablePayload)
    }
}