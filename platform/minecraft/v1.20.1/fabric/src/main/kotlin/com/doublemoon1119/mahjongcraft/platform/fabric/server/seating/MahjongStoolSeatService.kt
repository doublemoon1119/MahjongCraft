package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import net.minecraft.entity.Entity
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
     * @return 是否坐下。
     */
    fun sit(world: ServerWorld, stoolPos: BlockPos, entity: Entity): Boolean {
        val stool = world.getBlockState(stoolPos).block as? MahjongStoolBlock ?: return false
        if (entity.world !== world || entity.hasVehicle() || MahjongStoolSeatEntity.seatsAt(world, stoolPos).isNotEmpty()) return false

        val seatTopY = stoolPos.y + stool.design.seatHeight
        val seat = MahjongStoolSeatEntity(world = world).apply {
            refreshPositionAndAngles(stoolPos.x + HALF_BLOCK, MahjongStoolSeatGeometry.seatY(seatTopY), stoolPos.z + HALF_BLOCK, entity.yaw, 0.0f)
            markOccupancyChecked()
        }
        if (!hasRoomAbove(world, entity, seat, seatTopY)) return false
        if (!spawnGateway.spawn(world, seat, source = SPAWN_SOURCE, tableId = null)) return false
        if (!entity.startRiding(seat, true)) {
            seat.discard()
            return false
        }
        return true
    }

    /** [entity] 坐上 [seat] 後，凳面以上的身體範圍是否沒有方塊阻擋。 */
    private fun hasRoomAbove(
        world: ServerWorld,
        entity: Entity,
        seat: MahjongStoolSeatEntity,
        seatTopY: Double,
    ): Boolean {
        val halfWidth = entity.width / 2.0
        val headY = MahjongStoolSeatGeometry.passengerFeetY(seat.y) + entity.height
        val body = Box(seat.x - halfWidth, seatTopY, seat.z - halfWidth, seat.x + halfWidth, headY, seat.z + halfWidth)
        return world.isSpaceEmpty(entity, body)
    }

    /** [MahjongStoolSeatService] 的常數。 */
    private companion object {
        /** 方塊中心相對方塊角落的偏移。 */
        const val HALF_BLOCK: Double = 0.5

        /** 生成入口診斷訊息中的來源名稱。 */
        const val SPAWN_SOURCE: String = "mahjong_stool_seat"
    }
}
