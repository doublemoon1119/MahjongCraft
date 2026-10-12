package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 使用 Fabric 1.20.1 玩家傳送實作 [MahjongSeatingPresenter]。
 *
 * 每位玩家傳送到分配到的座位位置並面向桌子；該位置是麻將凳時接著坐上去。已經坐在分配到的凳子上的玩家不移動。
 *
 * @property tableLocationRegistry 查詢桌子目前的位置。
 * @property serverHolder 取得目前伺服器世界與在線玩家。
 * @property seatService 讓玩家坐上麻將凳的服務。
 */
@Single(binds = [MahjongSeatingPresenter::class])
class FabricMahjongSeatingPresenter(
    private val tableLocationRegistry: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val seatService: MahjongStoolSeatService,
) : MahjongSeatingPresenter {
    /**
     * 將入座名單移至對應座位，並在座位是凳子時安排乘坐。
     *
     * @param tableId 對局桌子 UUID。
     * @param seatedPlayerIds 依座位順序排列的玩家 UUID。
     */
    override fun present(tableId: Uuid, seatedPlayerIds: List<Uuid>) {
        val location = tableLocationRegistry.get(tableId)?.location ?: return
        val world = resolveWorld(location) ?: return
        val controllerPos = BlockPos(location.x, location.y, location.z)
        val state = world.getBlockState(controllerPos)
        if (!state.contains(Properties.HORIZONTAL_FACING)) return
        val tableFacing = state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing()
        val placements = MahjongSeatingTableLayout.seatPlacements(location.x, location.y, location.z, tableFacing)

        seatedPlayerIds.forEachIndexed { index, playerId ->
            val placement = placements.getOrNull(index) ?: return@forEachIndexed
            val player = serverHolder.findPlayer(playerId) ?: return@forEachIndexed
            val stoolPos = BlockPos.ofFloored(placement.x, placement.y, placement.z)
            if (isSeatedOn(player, world, stoolPos)) return@forEachIndexed
            player.teleport(world, placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            if (world.getBlockState(stoolPos).block is MahjongStoolBlock) seatService.sit(world, stoolPos, player)
        }
    }

    /**
     * [player] 是否已經坐在 [world] 中位於 [stoolPos] 的麻將凳上。
     *
     * @param player 要檢查的玩家。
     * @param world 凳子所在世界。
     * @param stoolPos 凳子方塊座標。
     * @return 玩家正乘坐該凳子的座位實體時為 true。
     */
    private fun isSeatedOn(player: ServerPlayerEntity, world: ServerWorld, stoolPos: BlockPos): Boolean {
        val seat = player.vehicle as? MahjongStoolSeatEntity ?: return false
        return seat.world === world && seat.blockPos == stoolPos
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
