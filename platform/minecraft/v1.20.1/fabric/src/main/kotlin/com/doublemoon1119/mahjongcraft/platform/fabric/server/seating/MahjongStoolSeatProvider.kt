package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

/**
 * 將固定席位上的內建麻將凳轉換成通用座位目標。
 *
 * 只檢查請求指定的方塊位置，不搜尋附近的其他凳子；實際入座由
 * [MahjongStoolSeatService] 執行。
 *
 * @property seatService 內建麻將凳的原生入座服務。
 */
class MahjongStoolSeatProvider(
    private val seatService: MahjongStoolSeatService,
) : SeatProvider<ServerWorld, Entity> {
    /**
     * 解析固定席位上的內建麻將凳。
     *
     * @param world 要查詢的伺服器世界。
     * @param request 固定席位的請求與幾何資料。
     * @return 位置已載入且確實為麻將凳時的單一目標，否則為空列表。
     */
    override fun resolve(world: ServerWorld, request: SeatRequest): List<SeatTarget<ServerWorld, Entity>> {
        if (request.dimensionId != world.registryKey.value.toString()) return emptyList()
        val stoolPos = BlockPos.ofFloored(request.seat.x, request.seat.y, request.seat.z)
        if (!world.chunkManager.isChunkLoaded(stoolPos.x shr 4, stoolPos.z shr 4)) return emptyList()
        if (world.getBlockState(stoolPos).block !is MahjongStoolBlock) return emptyList()
        return listOf(MahjongStoolSeatTarget(world, stoolPos, request, seatService))
    }
}

/**
 * 一張固定位置麻將凳的原生座位目標。
 *
 * @property world 座位所在的伺服器世界。
 * @property stoolPos 座位凳子的方塊位置。
 * @property request 固定席位要求的幾何資料。
 * @property seatService 執行實際乘坐操作的服務。
 */
private class MahjongStoolSeatTarget(
    private val world: ServerWorld,
    private val stoolPos: BlockPos,
    private val request: SeatRequest,
    private val seatService: MahjongStoolSeatService,
) : SeatTarget<ServerWorld, Entity> {
    /** 以維度、方塊座標與單一子座位識別這張凳子。 */
    override val identity: SeatIdentity = SeatIdentity(
        dimensionId = world.registryKey.value.toString(),
        kind = BLOCK_KIND,
        key = "${stoolPos.x},${stoolPos.y},${stoolPos.z}",
        slot = MAIN_SLOT,
    )

    /**
     * 回報凳面中心與固定席位要求的面向。
     *
     * @param world 要查詢的伺服器世界。
     * @param rider 預計乘坐的實體。
     * @return 凳子目前的乘坐幾何。
     */
    override fun geometry(world: ServerWorld, rider: Entity): SeatGeometry {
        if (world !== this.world || !world.chunkManager.isChunkLoaded(stoolPos.x shr 4, stoolPos.z shr 4)) {
            return SeatGeometry(
                x = request.seat.x,
                y = request.seat.y,
                z = request.seat.z,
                yaw = request.seat.yaw,
            )
        }
        val stool = world.getBlockState(stoolPos).block as? MahjongStoolBlock
            ?: return SeatGeometry(stoolPos.x + HALF_BLOCK, stoolPos.y.toDouble(), stoolPos.z + HALF_BLOCK, request.seat.yaw)
        val seatTopY = stoolPos.y + stool.design.seatHeight
        val seatY = MahjongStoolSeatGeometry.seatY(seatTopY)
        return SeatGeometry(
            x = stoolPos.x + HALF_BLOCK,
            y = MahjongStoolSeatGeometry.passengerFeetY(seatY),
            z = stoolPos.z + HALF_BLOCK,
            yaw = request.seat.yaw,
        )
    }

    /**
     * 檢查凳子目前是否能由 [rider] 乘坐。
     *
     * @param world 要查詢的伺服器世界。
     * @param rider 預計乘坐的實體。
     * @return 目前的座位狀態。
     */
    override fun inspect(world: ServerWorld, rider: Entity): SeatState {
        if (world !== this.world || !world.chunkManager.isChunkLoaded(stoolPos.x shr 4, stoolPos.z shr 4)) {
            return SeatState.INVALID
        }
        if (world.getBlockState(stoolPos).block !is MahjongStoolBlock) return SeatState.INVALID
        val ownSeat = rider.vehicle as? MahjongStoolSeatEntity
        if (ownSeat != null && ownSeat.world === world && ownSeat.blockPos == stoolPos) return SeatState.SEATED
        if (MahjongStoolSeatEntity.seatsAt(world, stoolPos).isNotEmpty()) return SeatState.OCCUPIED
        if (rider.hasVehicle()) return SeatState.UNSUPPORTED
        return if (seatService.canSit(world, stoolPos, rider)) SeatState.AVAILABLE else SeatState.BLOCKED
    }

    /**
     * 在執行前重新檢查並嘗試讓 [rider] 乘坐凳子。
     *
     * @param world 要執行入座的伺服器世界。
     * @param rider 要乘坐凳子的實體。
     * @return 入座後的座位狀態；服務拒絕時不留下自有座位實體。
     */
    override fun trySit(world: ServerWorld, rider: Entity): SeatState {
        val current = inspect(world, rider)
        if (current != SeatState.AVAILABLE) return current
        return if (seatService.sit(world, stoolPos, rider, request.seat.yaw)) {
            SeatState.SEATED
        } else {
            SeatState.FAILED
        }
    }

    /** 此 provider 使用的內建方塊座位種類。 */
    private companion object {
        /** 方塊座位的識別種類。 */
        const val BLOCK_KIND: String = "minecraft:block"

        /** 麻將凳唯一的子座位名稱。 */
        const val MAIN_SLOT: String = "main"

        /** 方塊中心的水平偏移。 */
        const val HALF_BLOCK: Double = 0.5
    }
}
