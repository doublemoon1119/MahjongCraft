package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftSeatingKeys
import net.minecraft.entity.Entity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid

/**
 * 使用原生座位能力實作 [MahjongSeatingPresenter]。
 *
 * 優先在固定席位入座；沒有座位時只傳送到通過安全檢查的站立點。
 * 正坐在本桌其他座位上的玩家先站起來再入座；已坐在自己座位上的玩家不動，占用或堵塞時保留原位置。此入口目前只解析在線玩家。
 *
 * @property tableLocationRegistry 查詢桌子目前的位置。
 * @property serverHolder 取得目前伺服器世界與在線玩家。
 * @property seatProviders 已完成註冊的原生座位來源。
 * @property failurePolicy 判斷座位來源丟出的失敗能否隔離。
 */
internal class FabricMahjongSeatingPresenter(
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val seatProviders: FabricSeatProviderRegistryImpl,
    private val failurePolicy: ExtensionFailurePolicy,
) : MahjongSeatingPresenter {
    /** 提供者違約與執行失敗的診斷。 */
    private val logger = mahjongCraftLogger(FabricMahjongSeatingPresenter::class)

    /**
     * 依固定席位安排在線玩家入座或安全站立；正坐在本桌其他座位上的玩家先站起來。
     *
     * @param tableId 對局桌子 UUID。
     * @param seatedPlayerIds 依座位順序排列的玩家 UUID。
     */
    override fun present(tableId: Uuid, seatedPlayerIds: List<Uuid>) {
        val seating = prepare(tableId, seatedPlayerIds) ?: return
        val players = seating.requests.indices.map { index -> seatedPlayerIds.getOrNull(index)?.let(serverHolder::findPlayer) }
        seating.coordinator.seatAll(seating.world, players, seating.requests).forEachIndexed { index, result ->
            val player = players[index] ?: return@forEachIndexed
            if (result != null) seating.notify(player, result)
        }
    }

    /**
     * 解析桌子位置並建立這次入座使用的席位與協調器。
     *
     * @param tableId 對局桌子 UUID。
     * @param seatedPlayerIds 依座位順序排列的玩家 UUID。
     * @return 這次入座的準備資料；桌子不存在、區塊未載入或不是麻將桌時為 null。
     */
    private fun prepare(tableId: Uuid, seatedPlayerIds: List<Uuid>): PreparedSeating? {
        val location = tableLocationRegistry.get(tableId)?.location ?: return null
        val world = resolveWorld(location) ?: return null
        check(world.server.isOnThread) { "Automatic seating must run on the server thread" }
        val controllerPos = BlockPos(location.x, location.y, location.z)
        if (!world.chunkManager.isChunkLoaded(controllerPos.x shr 4, controllerPos.z shr 4)) return null
        val state = world.getBlockState(controllerPos)
        if (!state.contains(Properties.HORIZONTAL_FACING)) return null
        val tableFacing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()
        val requests = MahjongSeatingTableLayout.seatRequests(
            layout = MahjongTableSeatLayout.forPlayerCount(seatedPlayerIds.size),
            tableId = tableId,
            dimensionId = location.dimensionId,
            controllerX = location.x,
            controllerY = location.y,
            controllerZ = location.z,
            tableFacing = tableFacing,
        )
        val coordinator = SeatAssignmentCoordinator<ServerWorld, Entity, FabricSeatSnapshot>(
            providers = seatProviders.entries(),
            environment = FabricSeatEnvironment(),
            failurePolicy = failurePolicy,
        ) { providerId, rider, cause ->
            logger.warn("Seat provider {} failed for table {}, rider {}", providerId, tableId, rider.uuid, cause)
        }
        return PreparedSeating(world, requests, coordinator)
    }

    /**
     * 一次入座使用的世界、席位與協調器。
     *
     * @property world 桌子所在世界。
     * @property requests 依座位 index 排列的固定席位。
     * @property coordinator 本次入座共用的協調器，跨玩家去重已認領的座位。
     */
    private class PreparedSeating(
        val world: ServerWorld,
        val requests: List<SeatRequest>,
        val coordinator: SeatAssignmentCoordinator<ServerWorld, Entity, FabricSeatSnapshot>,
    ) {
        /**
         * 入座不成功時提示玩家原因；成功入座或安全站立時不提示。
         *
         * @param player 入座的玩家。
         * @param result 入座結果。
         */
        fun notify(player: ServerPlayerEntity, result: SeatAssignmentResult) {
            val key = when (result) {
                SeatAssignmentResult.SEATED, SeatAssignmentResult.STANDING -> null
                SeatAssignmentResult.OCCUPIED -> MinecraftSeatingKeys.OCCUPIED
                SeatAssignmentResult.BLOCKED -> MinecraftSeatingKeys.BLOCKED
                SeatAssignmentResult.RIDING -> MinecraftSeatingKeys.RIDING
                SeatAssignmentResult.FAILED -> MinecraftSeatingKeys.FAILED
            }
            if (key != null) player.sendMessage(Text.translatable(key).formatted(Formatting.YELLOW), false)
        }
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
