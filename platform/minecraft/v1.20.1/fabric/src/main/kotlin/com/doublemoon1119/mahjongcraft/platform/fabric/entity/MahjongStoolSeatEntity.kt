package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import net.minecraft.entity.Dismounting
import net.minecraft.entity.Entity
import net.minecraft.entity.Entity.PositionUpdater
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * 讓 entity 坐在麻將凳上的隱形座位，位置與高度見 [MahjongStoolSeatGeometry]。
 *
 * 座位只認自己目前所在的方塊。伺服器端每 tick 檢查，沒有乘客、所在方塊不再是麻將凳，或從存檔重新生成後發現同一張凳子
 * 已有其他座位時，讓乘客下來並移除自己。座位不保存額外資料：有玩家乘坐時原版不把座位存進區塊，而是隨玩家存檔保存，
 * 玩家回來時重新生成並坐回去。轉頭不受限制。
 */
class MahjongStoolSeatEntity(
    type: EntityType<out MahjongStoolSeatEntity> = ModEntities.mahjongStoolSeat,
    world: World,
) : Entity(type, world) {
    /** 是否還需要確認同一張凳子沒有其他座位；坐下入口新建的座位在生成前已確認。 */
    private var needsOccupancyCheck = true

    init {
        isInvisible = true
        noClip = true
        setNoGravity(true)
    }

    /** 標記此座位在生成前已確認凳子上沒有其他座位。 */
    internal fun markOccupancyChecked() {
        needsOccupancyCheck = false
    }

    override fun tick() {
        super.tick()
        if (world.isClient) return
        val duplicated = needsOccupancyCheck && seatsAt(world, blockPos).any { it !== this }
        needsOccupancyCheck = false
        if (!hasPassengers() || world.getBlockState(blockPos).block !is MahjongStoolBlock || duplicated) {
            removeAllPassengers()
            discard()
        }
    }

    /** 讓乘客的腳底位於 [MahjongStoolSeatGeometry.passengerFeetY]，不依乘客種類另外偏移。 */
    override fun updatePassengerPosition(passenger: Entity, positionUpdater: PositionUpdater) {
        if (!hasPassenger(passenger)) return
        positionUpdater.accept(passenger, x, MahjongStoolSeatGeometry.passengerFeetY(y), z)
    }

    /** 依 [MahjongStoolSeatGeometry.dismountDirections] 找第一個站得下的相鄰方塊；都不行時站到凳子上方。 */
    override fun updatePassengerForDismount(passenger: LivingEntity): Vec3d = MahjongStoolSeatGeometry
        .dismountDirections(passenger.horizontalFacing)
        .firstNotNullOfOrNull { direction -> Dismounting.findRespawnPos(passenger.type, world, blockPos.offset(direction), false) }
        ?: super.updatePassengerForDismount(passenger)

    /** 座位不會受到任何傷害。 */
    override fun damage(source: DamageSource, amount: Float): Boolean = false

    override fun initDataTracker() = Unit

    override fun readCustomDataFromNbt(nbt: NbtCompound) = Unit

    override fun writeCustomDataToNbt(nbt: NbtCompound) = Unit

    /** 座位的查詢。 */
    companion object {
        /** [world] 中位於麻將凳 [stoolPos] 的座位。 */
        fun seatsAt(world: World, stoolPos: BlockPos): List<MahjongStoolSeatEntity> = world.getEntitiesByClass(
            MahjongStoolSeatEntity::class.java,
            Box(stoolPos),
        ) { seat -> !seat.isRemoved && seat.blockPos == stoolPos }
    }
}
