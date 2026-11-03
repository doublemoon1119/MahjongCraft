package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatCollisionPolicy
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.math.abs

/**
 * 開局提交前的入座安排；失敗時恢復本次安排之前的乘坐與位置。
 *
 * @property riders 正式名冊中的在線玩家與借用角色，尚未生成的內建角色為 null。
 * @property session 正式席位的選擇資料。
 * @property table 本桌四側等待席位。
 */
internal class FabricRoomStartSeating(
    private val riders: List<Entity?>,
    private val session: FabricSeatingSession,
    private val table: FabricSeatingSession,
) {
    /** 本次安排前的狀態，只保存目前名冊裡的乘坐者。 */
    private val originals = riders.filterNotNull().associateWith { FabricSeatEnvironment().snapshot(it) }

    /** 入座拒絕或原狀無法恢復時的診斷。 */
    private val logger = mahjongCraftLogger(FabricRoomStartSeating::class)

    /**
     * 先釋放本桌舊席位，再依正式排位入座；占用與安全檢查由既有協調器負責。
     *
     * @return 全部現有成員完成入座或允許的站立時為 true。
     */
    fun arrange(): Boolean {
        val results = SeatCollisionScope.with(riders, session.requests) {
            session.coordinator.seatAll(
                world = session.world,
                riders = riders,
                requests = session.requests,
                tableRequests = table.requests,
            )
        }
        val participants = riders.filterNotNull().filter {
            val body = it.boundingBox
            SeatCollisionPolicy.blocks(it.isRemoved, it.isSpectator, it is LivingEntity || it.isCollidable) &&
                body.maxX > body.minX &&
                body.maxY > body.minY &&
                body.maxZ > body.minZ
        }
        if (participants.indices.any { index -> participants.drop(index + 1).any { other -> participants[index].boundingBox.intersects(other.boundingBox) } }) {
            logger.warn("Room start seating rejected because final rider bounds overlap tableId={}", session.requests.first().tableId)
            return false
        }
        return results.withIndex().all { (index, result) ->
            val rider = riders[index]
            val accepted = rider == null || result == SeatAssignmentResult.SEATED || result == SeatAssignmentResult.STANDING
            if (!accepted) logger.warn("Room start seating rejected riderId={} result={}", rider.uuid, result)
            accepted
        }
    }

    /** 恢復本次移動的成員；先全部離開新載具，避免交換位置時阻擋彼此。 */
    fun rollback() {
        val changed = originals.filter { (rider, original) -> !FabricSeatEnvironment().unchanged(rider, original) }
        val temporary = changed.keys.mapNotNull { it.vehicle }.filter { vehicle -> originals.values.none { it.vehicle === vehicle } }
        changed.keys.forEach { it.stopRiding() }
        for ((rider, original) in changed) {
            if (!rider.isAlive || rider.world !== original.world) {
                logger.warn("Unable to restore start seating riderId={}", rider.uuid)
                continue
            }
            if (rider is ServerPlayerEntity) {
                rider.networkHandler.requestTeleport(
                    original.x,
                    original.y,
                    original.z,
                    original.yaw,
                    original.pitch,
                )
            } else {
                rider.refreshPositionAndAngles(
                    original.x,
                    original.y,
                    original.z,
                    original.yaw,
                    original.pitch,
                )
            }
        }
        for ((rider, original) in changed) {
            if (!rider.isAlive || rider.world !== original.world) continue
            val vehicle = original.vehicle
            if (vehicle != null) {
                val restored = vehicle.isAlive && rider.startRiding(vehicle, true)
                if (!restored) {
                    val request = table.requests.firstOrNull { request ->
                        abs(request.seat.x - original.x) <= 0.25 && abs(request.seat.z - original.z) <= 0.25
                    }
                    val recovery = request != null && table.coordinator.assign(table.world, rider, request) == SeatAssignmentResult.SEATED
                    if (!recovery) logger.warn("Unable to restore start seat riderId={}", rider.uuid)
                }
            }
        }
        temporary.filterIsInstance<MahjongStoolSeatEntity>().filterNot { it.hasPassengers() }.forEach(Entity::discard)
    }
}
