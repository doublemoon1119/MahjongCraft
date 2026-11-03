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
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/** 建立椅子、長凳與坐墊的座位 provider；登記時不讀取原生世界。 */
object ExampleFurnitureSeatProviders {
    /** 建立三種家具的 provider 描述。 */
    fun definitions(): List<RegisteredSeatProvider<ServerWorld, Entity>> = listOf(
        RegisteredSeatProvider(
            id = ExampleFurnitureIds.CHAIR,
            provider = ExampleChairSeatProvider(ExampleChairSeatService(), ExampleFurnitureIds.CHAIR),
            priority = 0,
        ),
        RegisteredSeatProvider(
            id = ExampleFurnitureIds.BENCH,
            provider = ExampleChairSeatProvider(ExampleChairSeatService(), ExampleFurnitureIds.BENCH),
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
 * 只解析指定標準席位方塊的椅子或長凳。
 *
 * @property service 手動右鍵與自動入座共用的椅子服務。
 * @property furnitureId 本 provider 辨識的單格家具。
 */
private class ExampleChairSeatProvider(
    private val service: ExampleChairSeatService,
    private val furnitureId: String,
) : SeatProvider<ServerWorld, Entity> {
    /** 建立指定位置的單一椅子座位目標。 */
    override fun resolve(world: ServerWorld, request: SeatRequest): List<SeatTarget<ServerWorld, Entity>> {
        if (request.dimensionId != world.registryKey.value.toString()) return emptyList()
        if (!ExampleFurnitureEntities.isRegistered()) return emptyList()
        if (!ExampleFurnitureBlocks.isRegistered()) return emptyList()
        val pos = BlockPos.ofFloored(request.seat.x, request.seat.y, request.seat.z)
        if (!world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return emptyList()
        val expected = if (furnitureId == ExampleFurnitureIds.BENCH) ExampleFurnitureBlocks.bench else ExampleFurnitureBlocks.chair
        if (world.getBlockState(pos).block !== expected) return emptyList()
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
        if (world.getBlockState(pos).isOf(ExampleFurnitureBlocks.bench)) world.getBlockState(pos).get(Properties.HORIZONTAL_FACING).asRotation() else request.seat.yaw,
    )

    /** 查詢椅子是否有效、占用或可用。 */
    override fun inspect(world: ServerWorld, rider: Entity): SeatState {
        if (world !== this.world || rider.world !== world || !world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return SeatState.INVALID
        if (!ExampleFurnitureBlocks.isSeat(world.getBlockState(pos))) return SeatState.INVALID
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
            SeatState.AVAILABLE -> if (service.sit(world, pos, rider, geometry(world, rider).yaw)) SeatState.SEATED else SeatState.FAILED
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

    /** 查詢與其他入口相同的占用與碰撞規則。 */
    override fun inspect(world: ServerWorld, rider: Entity): SeatState = if (world !== this.world) SeatState.INVALID else ExampleCushionSeatService.inspect(world, cushion, rider)

    /** 與真人及人偶建立共用同一個掛載、定位及失敗還原流程。 */
    override fun trySit(world: ServerWorld, rider: Entity): SeatState {
        val current = inspect(world, rider)
        if (current != SeatState.AVAILABLE) return current
        return if (ExampleCushionSeatService.sit(world, cushion, rider, request.seat.yaw)) SeatState.SEATED else SeatState.FAILED
    }
}
