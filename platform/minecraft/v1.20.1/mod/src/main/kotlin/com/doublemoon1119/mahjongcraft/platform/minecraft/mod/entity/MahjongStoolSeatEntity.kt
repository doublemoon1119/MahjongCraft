package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.vehicle.DismountHelper
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 讓 entity 坐在麻將凳上的隱形座位，位置與高度見 [MahjongStoolSeatGeometry]。
 *
 * 座位只認自己目前所在的方塊。伺服器端每 tick 檢查，沒有乘客、所在方塊不再是麻將凳，或從存檔重新生成後發現同一張凳子
 * 已有其他座位時，讓乘客下來並移除自己。座位不保存額外資料：有玩家乘坐時原版不把座位存進區塊，而是隨玩家存檔保存，
 * 玩家回來時重新生成並坐回去。轉頭不受限制。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class MahjongStoolSeatEntity(
    type: EntityType<out MahjongStoolSeatEntity> = ModEntities.mahjongStoolSeat,
    world: Level,
) : Entity(type, world) {
    /** 是否還需要確認同一張凳子沒有其他座位；坐下入口新建的座位在生成前已確認。 */
    private var needsOccupancyCheck = true

    init {
        isInvisible = true
        noPhysics = true
        setNoGravity(true)
    }

    /** 標記此座位在生成前已確認凳子上沒有其他座位。 */
    internal fun markOccupancyChecked() {
        needsOccupancyCheck = false
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        val duplicated = needsOccupancyCheck && seatsAt(level(), blockPosition()).any { it !== this }
        needsOccupancyCheck = false
        if (!isVehicle || level().getBlockState(blockPosition()).block !is MahjongStoolBlock || duplicated) {
            ejectPassengers()
            discard()
        }
    }

    /**
     * 讓乘客的腳底位於 [MahjongStoolSeatGeometry.passengerFeetY]，不依乘客種類另外偏移。
     *
     * @param passenger 正坐在此座位上的乘客。
     * @param positionUpdater 套用乘客新位置的更新器。
     */
    override fun positionRider(passenger: Entity, positionUpdater: MoveFunction) {
        if (!hasPassenger(passenger)) return
        positionUpdater.accept(passenger, x, MahjongStoolSeatGeometry.passengerFeetY(y), z)
    }

    /**
     * 依 [MahjongStoolSeatGeometry.dismountDirections] 找第一個站得下的相鄰方塊；都不行時站到凳子上方。
     *
     * @param passenger 要離開座位的乘客。
     * @return 可供乘客站立的下座位置。
     */
    override fun getDismountLocationForPassenger(passenger: LivingEntity): Vec3 = MahjongStoolSeatGeometry
        .dismountDirections(passenger.direction)
        .firstNotNullOfOrNull { direction -> DismountHelper.findSafeDismountLocation(passenger.type, level(), blockPosition().relative(direction), false) }
        ?: super.getDismountLocationForPassenger(passenger)

    /**
     * 座位不會受到任何傷害。
     *
     * @param source 傷害來源。
     * @param amount 傷害量；此座位一律不受傷害。
     * @return 一律回傳 `false`，表示傷害未造成效果。
     */
    override fun hurt(source: DamageSource, amount: Float): Boolean = false

    override fun defineSynchedData() = Unit

    /**
     * 座位不保存額外資料。
     *
     * @param nbt 未使用的 entity 持久化資料。
     */
    override fun readAdditionalSaveData(nbt: CompoundTag) = Unit

    /**
     * 座位不保存額外資料。
     *
     * @param nbt 未使用的 entity 持久化資料。
     */
    override fun addAdditionalSaveData(nbt: CompoundTag) = Unit

    /** 座位的查詢。 */
    companion object {
        /**
         * [world] 中位於麻將凳 [stoolPos] 的座位。
         *
         * @param world 要查詢的世界。
         * @param stoolPos 麻將凳方塊位置。
         * @return 尚未移除且位於該方塊中的座位 entity。
         */
        fun seatsAt(world: Level, stoolPos: BlockPos): List<MahjongStoolSeatEntity> = world.getEntitiesOfClass(
            MahjongStoolSeatEntity::class.java,
            AABB(stoolPos),
        ) { seat -> !seat.isRemoved && seat.blockPosition() == stoolPos }
    }
}
