package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureEntities
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box

/** 真人手動入座、人偶建立與座位 provider 共用的坐墊服務。 */
internal object ExampleCushionSeatService {
    /**
     * 依實際坐墊檢查載入、占用及身體空間，不移動家具。
     *
     * @param world 乘坐世界。
     * @param cushion 座位本體。
     * @param rider 要入座的乘坐者。
     * @return 本格座位目前狀態。
     */
    fun inspect(
        world: ServerWorld,
        cushion: ExampleCushionEntity,
        rider: Entity,
    ): SeatState {
        if (rider.world !== world || cushion.world !== world || cushion.isRemoved || !ExampleFurnitureEntities.isRegistered()) return SeatState.INVALID
        if (!world.chunkManager.isChunkLoaded(cushion.blockX shr 4, cushion.blockZ shr 4)) return SeatState.INVALID
        if (rider.vehicle === cushion) return SeatState.SEATED
        val occupancy = ExampleFurnitureSeatRules.occupancyState(rider.hasVehicle(), cushion.hasPassengers())
        if (occupancy != SeatState.AVAILABLE) return occupancy
        val body = Box(
            cushion.x - rider.width / 2.0,
            cushion.y + 0.2,
            cushion.z - rider.width / 2.0,
            cushion.x + rider.width / 2.0,
            ExampleSeatGeometry.cushionFeetY(cushion.y) + rider.height,
            cushion.z + rider.width / 2.0,
        )
        return if (ExampleFurnitureSpace.isClear(
                world,
                rider,
                body,
                cushion,
            )
        ) {
            SeatState.AVAILABLE
        } else {
            SeatState.BLOCKED
        }
    }

    /**
     * 掛載後定位並核對，失敗時還原乘坐者，永不刪除既有坐墊。
     *
     * @param world 乘坐世界。
     * @param cushion 座位本體。
     * @param rider 要入座的乘坐者。
     * @param yaw 本次面向。
     * @return 入座成功時為 true。
     */
    fun sit(
        world: ServerWorld,
        cushion: ExampleCushionEntity,
        rider: Entity,
        yaw: Float,
    ): Boolean {
        if (inspect(
                world,
                cushion,
                rider,
            ) != SeatState.AVAILABLE
        ) {
            return false
        }
        val original = rider.pos
        val originalYaw = rider.yaw
        val pitch = rider.pitch
        return ExampleSeatAttempt.run(
            create = { cushion },
            spawn = { true },
            mount = { rider.startRiding(it, true) },
            finish = {
                it.updatePassengerPosition(rider)
                if (rider is ServerPlayerEntity) {
                    rider.networkHandler.requestTeleport(
                        rider.x,
                        rider.y,
                        rider.z,
                        yaw,
                        pitch,
                    )
                } else {
                    rider.refreshPositionAndAngles(
                        rider.x,
                        rider.y,
                        rider.z,
                        yaw,
                        pitch,
                    )
                }
                rider.vehicle === it
            },
            restore = {
                if (rider.vehicle === cushion) rider.stopRiding()
                if (rider is ServerPlayerEntity) {
                    rider.networkHandler.requestTeleport(
                        original.x,
                        original.y,
                        original.z,
                        originalYaw,
                        pitch,
                    )
                } else {
                    rider.refreshPositionAndAngles(
                        original.x,
                        original.y,
                        original.z,
                        originalYaw,
                        pitch,
                    )
                }
            },
            discard = {},
        )
    }
}
