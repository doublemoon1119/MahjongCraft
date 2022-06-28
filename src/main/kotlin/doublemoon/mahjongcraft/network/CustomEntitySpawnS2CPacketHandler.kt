package doublemoon.mahjongcraft.network

import doublemoon.mahjongcraft.id
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.network.Packet
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import java.util.*
import kotlin.math.floor

object CustomEntitySpawnS2CPacketHandler : CustomPacketHandler {

    override val channelName = id("entity_spawn_packet")

    private data class CustomEntitySpawnS2CPacket(
        val entityType: EntityType<*>,
        val id: Int,
        val uuid: UUID,
        val x: Double,
        val y: Double,
        val z: Double,
        val pitch: Float,
        val yaw: Float,
        val headYaw: Float,
        val velocityX: Double,
        val velocityY: Double,
        val velocityZ: Double,
    ) : CustomPacket {
        val velocity = Vec3d(velocityX, velocityY, velocityZ)

        constructor(entity: Entity) : this(
            entityType = entity.type,
            id = entity.id,
            uuid = entity.uuid,
            x = entity.x,
            y = entity.y,
            z = entity.z,
            pitch = entity.pitch,
            yaw = entity.yaw,
            headYaw = entity.headYaw,
            velocityX = entity.velocity.x,
            velocityY = entity.velocity.y,
            velocityZ = entity.velocity.z
        )

        constructor(byteBuf: PacketByteBuf) : this(
            entityType = Registry.ENTITY_TYPE.get(byteBuf.readVarInt()),
            id = byteBuf.readInt(),
            uuid = byteBuf.readUuid(),
            x = byteBuf.readDouble(),
            y = byteBuf.readDouble(),
            z = byteBuf.readDouble(),
            pitch = byteBuf.readByte() * 360 / 256f,
            yaw = byteBuf.readByte() * 360 / 256f,
            headYaw = byteBuf.readByte() * 360 / 256f,
            velocityX = byteBuf.readShort() / 8000.0,
            velocityY = byteBuf.readShort() / 8000.0,
            velocityZ = byteBuf.readShort() / 8000.0,
        )

        override fun writeByteBuf(byteBuf: PacketByteBuf) {
            with(byteBuf) {
                writeVarInt(Registry.ENTITY_TYPE.getRawId(entityType)) //typeId
                writeInt(id)
                writeUuid(uuid)
                writeDouble(x)
                writeDouble(y)
                writeDouble(z)
                writeByte(floor(pitch * 256f / 360f).toInt())
                writeByte(floor(yaw * 256f / 360f).toInt())
                writeByte(floor(headYaw * 256f / 360f).toInt())
                val d1 = velocityX.coerceIn(-3.9, 3.9)
                val d2 = velocityY.coerceIn(-3.9, 3.9)
                val d3 = velocityZ.coerceIn(-3.9, 3.9)
                writeShort((d1 * 8000.0).toInt())
                writeShort((d2 * 8000.0).toInt())
                writeShort((d3 * 8000.0).toInt())
            }
        }
    }

    /**
     * 參考與借用 Forge 的 FMLPlayMessages 的寫法
     * */
    fun createPacket(entity: Entity): Packet<*> {
        if (entity.world.isClient) throw IllegalStateException("CustomEntitySpawnS2CPacket.create called on the logical client!")
        return ServerPlayNetworking.createS2CPacket(channelName, CustomEntitySpawnS2CPacket(entity).createByteBuf())
    }

    override fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        byteBuf: PacketByteBuf,
        responseSender: PacketSender
    ) {
        CustomEntitySpawnS2CPacket(byteBuf).also { packet ->
            client.execute {
                val world = handler.world.also { checkNotNull(it) { "Tried to spawn entity in a null world!" } }
                val entity = (packet.entityType.create(world) ?: throw IllegalStateException(
                    "Failed to create instance of entity \"" + Registry.ENTITY_TYPE.getId(packet.entityType)
                        .toString() + "\"!"
                )).apply {
                    updateTrackedPosition(packet.x, packet.y, packet.z)
                    updatePositionAndAngles(packet.x, packet.y, packet.z, packet.yaw, packet.pitch)
                    this.headYaw = packet.headYaw //頭跟身體都朝向頭的方向
                    this.bodyYaw = packet.headYaw
                    this.id = packet.id
                    this.uuid = packet.uuid
                }
                world.addEntity(packet.id, entity)
                entity.velocity = packet.velocity
            }
        }
    }
}