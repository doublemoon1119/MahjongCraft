package com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureBlocks
import net.minecraft.entity.Dismounting
import net.minecraft.entity.Entity
import net.minecraft.entity.Entity.PositionUpdater
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * 範例椅子與長凳共用的隱形支撐；有乘客時由原版保存，無乘客或家具失效時移除。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class ExampleChairSeatEntity(
    type: EntityType<out ExampleChairSeatEntity>,
    world: World,
) : Entity(type, world) {
    /** 是否需要在載入後重新檢查同一張椅子上的座位。 */
    private var needsOccupancyCheck = true

    init {
        isInvisible = true
        noClip = true
        setNoGravity(true)
    }

    /** 標示新建座位已由 provider 完成占用檢查。 */
    internal fun markOccupancyChecked() {
        needsOccupancyCheck = false
    }

    /** 載入後核對乘客、家具與重複支撐；失效時只釋放本席。 */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        val duplicate = needsOccupancyCheck && seatsAt(world, blockPos).any { it !== this }
        needsOccupancyCheck = false
        if (!hasPassengers() || !ExampleFurnitureBlocks.isRegistered() || !ExampleFurnitureBlocks.isSeat(world.getBlockState(blockPos)) || duplicate) {
            removeAllPassengers()
            discard()
        }
    }

    /** 將乘客腳底定位在範例椅子的固定坐姿高度。 */
    override fun updatePassengerPosition(passenger: Entity, positionUpdater: PositionUpdater) {
        if (hasPassenger(passenger)) {
            positionUpdater.accept(passenger, x, ExampleSeatGeometry.passengerFeetY(y), z)
        }
    }

    /** 優先在乘客面向的相鄰方塊尋找安全下座位置。 */
    override fun updatePassengerForDismount(passenger: LivingEntity): Vec3d = Direction.fromRotation(passenger.headYaw.toDouble()).let { facing ->
        listOf(facing, facing.rotateYClockwise(), facing.rotateYCounterclockwise(), facing.opposite)
            .firstNotNullOfOrNull { direction ->
                Dismounting.findRespawnPos(passenger.type, world, blockPos.offset(direction), false)
            }
    } ?: super.updatePassengerForDismount(passenger)

    /** 隱形座位不接受傷害。 */
    override fun damage(source: DamageSource, amount: Float): Boolean = false

    /** 方塊位置與乘客由原版保存，沒有額外欄位。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    /** 方塊位置與乘客由原版保存，沒有額外欄位。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    /** entity 不需要資料追蹤欄位。 */
    override fun initDataTracker() = Unit

    /** 查詢指定方塊上的範例椅子座位。 */
    companion object {
        /** 查詢指定位置仍存在的範例椅子座位。 */
        fun seatsAt(world: World, pos: BlockPos): List<ExampleChairSeatEntity> = world.getEntitiesByClass(
            ExampleChairSeatEntity::class.java,
            Box(pos),
        ) { seat -> !seat.isRemoved && seat.blockPos == pos }
    }
}
