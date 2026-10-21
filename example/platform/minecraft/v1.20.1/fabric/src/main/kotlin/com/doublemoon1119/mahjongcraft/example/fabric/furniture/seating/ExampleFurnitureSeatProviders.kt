package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureEntities
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/** 建立範例椅子與坐墊座位 provider 的入口；建立時不讀取原生世界。 */
object ExampleFurnitureSeatProviders {
    /** 產生兩個可由 Fabric extension 登記的 provider 描述。 */
    fun definitions(): List<RegisteredSeatProvider<ServerWorld, Entity>> = listOf(
        RegisteredSeatProvider(
            id = ExampleFurnitureIds.CHAIR,
            provider = ExampleChairSeatProvider(ExampleChairSeatService()),
            priority = 0,
        ),
        RegisteredSeatProvider(
            id = ExampleFurnitureIds.CUSHION,
            provider = ExampleCushionSeatProvider(),
            priority = 0,
        ),
    )
}

/**
 * 只解析指定標準席位方塊的範例椅子 provider。
 *
 * @property service 手動右鍵與自動入座共用的椅子服務。
 */
private class ExampleChairSeatProvider(
    private val service: ExampleChairSeatService,
) : SeatProvider<ServerWorld, Entity> {
    /** 建立指定位置的單一椅子座位目標。 */
    override fun resolve(world: ServerWorld, request: SeatRequest): List<SeatTarget<ServerWorld, Entity>> {
        if (request.dimensionId != world.registryKey.value.toString()) return emptyList()
        if (!ExampleFurnitureEntities.isRegistered()) return emptyList()
        if (!ExampleFurnitureBlocks.isRegistered()) return emptyList()
        val pos = BlockPos.ofFloored(request.seat.x, request.seat.y, request.seat.z)
        if (!world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return emptyList()
        if (world.getBlockState(pos).block !== ExampleFurnitureBlocks.chair) return emptyList()
        return listOf(ExampleChairSeatTarget(world, pos, request, service))
    }
}

/**
 * 範例方塊椅子的座位目標，每次入座前重新檢查所在方塊。
 *
 * @property world 解析時的世界，禁止跨世界重用目標。
 * @property pos 椅子所在的固定方塊位置。
 * @property request 本次標準席位與面向要求。
 * @property service 椅子的入座服務。
 */
private class ExampleChairSeatTarget(
    private val world: ServerWorld,
    private val pos: BlockPos,
    private val request: SeatRequest,
    private val service: ExampleChairSeatService,
) : SeatTarget<ServerWorld, Entity> {
    override val identity: SeatIdentity = SeatIdentity(
        dimensionId = world.registryKey.value.toString(),
        kind = "minecraft:block",
        key = "${pos.x},${pos.y},${pos.z}",
    )

    /** 回報椅子實際乘坐者腳底與標準席位面向。 */
    override fun geometry(world: ServerWorld, rider: Entity): SeatGeometry = SeatGeometry(
        pos.x + 0.5,
        ExampleSeatGeometry.passengerFeetY(ExampleSeatGeometry.chairSeatY(pos.y)),
        pos.z + 0.5,
        request.seat.yaw,
    )

    /** 查詢椅子是否有效、占用或可用。 */
    override fun inspect(world: ServerWorld, rider: Entity): SeatState {
        if (world !== this.world || rider.world !== world || !world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return SeatState.INVALID
        if (world.getBlockState(pos).block !== ExampleFurnitureBlocks.chair) return SeatState.INVALID
        val own = rider.vehicle as? ExampleChairSeatEntity
        if (own != null && own.world === world && own.blockPos == pos) return SeatState.SEATED
        if (ExampleChairSeatEntity.seatsAt(world, pos).isNotEmpty()) return SeatState.OCCUPIED
        if (rider.hasVehicle()) return SeatState.UNSUPPORTED
        return if (service.canSit(world, pos, rider)) SeatState.AVAILABLE else SeatState.BLOCKED
    }

    /** 重新檢查後讓乘坐者坐上椅子。 */
    override fun trySit(world: ServerWorld, rider: Entity): SeatState {
        val current = inspect(world, rider)
        return when (current) {
            SeatState.AVAILABLE -> if (service.sit(world, pos, rider, request.seat.yaw)) SeatState.SEATED else SeatState.FAILED
            else -> current
        }
    }
}

/** 在標準席位附近查詢已載入範圍內範例坐墊的 provider。 */
private class ExampleCushionSeatProvider : SeatProvider<ServerWorld, Entity> {
    /** 只查標準席位附近已載入的坐墊；實際乘坐幾何由本體再次篩選。 */
    override fun resolve(world: ServerWorld, request: SeatRequest): List<SeatTarget<ServerWorld, Entity>> {
        if (request.dimensionId != world.registryKey.value.toString()) return emptyList()
        if (!ExampleFurnitureEntities.isRegistered()) return emptyList()
        val center = request.seat
        val box = Box(center.x - 1.0, center.y - 2.0, center.z - 1.0, center.x + 1.0, center.y + 2.0, center.z + 1.0)
        return world.getEntitiesByClass(ExampleCushionEntity::class.java, box) { !it.isRemoved }
            .map { ExampleCushionSeatTarget(world, it, request) }
    }
}

/**
 * 範例坐墊的座位目標，使用坐墊目前位置，不把家具拉回理想位置。
 *
 * @property world 解析時的世界。
 * @property cushion 作為座位本體的坐墊實體。
 * @property request 本次標準席位與面向要求。
 */
private class ExampleCushionSeatTarget(
    private val world: ServerWorld,
    private val cushion: ExampleCushionEntity,
    private val request: SeatRequest,
) : SeatTarget<ServerWorld, Entity> {
    override val identity: SeatIdentity = ExampleFurnitureSeatRules.cushionIdentity(
        dimensionId = world.registryKey.value.toString(),
        uuidAsString = cushion.uuidAsString,
    )

    /** 回報坐墊目前實際位置與標準席位面向。 */
    override fun geometry(world: ServerWorld, rider: Entity): SeatGeometry = SeatGeometry(
        cushion.x,
        ExampleSeatGeometry.cushionFeetY(cushion.y),
        cushion.z,
        request.seat.yaw,
    )

    /** 查詢坐墊是否仍存在、占用或可用。 */
    override fun inspect(world: ServerWorld, rider: Entity): SeatState {
        if (world !== this.world || rider.world !== world || cushion.world !== world || cushion.isRemoved || !ExampleFurnitureEntities.isRegistered()) return SeatState.INVALID
        if (!world.chunkManager.isChunkLoaded(cushion.blockX shr 4, cushion.blockZ shr 4)) return SeatState.INVALID
        if (rider.vehicle === cushion) return SeatState.SEATED
        val occupancy = ExampleFurnitureSeatRules.occupancyState(
            hasVehicle = rider.hasVehicle(),
            occupied = cushion.hasPassengers(),
        )
        if (occupancy != SeatState.AVAILABLE) return occupancy
        val geometry = geometry(world, rider)
        val body = Box(
            geometry.x - rider.width / 2.0,
            cushion.y + 0.2,
            geometry.z - rider.width / 2.0,
            geometry.x + rider.width / 2.0,
            geometry.y + rider.height,
            geometry.z + rider.width / 2.0,
        )
        return if (ExampleFurnitureSpace.isClear(world, rider, body, cushion)) SeatState.AVAILABLE else SeatState.BLOCKED
    }

    /** 重新檢查後讓乘坐者坐上坐墊，不移動坐墊本身。 */
    override fun trySit(world: ServerWorld, rider: Entity): SeatState {
        val current = inspect(world, rider)
        if (current != SeatState.AVAILABLE) return current
        val originalX = rider.x
        val originalY = rider.y
        val originalZ = rider.z
        val originalYaw = rider.yaw
        val pitch = rider.pitch
        val seated = ExampleSeatAttempt.run(
            create = { cushion },
            spawn = { true },
            mount = { rider.startRiding(it, true) },
            finish = {
                it.updatePassengerPosition(rider)
                if (rider is ServerPlayerEntity) {
                    rider.networkHandler.requestTeleport(rider.x, rider.y, rider.z, request.seat.yaw, pitch)
                } else {
                    rider.refreshPositionAndAngles(rider.x, rider.y, rider.z, request.seat.yaw, pitch)
                }
                rider.vehicle === it
            },
            restore = {
                if (rider.vehicle === cushion) rider.stopRiding()
                if (rider is ServerPlayerEntity) {
                    rider.networkHandler.requestTeleport(originalX, originalY, originalZ, originalYaw, pitch)
                } else {
                    rider.refreshPositionAndAngles(originalX, originalY, originalZ, originalYaw, pitch)
                }
            },
            discard = {},
        )
        return if (seated) SeatState.SEATED else SeatState.FAILED
    }
}
