package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureEntities
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/** 範例椅子手動右鍵與座位 provider 共用的入座服務。 */
class ExampleChairSeatService {
    /** 生成暫時支撐並入座；失敗時清理本次支撐，已上座者則還原位置與面向。 */
    fun sit(world: ServerWorld, pos: BlockPos, rider: Entity, yaw: Float): Boolean {
        if (!ExampleFurnitureEntities.isRegistered()) return false
        if (!canSit(world, pos, rider)) return false
        val originalX = rider.x
        val originalY = rider.y
        val originalZ = rider.z
        val originalYaw = rider.yaw
        val originalPitch = rider.pitch
        val seat = ExampleChairSeatEntity(ExampleFurnitureEntities.chairSeat, world).apply {
            refreshPositionAndAngles(
                pos.x + 0.5,
                ExampleSeatGeometry.chairSeatY(pos.y),
                pos.z + 0.5,
                yaw,
                0.0f,
            )
            markOccupancyChecked()
        }
        return ExampleSeatAttempt.run(
            create = { seat },
            spawn = { world.spawnEntity(it) },
            mount = { rider.startRiding(it, true) },
            finish = {
                it.updatePassengerPosition(rider)
                faceTable(rider, yaw)
                rider.vehicle === it
            },
            restore = {
                if (rider.vehicle === seat) rider.stopRiding()
                if (rider is ServerPlayerEntity) {
                    rider.networkHandler.requestTeleport(originalX, originalY, originalZ, originalYaw, originalPitch)
                } else {
                    rider.refreshPositionAndAngles(originalX, originalY, originalZ, originalYaw, originalPitch)
                }
            },
            discard = { it.discard() },
        )
    }

    /** 檢查椅子存在、沒有其他座位、玩家沒有載具且身體空間可用。 */
    fun canSit(world: ServerWorld, pos: BlockPos, rider: Entity): Boolean {
        if (!ExampleFurnitureEntities.isRegistered()) return false
        if (!world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return false
        if (world.getBlockState(pos).block !== ExampleFurnitureBlocks.chair) return false
        if (rider.world !== world) return false
        if (ExampleFurnitureSeatRules.occupancyState(
                hasVehicle = rider.hasVehicle(),
                occupied = ExampleChairSeatEntity.seatsAt(world, pos).isNotEmpty(),
            ) != SeatState.AVAILABLE
        ) {
            return false
        }
        val seatY = ExampleSeatGeometry.chairSeatY(pos.y)
        val feetY = ExampleSeatGeometry.passengerFeetY(seatY)
        val body = Box(
            pos.x + 0.5 - rider.width / 2.0,
            pos.y + ExampleSeatGeometry.CHAIR_TOP_HEIGHT,
            pos.z + 0.5 - rider.width / 2.0,
            pos.x + 0.5 + rider.width / 2.0,
            feetY + rider.height,
            pos.z + 0.5 + rider.width / 2.0,
        )
        return ExampleFurnitureSpace.isClear(world, rider, body)
    }

    /** 將成功入座者面向標準座位要求，保留原本仰角。 */
    private fun faceTable(rider: Entity, yaw: Float) {
        if (rider is ServerPlayerEntity) {
            rider.networkHandler.requestTeleport(rider.x, rider.y, rider.z, yaw, rider.pitch)
        } else {
            rider.refreshPositionAndAngles(rider.x, rider.y, rider.z, yaw, rider.pitch)
        }
    }
}
