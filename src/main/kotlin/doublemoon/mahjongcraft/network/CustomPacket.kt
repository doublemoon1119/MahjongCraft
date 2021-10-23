package doublemoon.mahjongcraft.network

import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf

interface CustomPacket {

    fun writeByteBuf(byteBuf: PacketByteBuf)

    fun createByteBuf(): PacketByteBuf = PacketByteBuf(Unpooled.buffer()).also { writeByteBuf(it) }
}