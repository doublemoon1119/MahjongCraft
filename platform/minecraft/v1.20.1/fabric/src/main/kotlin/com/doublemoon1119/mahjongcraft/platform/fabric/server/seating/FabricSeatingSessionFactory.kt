package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.entity.Entity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.math.floor
import kotlin.uuid.Uuid

/**
 * 為玩家與 AI 角色建立相同的標準席位與安全協調器。
 *
 * @property tableLocationRegistry 目前桌子的位置。
 * @property serverHolder 目前的伺服器。
 * @property seatProviders 已凍結的座位來源。
 * @property failurePolicy 第三方失敗的隔離政策。
 */
internal class FabricSeatingSessionFactory(
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val seatProviders: FabricSeatProviderRegistryImpl,
    private val failurePolicy: ExtensionFailurePolicy,
) {
    /** 提供者違約與執行失敗的診斷。 */
    private val logger = mahjongCraftLogger(FabricSeatingSessionFactory::class)

    /**
     * 解析桌子位置並建立這次入座使用的席位與協調器。
     *
     * @param tableId 對局桌子 UUID。
     * @param seatedPlayerIds 依座位順序排列的玩家 UUID。
     * @return 這次入座的選擇資料；桌子不存在、區塊未載入或不是麻將桌時為 null。
     */
    fun prepare(tableId: Uuid, seatedPlayerIds: List<Uuid>): FabricSeatingSession? = prepareLayout(tableId, MahjongTableSeatLayout.forPlayerCount(seatedPlayerIds.size))

    /**
     * 取得本桌四個標準等待席位，不把等待位置當成正式名冊順序。
     *
     * @param tableId 桌子 UUID。
     * @return 已載入的四側席位；桌子或區塊不可用時為 null。
     */
    fun prepareTable(tableId: Uuid): FabricSeatingSession? = prepareLayout(tableId, MahjongTableSeatLayout.FOUR_PLAYER)

    /**
     * 依標準布局建立同一個桌子座標框架。
     *
     * @param tableId 桌子 UUID。
     * @param layout 固定席位布局。
     * @return 已驗證的原生席位資料。
     */
    private fun prepareLayout(tableId: Uuid, layout: MahjongTableSeatLayout): FabricSeatingSession? {
        val location = tableLocationRegistry.get(tableId)?.location ?: return null
        val world = resolveWorld(location) ?: return null
        check(world.server.isOnThread) { "Automatic seating must run on the server thread" }
        val controllerPos = BlockPos(location.x, location.y, location.z)
        if (!world.chunkManager.isChunkLoaded(controllerPos.x shr 4, controllerPos.z shr 4)) return null
        val tableFacing = MahjongTableLookup.find(world, controllerPos, tableId)?.facing ?: return null
        val requests = MahjongSeatingTableLayout.seatRequests(
            layout = layout,
            tableId = tableId,
            dimensionId = location.dimensionId,
            controllerX = location.x,
            controllerY = location.y,
            controllerZ = location.z,
            tableFacing = tableFacing,
        )
        if (requests.any { request ->
                val x = floor(request.seat.x).toInt()
                val z = floor(request.seat.z).toInt()
                (((x - 1) shr 4)..((x + 1) shr 4)).any { cx ->
                    (((z - 1) shr 4)..((z + 1) shr 4)).any { cz -> !world.chunkManager.isChunkLoaded(cx, cz) }
                }
            }
        ) {
            return null
        }
        val coordinator = SeatAssignmentCoordinator<ServerWorld, Entity, FabricSeatSnapshot>(
            providers = seatProviders.entries(),
            environment = FabricSeatEnvironment(),
            failurePolicy = failurePolicy,
        ) { providerId, rider, cause ->
            logger.warn("Seat provider {} failed for table {}, rider {}", providerId, tableId, rider.uuid, cause)
        }
        return FabricSeatingSession(
            world = world,
            requests = requests,
            coordinator = coordinator,
            location = location,
            facing = tableFacing,
        )
    }

    /**
     * 由版本無關 dimension ID 取得目前 server session 的世界。
     *
     * @param location 含 dimension ID 的桌子位置。
     * @return 對應的已載入伺服器世界；無法解析時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }
}
