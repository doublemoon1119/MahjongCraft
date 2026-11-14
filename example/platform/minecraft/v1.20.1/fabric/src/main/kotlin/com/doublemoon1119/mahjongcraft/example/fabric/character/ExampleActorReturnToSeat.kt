package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleSeatGeometry
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleChairSeatService
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionSeatService
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSpace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.abs

/** 範例人偶在指定位置附近自行回座，沒有座位時回原位站立。 */
internal object ExampleActorReturnToSeat {
    /**
     * 優先使用指定位置的範例家具；沒有家具時，確認空間可用且腳下有支撐後回原位站立。
     *
     * @param actor 仍在本場接管中的人偶。
     * @param request 最新指定席位。
     */
    fun tryReturn(actor: ExampleActorEntity, request: SeatRequest) {
        val world = actor.world as? ServerWorld ?: return
        if (!actor.control.isActive || !actor.isAlive || actor.hasVehicle() || world.registryKey.value.toString() != request.dimensionId) return
        val seat = request.seat
        if (!isNear(
                x = actor.x,
                y = actor.y,
                z = actor.z,
                seatX = seat.x,
                seatY = seat.y,
                seatZ = seat.z,
            )
        ) {
            return
        }
        val pos = BlockPos.ofFloored(
            seat.x,
            seat.y,
            seat.z,
        )
        if (!world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return
        val state = world.getBlockState(pos)
        if (ExampleFurnitureBlocks.isSeat(state)) {
            if (state.isOf(ExampleFurnitureBlocks.bench) && abs(MathHelper.wrapDegrees(state.get(Properties.HORIZONTAL_FACING).asRotation() - seat.yaw)) > 15f) return
            ExampleChairSeatService().sit(
                world = world,
                pos = pos,
                rider = actor,
                yaw = seat.yaw,
            )
            return
        }
        val candidates = world.getEntitiesByClass(
            ExampleCushionEntity::class.java,
            Box(
                seat.x - 0.5,
                seat.y - 1.0,
                seat.z - 0.5,
                seat.x + 0.5,
                seat.y + 2.0,
                seat.z + 0.5,
            ),
        ) { cushion ->
            !cushion.isRemoved && abs(cushion.x - seat.x) <= 0.25 && abs(cushion.z - seat.z) <= 0.25 && abs(ExampleSeatGeometry.cushionFeetY(cushion.y) - seat.y) <= 1.0
        }
        if (candidates.isNotEmpty()) {
            val cushion = candidates.singleOrNull() ?: return
            ExampleCushionSeatService.sit(
                world = world,
                cushion = cushion,
                rider = actor,
                yaw = seat.yaw,
            )
            return
        }
        if (actor.x == seat.x && actor.y == seat.y && actor.z == seat.z && abs(MathHelper.wrapDegrees(actor.yaw - seat.yaw)) < 0.001f) return
        val body = actor.boundingBox.offset(
            seat.x - actor.x,
            seat.y - actor.y,
            seat.z - actor.z,
        )
        standIfSafe(
            body = body,
            isClear = { bounds ->
                ExampleFurnitureSpace.isClear(
                    world = world,
                    actor = actor,
                    box = bounds,
                )
            },
            hasSupport = { bounds ->
                world.getBlockCollisions(
                    actor,
                    Box(
                        bounds.minX,
                        bounds.minY - SUPPORT_DEPTH,
                        bounds.minZ,
                        bounds.maxX,
                        bounds.minY,
                        bounds.maxZ,
                    ),
                ).any { !it.isEmpty }
            },
            move = {
                actor.refreshPositionAndAngles(
                    seat.x,
                    seat.y,
                    seat.z,
                    seat.yaw,
                    actor.pitch,
                )
                actor.headYaw = seat.yaw
                actor.bodyYaw = seat.yaw
                actor.velocity = Vec3d.ZERO
                actor.fallDistance = 0f
            },
        )
    }

    /**
     * 確認站立空間與腳下支撐後，才移動人偶。
     *
     * @param body 指定位置的完整站立範圍。
     * @param isClear 檢查區塊已載入、位於邊界內且沒有方塊或其他乘客阻擋。
     * @param hasSupport 檢查腳下是否有支撐方塊。
     * @param move 將同一人偶移到指定位置。
     * @return 已執行移動時為 true。
     */
    internal fun standIfSafe(
        body: Box,
        isClear: (Box) -> Boolean,
        hasSupport: (Box) -> Boolean,
        move: () -> Unit,
    ): Boolean {
        if (!isClear(body) || !hasSupport(body)) return false
        move()
        return true
    }

    /**
     * 依實際乘坐位置與公開容許誤差，確認是否仍坐在指定席位。
     *
     * @param x 人偶 X。
     * @param y 人偶腳底 Y。
     * @param z 人偶 Z。
     * @param seat 指定席位。
     * @return 仍坐在指定位置時為 true。
     */
    internal fun isAtSeat(
        x: Double,
        y: Double,
        z: Double,
        seat: SeatGeometry,
    ): Boolean {
        val dx = x - seat.x
        val dz = z - seat.z
        return dx * dx + dz * dz <= 0.25 * 0.25 && abs(y - seat.y) <= 1.0
    }

    /**
     * 比較前後指定位置的座標與朝向，判斷是否仍為同一席位。
     *
     * @param previous 上次指定位置。
     * @param current 目前指定位置。
     * @return 座標與等價朝向相同時為 true。
     */
    internal fun sameSeat(previous: SeatGeometry, current: SeatGeometry): Boolean = previous.x == current.x && previous.y == current.y && previous.z == current.z && abs(MathHelper.wrapDegrees(previous.yaw - current.yaw)) < 0.001f

    /**
     * 範例只在指定位置六格內返回，跑遠時留在原處。
     *
     * @param x 人偶 X。
     * @param y 人偶 Y。
     * @param z 人偶 Z。
     * @param seatX 指定席位 X。
     * @param seatY 指定席位 Y。
     * @param seatZ 指定席位 Z。
     * @return 座標有效且距離不超過六格時為 true。
     */
    internal fun isNear(
        x: Double,
        y: Double,
        z: Double,
        seatX: Double,
        seatY: Double,
        seatZ: Double,
    ): Boolean {
        val dx = x - seatX
        val dy = y - seatY
        val dz = z - seatZ
        val squared = dx * dx + dy * dy + dz * dz
        return squared.isFinite() && squared <= MAX_RETURN_DISTANCE_SQUARED
    }

    /** 腳下十六分之一格用於確認支撐，避免傳送到懸空位置。 */
    private const val SUPPORT_DEPTH = 1.0 / 16.0

    /** 範例允許六格內返回指定位置。 */
    private const val MAX_RETURN_DISTANCE_SQUARED = 36.0
}
