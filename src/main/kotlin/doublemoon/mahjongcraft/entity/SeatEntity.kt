package doublemoon.mahjongcraft.entity

import doublemoon.mahjongcraft.network.CustomEntitySpawnS2CPacketHandler
import doublemoon.mahjongcraft.registry.EntityTypeRegistry
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * 這只是一個實體, 並沒有實際的模型, 主要是用來製造出椅子的效果,
 *
 * @param sourceBlock [SeatEntity] 所在的那格 [BlockPos], 用來判斷椅子有沒有被摧毀, 這格方塊如果消失了, [SeatEntity] 也會消失
 * @param sitOffsetY 玩家坐在椅子上的偏移量
 * @param stopSitOffsetY 玩家離開椅子的偏移量 (會往椅子上方傳送)
 * */
class SeatEntity(
    type: EntityType<SeatEntity> = EntityTypeRegistry.seat,
    world: World,
    private var sourceBlock: BlockPos? = null,
    private val sitOffsetY: Double = 0.3,
    private val stopSitOffsetY: Double = 0.8,
) : Entity(type, world) {

    init {
        isInvisible = true  //將椅子設為隱形的實體
        sourceBlock?.apply { setPos(x + 0.5, y + sitOffsetY, z + 0.5) }
    }

    override fun tick() {
        super.tick()
        if (sourceBlock == null) sourceBlock = blockPos
        if (!world.isClient && (passengerList.isEmpty() || world.isAir(sourceBlock))) remove(RemovalReason.DISCARDED)
    }

    override fun getMountedHeightOffset(): Double = 0.0

    override fun initDataTracker() {}

    override fun readCustomDataFromNbt(nbt: NbtCompound?) {}

    override fun writeCustomDataToNbt(nbt: NbtCompound?) {}

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> =
        CustomEntitySpawnS2CPacketHandler.createPacket(this)

    override fun updatePassengerForDismount(passenger: LivingEntity): Vec3d =
        Vec3d(blockPos.x + 0.5, blockPos.y + stopSitOffsetY, blockPos.z + 0.5)

    companion object {
        /**
         * 檢查 [world] 的位置 [pos] 上能不能生成 [SeatEntity] (即有沒有相同的 [SeatEntity] 實體,以及高度足夠),
         * 必須在伺服端調用
         *
         * @param pos 要生成 [SeatEntity] 的位置
         * @param height 計算用的高度, 除了 [pos] 往上開始算到 [height] 空間足夠的話就表示高度可以, 預設為 2
         * @param checkEntity 檢查 [pos] 上否已經有 [SeatEntity] 存在 (即檢查是否有人已經坐在這)
         * */
        fun canSpawnAt(
            world: ServerWorld,
            pos: BlockPos,
            height: Int = 2,
            checkEntity: Boolean = true,
        ): Boolean {
            val heightEnough =
                if (height <= 0) true  //不應該出現負數的情況
                else (1..height).all {  //檢查上方是否有足夠空間
                    val blockState = world.getBlockState(pos.offset(Direction.UP, it))
                    blockState.getCollisionShape(world, pos).isEmpty
                }

            return if (checkEntity) {
                val seatEntitiesAtThisPos =
                    world.getEntitiesByType(EntityTypeRegistry.seat) { it.blockPos == pos && it.isAlive }
                seatEntitiesAtThisPos.isEmpty() && heightEnough //同一格不能有其他 SeatEntity 實體 & 高度足夠
            } else {
                heightEnough
            }
        }

        /**
         * 讓 [entity] 坐在 [pos] 的位置上,
         * 使用前請使用 [canSpawnAt] 檢查,
         * 必須在伺服端調用
         * */
        fun spawnAt(
            world: ServerWorld,
            pos: BlockPos,
            entity: Entity,
            sitOffsetY: Double = 0.3,
            stopSitOffsetY: Double = 0.8,
        ) {
            val seatEntity = SeatEntity(
                world = world,
                sourceBlock = pos,
                sitOffsetY = sitOffsetY,
                stopSitOffsetY = stopSitOffsetY
            )
            world.spawnEntity(seatEntity)
            entity.startRiding(seatEntity, false)
        }

        /**
         * 基本與 [spawnAt] 一樣,
         * 會強制讓原本坐在 [pos] 上的 [SeatEntity] 上的實體下來,
         * 必須在伺服端調用
         * */
        fun forceSpawnAt(
            world: ServerWorld,
            pos: BlockPos,
            entity: Entity,
            sitOffsetY: Double = 0.3,
            stopSitOffsetY: Double = 0.8,
        ) {
            val seatEntitiesAtThisPos = world.getEntitiesByType(EntityTypeRegistry.seat) {  //取得在 pos 上相同的實體
                it.blockPos == pos && it.isAlive
            }
            seatEntitiesAtThisPos.forEach { it.remove(RemovalReason.DISCARDED) }
            spawnAt(world, pos, entity, sitOffsetY, stopSitOffsetY)
        }
    }
}