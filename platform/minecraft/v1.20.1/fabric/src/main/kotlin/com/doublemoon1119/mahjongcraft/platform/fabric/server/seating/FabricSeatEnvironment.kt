package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatEnvironment
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * 原生乘坐者的位置與載具快照，僅在單次入座嘗試內使用。
 *
 * @property world 原本世界。
 * @property x 原本 X 座標。
 * @property y 原本腳底高度。
 * @property z 原本 Z 座標。
 * @property yaw 原本朝向。
 * @property pitch 原本仰角。
 * @property vehicle 原本載具，不用於強制卸乘。
 */
internal data class FabricSeatSnapshot(
    val world: ServerWorld,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val vehicle: Entity?,
)

/** 原生站立檢查與違約回復；不強制載入區塊，只在局間換位時讓玩家離開本桌的座位，不卸除其他載具。 */
internal class FabricSeatEnvironment : SeatEnvironment<ServerWorld, Entity, FabricSeatSnapshot> {
    /** 複製乘坐者目前的世界、位置與載具，不保留可變的位置物件。 */
    override fun snapshot(rider: Entity): FabricSeatSnapshot = FabricSeatSnapshot(
        world = rider.world as ServerWorld,
        x = rider.x,
        y = rider.y,
        z = rider.z,
        yaw = rider.yaw,
        pitch = rider.pitch,
        vehicle = rider.vehicle,
    )

    /** 確認只讀查詢或失敗嘗試未改動原狀態。 */
    override fun unchanged(rider: Entity, snapshot: FabricSeatSnapshot): Boolean = rider.world === snapshot.world &&
        rider.x == snapshot.x &&
        rider.y == snapshot.y &&
        rider.z == snapshot.z &&
        rider.yaw == snapshot.yaw &&
        rider.pitch == snapshot.pitch &&
        rider.vehicle === snapshot.vehicle

    /** 判斷是否已有不能由自動入座強制卸除的載具。 */
    override fun hasVehicle(rider: Entity): Boolean = rider.hasVehicle()

    /** 固定站立點安全且乘坐者沒有載具時才移動。 */
    override fun stand(
        world: ServerWorld,
        rider: Entity,
        request: SeatRequest,
    ): Boolean {
        val seat = request.seat
        if (rider.hasVehicle() || !safeStanding(world, rider, seat.x, seat.y, seat.z)) return false
        return move(world, rider, seat.x, seat.y, seat.z, seat.yaw, 0f)
    }

    /** 驗證提供者已立即完成腳底定位，不要求已入座本人重新轉向。 */
    override fun positioned(
        world: ServerWorld,
        rider: Entity,
        geometry: SeatGeometry,
    ): Boolean = rider.world === world && rider.squaredDistanceTo(geometry.x, geometry.y, geometry.z) <= POSITION_EPSILON

    /** 從本桌的座位上起身；乘坐者已確認坐在本桌某個座位上。 */
    override fun leaveSeat(rider: Entity) {
        rider.stopRiding()
    }

    /** 不干預載具；僅在目前位置不安全且原位置仍安全時嘗試回復。 */
    override fun recover(rider: Entity, snapshot: FabricSeatSnapshot) {
        if (unchanged(rider, snapshot) || rider.hasVehicle() || snapshot.vehicle != null) return
        val currentWorld = rider.world as? ServerWorld ?: return
        if (safeStanding(currentWorld, rider, rider.x, rider.y, rider.z)) return
        if (safeStanding(snapshot.world, rider, snapshot.x, snapshot.y, snapshot.z)) {
            move(snapshot.world, rider, snapshot.x, snapshot.y, snapshot.z, snapshot.yaw, snapshot.pitch)
        }
    }

    /**
     * 檢查已載入區域內的腳下支撐、方塊與實體，不把玩家傳入堵塞位置。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @param x 目標 X。
     * @param y 腳底高度。
     * @param z 目標 Z。
     * @return 可安全站立時為 true。
     */
    private fun safeStanding(
        world: ServerWorld,
        rider: Entity,
        x: Double,
        y: Double,
        z: Double,
    ): Boolean {
        val halfWidth = rider.width / 2.0
        val body = Box(x - halfWidth, y, z - halfWidth, x + halfWidth, y + rider.height, z + halfWidth)
        val minimum = BlockPos.ofFloored(body.minX - CHUNK_MARGIN, y - SUPPORT_DEPTH, body.minZ - CHUNK_MARGIN)
        val maximum = BlockPos.ofFloored(body.maxX + CHUNK_MARGIN, body.maxY, body.maxZ + CHUNK_MARGIN)
        if (minimum.y < world.bottomY || maximum.y >= world.topY || !world.worldBorder.contains(body)) return false
        for (chunkX in (minimum.x shr 4)..(maximum.x shr 4)) {
            for (chunkZ in (minimum.z shr 4)..(maximum.z shr 4)) {
                if (!world.chunkManager.isChunkLoaded(chunkX, chunkZ)) return false
            }
        }
        if (!world.isSpaceEmpty(rider, body)) return false
        if (world.getOtherEntities(rider, body) { !it.isRemoved && !it.isSpectator && (it is LivingEntity || it.isCollidable) }.isNotEmpty()) return false
        if (!world.getFluidState(BlockPos.ofFloored(x, y, z)).isEmpty) return false
        return world.getBlockCollisions(rider, body.offset(0.0, -SUPPORT_DEPTH, 0.0)).iterator().hasNext()
    }

    /**
     * 執行已驗證的移動；非玩家實體只支援同世界，跨世界代理交由其生命週期處理。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @param x 目標 X。
     * @param y 目標 Y。
     * @param z 目標 Z。
     * @param yaw 水平角度。
     * @param pitch 仰角。
     * @return 已執行移動時為 true。
     */
    private fun move(
        world: ServerWorld,
        rider: Entity,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
    ): Boolean {
        if (rider is ServerPlayerEntity) {
            rider.teleport(world, x, y, z, yaw, pitch)
        } else if (rider.world === world) {
            rider.refreshPositionAndAngles(x, y, z, yaw, pitch)
        } else {
            return false
        }
        return true
    }

    /** 安全檢查的小量與容許誤差。 */
    private companion object {
        /** 腳底支撐查詢深度。 */
        const val SUPPORT_DEPTH = 0.05

        /** 碰撞查詢可能讀取相鄰方塊，先確認其區塊載入。 */
        const val CHUNK_MARGIN = 1.0

        /** 成功定位的平方距離誤差。 */
        const val POSITION_EPSILON = 0.0001
    }
}
