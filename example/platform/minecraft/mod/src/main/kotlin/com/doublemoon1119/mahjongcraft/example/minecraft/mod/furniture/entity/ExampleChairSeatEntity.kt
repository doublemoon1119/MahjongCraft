package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.entity

import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.entity.ExampleSeatGeometry
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.ExampleEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.MoveFunction
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.vehicle.DismountHelper
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 範例椅子與長凳共用的隱形支撐；有乘客時由原版保存，無乘客或家具失效時移除。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class ExampleChairSeatEntity(
    type: EntityType<out ExampleChairSeatEntity>,
    world: Level,
) : ExampleEntity(type, world) {
    /** 是否需要在載入後重新檢查同一張椅子上的座位。 */
    private var needsOccupancyCheck = true

    init {
        isInvisible = true
        noPhysics = true
        setNoGravity(true)
    }

    /** 標示新建座位已由 provider 完成占用檢查。 */
    internal fun markOccupancyChecked() {
        needsOccupancyCheck = false
    }

    /** 載入後核對乘客、家具與重複支撐；失效時只釋放本席。 */
    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        val duplicate = needsOccupancyCheck && seatsAt(level(), blockPosition()).any { it !== this }
        needsOccupancyCheck = false
        if (!isVehicle || !ExampleFurnitureBlocks.isRegistered() || !ExampleFurnitureBlocks.isSeat(level().getBlockState(blockPosition())) || duplicate) {
            ejectPassengers()
            discard()
        }
    }

    /** 將乘客腳底定位在範例椅子的固定坐姿高度。 */
    override fun positionRider(passenger: Entity, positionUpdater: MoveFunction) {
        if (hasPassenger(passenger)) {
            positionUpdater.accept(passenger, x, ExampleSeatGeometry.passengerFeetY(y), z)
        }
    }

    /** 優先在乘客面向的相鄰方塊尋找安全下座位置。 */
    override fun getDismountLocationForPassenger(passenger: LivingEntity): Vec3 = Direction.fromYRot(passenger.yHeadRot.toDouble()).let { facing ->
        listOf(facing, facing.clockWise, facing.counterClockWise, facing.opposite)
            .firstNotNullOfOrNull { direction ->
                DismountHelper.findSafeDismountLocation(passenger.type, level(), blockPosition().relative(direction), false)
            }
    } ?: super.getDismountLocationForPassenger(passenger)

    /** 隱形座位不接受傷害。 */
    override fun hurt(source: DamageSource, amount: Float): Boolean = false

    /** 方塊位置與乘客由原版保存，沒有額外欄位。 */
    override fun readAdditionalSaveData(nbt: CompoundTag) = Unit

    /** 方塊位置與乘客由原版保存，沒有額外欄位。 */
    override fun addAdditionalSaveData(nbt: CompoundTag) = Unit

    /** 查詢指定方塊上的範例椅子座位。 */
    companion object {
        /** 查詢指定位置仍存在的範例椅子座位。 */
        fun seatsAt(world: Level, pos: BlockPos): List<ExampleChairSeatEntity> = world.getEntitiesOfClass(
            ExampleChairSeatEntity::class.java,
            AABB(pos),
        ) { seat -> !seat.isRemoved && seat.blockPosition() == pos }
    }
}
