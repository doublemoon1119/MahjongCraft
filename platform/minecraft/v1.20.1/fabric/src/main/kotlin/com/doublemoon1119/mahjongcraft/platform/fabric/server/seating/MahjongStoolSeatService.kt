package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single

/**
 * 讓任何 entity 坐上麻將凳的伺服器端入口；玩家右鍵凳子與開局入座都透過這裡。
 *
 * 坐下只是 Minecraft 的乘坐，與房間、牌局或座位分配無關。
 *
 * @property spawnGateway 正式生成座位 entity 的入口。
 */
@Single
class MahjongStoolSeatService(
    private val spawnGateway: FabricEntitySpawnGateway,
) {
    /**
     * 讓 [entity] 坐上位於 [stoolPos] 的麻將凳，座位朝向設為 [entity] 目前的朝向。
     *
     * 該位置不是麻將凳、凳子已經有座位、[entity] 正在騎乘、不在 [world] 中，或坐下後會卡進上方方塊時不坐下。
     *
     * @param world 麻將凳所在的伺服器世界。
     * @param stoolPos 要坐上的凳子方塊座標。
     * @param entity 要乘坐凳子的實體。
     * @param seatYaw 入座後的水平面向；省略時沿用實體目前的面向。
     * @return 是否坐下。
     */
    fun sit(
        world: ServerWorld,
        stoolPos: BlockPos,
        entity: Entity,
        seatYaw: Float = entity.yaw,
    ): Boolean {
        if (!world.chunkManager.isChunkLoaded(stoolPos.x shr 4, stoolPos.z shr 4)) return false
        val stool = world.getBlockState(stoolPos).block as? MahjongStoolBlock ?: return false
        if (!canSit(world, stoolPos, entity)) return false

        val seatTopY = stoolPos.y + stool.design.seatHeight
        val seat = MahjongStoolSeatEntity(world = world).apply {
            refreshPositionAndAngles(
                stoolPos.x + HALF_BLOCK,
                MahjongStoolSeatGeometry.seatY(seatTopY),
                stoolPos.z + HALF_BLOCK,
                seatYaw,
                0.0f,
            )
            markOccupancyChecked()
        }
        var seated = false
        return try {
            if (!hasRoomAbove(world, entity, seat.x, seat.y, seat.z, seatTopY)) return false
            if (!spawnGateway.spawn(world, seat, source = SPAWN_SOURCE, tableId = null)) return false
            if (!entity.startRiding(seat, true)) return false
            seat.updatePassengerPosition(entity)
            synchronizeSeatedFacing(entity, seatYaw)
            if (entity.vehicle !== seat) return false
            seated = true
            true
        } finally {
            if (!seated) discardFailedSeat(entity, seat)
        }
    }

    /**
     * 新入座後設定面向；玩家須透過連線同步視角，僅改伺服器實體角度不會更新本機相機。
     *
     * 保留既有乘坐關係與仰角，不使用會卸乘的跨世界傳送入口。
     *
     * @param entity 已完成乘坐定位的實體。
     * @param seatYaw 座位要求的水平面向。
     */
    private fun synchronizeSeatedFacing(entity: Entity, seatYaw: Float) {
        if (entity is ServerPlayerEntity) {
            entity.networkHandler.requestTeleport(entity.x, entity.y, entity.z, seatYaw, entity.pitch)
        } else {
            entity.refreshPositionAndAngles(entity.x, entity.y, entity.z, seatYaw, entity.pitch)
        }
    }

    /**
     * 檢查 [entity] 是否可以嘗試坐上指定的麻將凳；此方法不會建立座位或移動實體。
     *
     * @param world 麻將凳所在的伺服器世界。
     * @param stoolPos 要檢查的凳子方塊座標。
     * @param entity 要乘坐凳子的實體。
     * @return 凳子存在、未被占用且乘坐空間足夠時為 true。
     */
    fun canSit(
        world: ServerWorld,
        stoolPos: BlockPos,
        entity: Entity,
    ): Boolean {
        if (!world.chunkManager.isChunkLoaded(stoolPos.x shr 4, stoolPos.z shr 4)) return false
        val stool = world.getBlockState(stoolPos).block as? MahjongStoolBlock ?: return false
        if (entity.world !== world || entity.hasVehicle()) return false
        if (MahjongStoolSeatEntity.seatsAt(world, stoolPos).isNotEmpty()) return false
        val seatTopY = stoolPos.y + stool.design.seatHeight
        val seatX = stoolPos.x + HALF_BLOCK
        val seatZ = stoolPos.z + HALF_BLOCK
        val seatY = MahjongStoolSeatGeometry.seatY(seatTopY)
        return hasRoomAbove(world, entity, seatX, seatY, seatZ, seatTopY)
    }

    /**
     * 清理本次嘗試建立的座位，不觸碰其他座位或乘坐者原有的載具。
     *
     * @param entity 本次嘗試的乘坐者。
     * @param seat 本次嘗試建立的座位實體。
     */
    private fun discardFailedSeat(entity: Entity, seat: MahjongStoolSeatEntity) {
        if (entity.vehicle === seat) entity.stopRiding()
        seat.discard()
    }

    /**
     * 檢查 [entity] 坐上凳子後，凳面以上的身體範圍是否沒有方塊阻擋。
     *
     * @param world 凳子所在世界。
     * @param entity 要檢查碰撞空間的實體。
     * @param seatX 即將乘坐的座位 X 座標。
     * @param seatY 即將乘坐的座位 Y 座標。
     * @param seatZ 即將乘坐的座位 Z 座標。
     * @param seatTopY 凳面高度。
     * @return 乘坐者身體範圍沒有方塊阻擋時為 true。
     */
    private fun hasRoomAbove(
        world: ServerWorld,
        entity: Entity,
        seatX: Double,
        seatY: Double,
        seatZ: Double,
        seatTopY: Double,
    ): Boolean {
        val halfWidth = entity.width / 2.0
        val headY = MahjongStoolSeatGeometry.passengerFeetY(seatY) + entity.height
        val body = Box(seatX - halfWidth, seatTopY, seatZ - halfWidth, seatX + halfWidth, headY, seatZ + halfWidth)
        val minimum = BlockPos.ofFloored(body.minX - COLLISION_MARGIN, body.minY, body.minZ - COLLISION_MARGIN)
        val maximum = BlockPos.ofFloored(body.maxX + COLLISION_MARGIN, body.maxY, body.maxZ + COLLISION_MARGIN)
        if (minimum.y < world.bottomY || maximum.y >= world.topY || !world.worldBorder.contains(body)) return false
        for (chunkX in (minimum.x shr 4)..(maximum.x shr 4)) {
            for (chunkZ in (minimum.z shr 4)..(maximum.z shr 4)) {
                if (!world.chunkManager.isChunkLoaded(chunkX, chunkZ)) return false
            }
        }
        return world.isSpaceEmpty(entity, body) &&
            world.getOtherEntities(entity, body) { !it.isRemoved && !it.isSpectator && (it is LivingEntity || it.isCollidable) }.isEmpty()
    }

    /** [MahjongStoolSeatService] 的常數。 */
    private companion object {
        /** 方塊中心相對方塊角落的偏移。 */
        const val HALF_BLOCK: Double = 0.5

        /** 碰撞查詢可能讀取相鄰方塊，先確認其區塊載入。 */
        const val COLLISION_MARGIN: Double = 1.0

        /** 生成入口診斷訊息中的來源名稱。 */
        const val SPAWN_SOURCE: String = "mahjong_stool_seat"
    }
}
